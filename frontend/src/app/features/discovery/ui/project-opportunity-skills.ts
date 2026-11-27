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
import type { OpportunityContentValue, OpportunityStatus, OpportunityType } from '../domain/project-opportunity-skills'
import type {
  ManagedProjectOpportunity,
  OpportunitySkill,
  OpportunitySkillImportance,
} from '../domain/project-opportunity-skills'
import { IconComponent } from '../../../shared/icon/icon'
import { ProjectOpportunitySkillsApi } from '../data-access/project-opportunity-skills-api'
import { opportunitySkillsStrings as strings } from '../strings/opportunity-skills.strings'
import type { ProjectStatus } from '../domain/project-review'

type OpportunityAction = 'OPEN' | 'PAUSE' | 'CLOSE' | 'ARCHIVE'
const opportunityActionsByStatus: Record<OpportunityStatus, readonly OpportunityAction[]> = {
  DRAFT: ['OPEN', 'ARCHIVE'],
  OPEN: ['PAUSE', 'CLOSE', 'ARCHIVE'],
  PAUSED: ['OPEN', 'CLOSE', 'ARCHIVE'],
  FILLED: ['ARCHIVE'],
  CLOSED: ['ARCHIVE'],
  ARCHIVED: [],
}

@Component({
  selector: 'pulso-project-opportunity-skills',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, IconComponent],
  templateUrl: './project-opportunity-skills.html',
})
export class ProjectOpportunitySkillsComponent {
  @Input({ required: true }) projectId = ''
  @Input() projectName = ''
  @Input() projectStatus: ProjectStatus = 'DRAFT'
  @Input() skills: readonly SkillCatalogItem[] = []
  @Input() catalogsLoaded = false
  @Input() catalogsLoading = false
  @Input() catalogsErrorMessage: string | null = null
  @Output() catalogsRetryRequested = new EventEmitter<void>()

  readonly strings = strings
  readonly importanceOptions: ReadonlyArray<{ value: OpportunitySkillImportance; label: string }> = [
    { value: 'REQUIRED', get label() { return strings.required } },
    { value: 'PREFERRED', get label() { return strings.preferred } },
  ]

