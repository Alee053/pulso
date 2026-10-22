import { CommonModule } from '@angular/common'
import { Component, EventEmitter, inject, Input, Output, signal } from '@angular/core'
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms'
import { HttpErrorResponse } from '@angular/common/http'
import { forkJoin, firstValueFrom } from 'rxjs'
import type { SkillCatalogItem } from '../domain/catalog'
import type {
  ManagedProjectOpportunity,
  OpportunitySkill,
  OpportunitySkillImportance,
} from '../domain/project-opportunity-skills'
import { ProjectOpportunitySkillsApi } from '../data-access/project-opportunity-skills-api'
import { opportunitySkillsStrings as strings } from '../strings/opportunity-skills.strings'

@Component({
  selector: 'pulso-project-opportunity-skills',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './project-opportunity-skills.html',
})
export class ProjectOpportunitySkillsComponent {
  @Input({ required: true }) projectId = ''
  @Input() projectName = ''
  @Input() skills: readonly SkillCatalogItem[] = []
  @Input() catalogsLoaded = false
  @Input() catalogsLoading = false
  @Input() catalogsErrorMessage: string | null = null
  @Output() catalogsRetryRequested = new EventEmitter<void>()

  readonly strings = strings
  readonly importanceOptions: Array<{ value: OpportunitySkillImportance; label: string }> = [
    { value: 'REQUIRED', label: strings.required },
    { value: 'PREFERRED', label: strings.preferred },
  ]
  readonly form = new FormGroup({
    skillId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    importance: new FormControl<OpportunitySkillImportance>('REQUIRED', { nonNullable: true }),
  })
  readonly expanded = signal(false)
  readonly loading = signal(false)
  readonly loaded = signal(false)
  readonly hasMore = signal(false)
  readonly opportunities = signal<ManagedProjectOpportunity[]>([])
  readonly skillsByOpportunity = signal<Record<string, OpportunitySkill[]>>({})
  readonly errorMessage = signal<string | null>(null)
  readonly successMessage = signal<string | null>(null)
  readonly formOpportunityId = signal<string | null>(null)
  readonly saving = signal(false)
  readonly pendingSkillKey = signal<string | null>(null)

  private readonly api = inject(ProjectOpportunitySkillsApi)
  private nextOffset = 0

  toggle(): void {
    const next = !this.expanded()
    this.expanded.set(next)
    if (next && !this.loaded()) this.loadMore()
  }

  loadMore(): void {
    if (this.loading()) return
    this.loading.set(true)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    this.api.listOpportunities(this.projectId, this.nextOffset).subscribe({
      next: (page) => {
        if (page.data.length === 0) {
          this.nextOffset = page.meta.offset + page.meta.limit
          this.hasMore.set(page.meta.hasMore)
          this.loaded.set(true)
          this.loading.set(false)
          return
        }
        forkJoin(page.data.map((opportunity) =>
          this.api.listSkills(this.projectId, opportunity.id),
        )).subscribe({
          next: (skillLists) => {
            const additions = Object.fromEntries(page.data.map((opportunity, index) =>
              [opportunity.id, skillLists[index] ?? []],
            ))
            this.opportunities.update((opportunities) => [...opportunities, ...page.data])
            this.skillsByOpportunity.update((current) => ({ ...current, ...additions }))
            this.nextOffset = page.meta.offset + page.meta.limit
            this.hasMore.set(page.meta.hasMore)
            this.loaded.set(true)
            this.loading.set(false)
          },
          error: () => {
            this.errorMessage.set(strings.loadFailed)
            this.loading.set(false)
          },
        })
      },
      error: () => {
        this.errorMessage.set(strings.loadFailed)
        this.loading.set(false)
      },
    })
  }

  trackOpportunity(_index: number, opportunity: ManagedProjectOpportunity): string {
    return opportunity.id
  }

  availableSkills(opportunityId: string): SkillCatalogItem[] {
    const associatedIds = new Set(
      this.associationsFor(opportunityId).map((skill) => skill.skillId),
    )
    return this.skills.filter((skill) => !associatedIds.has(skill.id))
  }

  associationsFor(opportunityId: string): OpportunitySkill[] {
    return this.skillsByOpportunity()[opportunityId] || []
  }

  beginAdding(opportunityId: string): void {
    this.formOpportunityId.set(opportunityId)
    this.form.reset({ skillId: '', importance: 'REQUIRED' })
    this.errorMessage.set(null)
    this.successMessage.set(null)
  }

  cancelAdding(): void {
    this.formOpportunityId.set(null)
    this.form.reset({ skillId: '', importance: 'REQUIRED' })
  }

  async associate(opportunityId: string): Promise<void> {
    if (this.saving() || this.form.invalid || !this.catalogsLoaded) return
    this.saving.set(true)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    const { skillId, importance } = this.form.getRawValue()
    try {
      const association = await firstValueFrom(this.api.associate(
        this.projectId, opportunityId, skillId, importance,
      ))
      this.updateAssociation(opportunityId, association)
      this.formOpportunityId.set(null)
      this.form.reset({ skillId: '', importance: 'REQUIRED' })
      this.successMessage.set(strings.saved)
    } catch (error) {
      this.errorMessage.set(this.errorCopy(error, strings.saveFailed))
    } finally {
      this.saving.set(false)
    }
  }

  async changeImportance(
    opportunityId: string,
    association: OpportunitySkill,
    event: Event,
  ): Promise<void> {
    if (this.pendingSkillKey()) return
    const select = event.currentTarget
    if (!(select instanceof HTMLSelectElement)) return
    const importance = select.value
    if (importance !== 'REQUIRED' && importance !== 'PREFERRED') return
    const key = `${opportunityId}:${association.skillId}`
    this.pendingSkillKey.set(key)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      const updated = await firstValueFrom(this.api.changeImportance(
        this.projectId, opportunityId, association.skillId, importance,
      ))
      this.updateAssociation(opportunityId, updated)
      this.successMessage.set(strings.updated)
    } catch (error) {
      select.value = association.importance
      this.errorMessage.set(this.errorCopy(error, strings.updateFailed))
    } finally {
      this.pendingSkillKey.set(null)
    }
  }

  async remove(opportunityId: string, skillId: string): Promise<void> {
    if (this.pendingSkillKey()) return
    const key = `${opportunityId}:${skillId}`
    this.pendingSkillKey.set(key)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      await firstValueFrom(this.api.remove(this.projectId, opportunityId, skillId))
      this.skillsByOpportunity.update((current) => ({
        ...current,
        [opportunityId]: (current[opportunityId] || [])
          .filter((skill) => skill.skillId !== skillId),
      }))
      this.successMessage.set(strings.removed)
    } catch (error) {
      this.errorMessage.set(this.errorCopy(error, strings.removeFailed))
    } finally {
      this.pendingSkillKey.set(null)
    }
  }

  private updateAssociation(opportunityId: string, association: OpportunitySkill): void {
    this.skillsByOpportunity.update((current) => {
      const existing = current[opportunityId] ?? []
      const alreadyPresent = existing.some((skill) => skill.skillId === association.skillId)
      return {
        ...current,
        [opportunityId]: alreadyPresent
          ? existing.map((skill) => skill.skillId === association.skillId ? association : skill)
          : [...existing, association],
      }
    })
  }

  private errorCopy(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback
    if (error.status === 403) return strings.permission
    if (error.status === 404) return strings.unavailable
    if (error.status === 409) return strings.conflict
    return fallback
  }
}