  readonly opportunityTypes: OpportunityType[] = [
    'TALENT', 'COLLABORATION', 'CUSTOMER', 'BETA_TESTER', 'INVESTMENT',
    'MENTOR', 'SUPPLIER', 'PARTNERSHIP', 'OTHER',
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
  readonly loadFailed = signal(false)
  readonly errorMessage = signal<string | null>(null)
  readonly successMessage = signal<string | null>(null)
  readonly formOpportunityId = signal<string | null>(null)
  readonly saving = signal(false)
  readonly pendingSkillKey = signal<string | null>(null)
  readonly createOpen = signal(false)
  readonly editingOpportunityId = signal<string | null>(null)
  readonly opportunitySaving = signal(false)
  readonly pendingOpportunityAction = signal<string | null>(null)

  readonly opportunityForm = new FormGroup({
    opportunityType: new FormControl<OpportunityType>('TALENT', { nonNullable: true, validators: [Validators.required] }),
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(5000)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    department: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    remoteAllowed: new FormControl(false, { nonNullable: true }),
  })

  private readonly api = inject(ProjectOpportunitySkillsApi)
  private readonly availableSkillsCache = new Map<
    string,
    { catalog: readonly SkillCatalogItem[]; associatedIds: string; result: SkillCatalogItem[] }
  >()
  private nextOffset = 0

  toggle(): void {
    const next = !this.expanded()
    this.expanded.set(next)
    if (next && !this.loaded()) this.loadMore()
  }

  loadMore(): void {
    if (this.loading()) return
    this.loading.set(true)
    this.loadFailed.set(false)
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
            this.loadFailed.set(true)
            this.loading.set(false)
          },
        })
      },
      error: () => {
        this.loadFailed.set(true)
        this.loading.set(false)
      },
    })
  }

  trackOpportunity(_index: number, opportunity: ManagedProjectOpportunity): string {
    return opportunity.id
  }

  displayOpportunityType(type: OpportunityType): string { return strings.typeOptions[type] }

  displayOpportunityStatus(status: OpportunityStatus): string { return strings.status[status] }

  createInitialStatus(): 'DRAFT' | 'OPEN' { return this.projectStatus === 'PUBLISHED' ? 'OPEN' : 'DRAFT' }

  beginCreate(): void {
    this.createOpen.set(true)
    this.editingOpportunityId.set(null)
    this.resetOpportunityForm()
    this.errorMessage.set(null)
    this.successMessage.set(null)
  }

  beginEdit(opportunity: ManagedProjectOpportunity): void {
    if (opportunity.status === 'ARCHIVED' || this.opportunitySaving()) return
    this.createOpen.set(false)
    this.editingOpportunityId.set(opportunity.id)
    this.opportunityForm.reset({
      opportunityType: opportunity.opportunityType,
      title: opportunity.title,
      description: opportunity.description,
      city: opportunity.city ?? '',
      department: opportunity.department ?? '',
      remoteAllowed: opportunity.remoteAllowed,
    })
    this.errorMessage.set(null)
    this.successMessage.set(null)
  }

  cancelOpportunityForm(): void {
    this.createOpen.set(false)
    this.editingOpportunityId.set(null)
    this.resetOpportunityForm()
  }

  opportunityActions(opportunity: ManagedProjectOpportunity): readonly OpportunityAction[] {
    return opportunityActionsByStatus[opportunity.status].filter((action) =>
      action !== 'OPEN' || this.projectStatus === 'PUBLISHED')
  }

  opportunityFieldError(field: 'opportunityType' | 'title' | 'description', rule: string): boolean {
    const control = this.opportunityForm.controls[field]
    return control.touched && control.hasError(rule)
  }

  async saveOpportunity(): Promise<void> {
    if (this.opportunitySaving() || this.opportunityForm.invalid) {
      this.opportunityForm.markAllAsTouched()
      return
    }
    const value = this.opportunityForm.getRawValue()
    if (!value.remoteAllowed && !value.city.trim() && !value.department.trim()) {
      this.errorMessage.set(strings.remoteLocationRequired)
      return
    }
    const content: OpportunityContentValue = value
    this.opportunitySaving.set(true)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      const editingId = this.editingOpportunityId()
      const updated = editingId
        ? await firstValueFrom(this.api.updateOpportunity(this.projectId, editingId, content))
        : await firstValueFrom(this.api.createOpportunity(this.projectId, content, this.createInitialStatus()))
      if (editingId) {
        this.opportunities.update((items) => items.map((item) => item.id === updated.id ? updated : item))
        this.successMessage.set(strings.editSuccess)
      } else {
        this.opportunities.update((items) => [updated, ...items])
        this.skillsByOpportunity.update((current) => ({ ...current, [updated.id]: [] }))
        this.successMessage.set(strings.createSuccess)
      }
      this.cancelOpportunityForm()
    } catch (error) {
      this.errorMessage.set(this.errorCopy(error, strings.saveFailed, true))
    } finally {
      this.opportunitySaving.set(false)
    }
  }

  async changeOpportunityStatus(opportunity: ManagedProjectOpportunity, action: OpportunityAction): Promise<void> {
    if (this.pendingOpportunityAction() || !this.opportunityActions(opportunity).includes(action)) return
    this.pendingOpportunityAction.set(opportunity.id)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      const updated = await firstValueFrom(this.api.changeOpportunityStatus(this.projectId, opportunity.id, action))
      this.opportunities.update((items) => items.map((item) => item.id === updated.id ? updated : item))
      this.successMessage.set(strings.opportunityStatus(this.displayOpportunityStatus(updated.status)))
    } catch (error) {
      this.errorMessage.set(this.errorCopy(error, strings.transitionFailed, true))
    } finally {
      this.pendingOpportunityAction.set(null)
    }
  }

  availableSkills(opportunityId: string): SkillCatalogItem[] {
    const associatedIds = this.associationsFor(opportunityId)
      .map((skill) => skill.skillId).sort().join(':')
    const cached = this.availableSkillsCache.get(opportunityId)
    if (cached?.catalog === this.skills && cached.associatedIds === associatedIds) return cached.result

    const associated = new Set(associatedIds ? associatedIds.split(':') : [])
    const result = this.skills.filter((skill) => !associated.has(skill.id))
    this.availableSkillsCache.set(opportunityId, {
      catalog: this.skills, associatedIds, result,
    })
    return result
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
      this.errorMessage.set(this.errorCopy(error, strings.saveFailedSkill))
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

  private errorCopy(error: unknown, fallback: string, opportunity = false): string {
    if (!(error instanceof HttpErrorResponse)) return fallback
    if (error.status === 403) return opportunity ? strings.permission : strings.skillPermission
    if (error.status === 404) return opportunity ? strings.unavailable : strings.skillUnavailable
    if (error.status === 409) return opportunity ? strings.conflict : strings.skillConflict
    return fallback
  }

  private resetOpportunityForm(): void {
    this.opportunityForm.reset({
      opportunityType: 'TALENT', title: '', description: '', city: '', department: '', remoteAllowed: false,
    })
  }
}
