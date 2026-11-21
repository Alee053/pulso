import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core'
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import type { SectorCatalogItem } from '../domain/catalog'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import { projectStageOptions, projectStages, type ProjectStage } from '../domain/project'
import type { ProjectUpdateFormValue } from '../domain/project'
import { projectContract } from '../data-access/project.contract'
import { ProjectsApi } from '../data-access/projects-api'
import { projectManagementStrings as strings } from '../strings/project-management.strings'
import { projectStatuses, type ProjectStatus } from '../domain/project-review'

@Component({
  selector: 'pulso-project-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './project-management.html',
})
export class ProjectManagementComponent {
  @Input({ required: true }) project!: CurrentProjectMembership
  @Input() sectors: readonly SectorCatalogItem[] = []
  @Output() projectChanged = new EventEmitter<CurrentProjectMembership>()

  readonly strings = strings
  readonly stages = projectStageOptions
  readonly isEditing = signal(false)
  readonly saving = signal(false)
  readonly actionBusy = signal(false)
  readonly error = signal<string | null>(null)
  readonly success = signal<string | null>(null)
  private readonly api = inject(ProjectsApi)

  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, requiredTrimmedText, Validators.maxLength(projectContract.nameMaxLength)] }),
    tagline: new FormControl('', { nonNullable: true, validators: [Validators.required, requiredTrimmedText, Validators.maxLength(projectContract.taglineMaxLength)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(projectContract.descriptionMaxLength)] }),
    valueProposition: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(projectContract.descriptionMaxLength)] }),
    stage: new FormControl<ProjectStage>('IDEA', { nonNullable: true, validators: [Validators.required] }),
    sectorId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, requiredTrimmedText, Validators.maxLength(projectContract.cityMaxLength)] }),
    department: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(projectContract.cityMaxLength)] }),
  })

  beginEdit(): void {
    this.error.set(null)
    this.success.set(null)
    this.form.reset({
      name: this.project.name,
      tagline: this.project.tagline,
      description: this.project.description ?? '',
      valueProposition: this.project.valueProposition ?? '',
      stage: this.project.stage,
      sectorId: this.project.sectorId,
      city: this.project.city ?? '',
      department: this.project.department ?? '',
    })
    this.isEditing.set(true)
  }

  cancelEdit(): void {
    this.isEditing.set(false)
    this.error.set(null)
  }

  async save(): Promise<void> {
    if (this.saving()) return
    this.error.set(null)
    this.success.set(null)
    if (this.form.invalid) {
      this.form.markAllAsTouched()
      return
    }
    const value = this.form.getRawValue()
    if (!projectStages.includes(value.stage)
      || (value.sectorId !== this.project.sectorId && !this.sectors.some((sector) => sector.id === value.sectorId))) {
      this.form.controls.sectorId.setErrors({ unavailable: true })
      return
    }
    const patch = changedProjectFields(this.project, value)
    if (Object.keys(patch).length === 0) {
      this.isEditing.set(false)
      return
    }
    this.saving.set(true)
    try {
      const updated = await firstValueFrom(this.api.update(this.project.projectId, patch))
      const next: CurrentProjectMembership = {
        ...this.project,
        name: updated.name,
        tagline: updated.tagline,
        description: updated.description,
        valueProposition: updated.valueProposition,
        sectorId: updated.sectorId,
        sectorName: this.sectors.find((sector) => sector.id === updated.sectorId)?.name ?? this.project.sectorName,
        stage: updated.stage,
        city: updated.city,
        department: updated.department,
        status: updated.status,
        verificationStatus: updated.verificationStatus,
      }
      this.projectChanged.emit(next)
      this.isEditing.set(false)
      this.success.set(strings.saveSuccess)
    } catch (error: unknown) {
      this.error.set(isConflict(error) ? strings.lifecycleConflict : strings.saveFailed)
    } finally {
      this.saving.set(false)
    }
  }

  async requestPublication(): Promise<void> {
    if (this.actionBusy() || this.project.status !== 'DRAFT') return
    await this.runLifecycleAction(() => this.api.requestPublication(this.project.projectId), strings.requestPublicationSuccess)
  }

  async archive(): Promise<void> {
    const allowed: ProjectStatus[] = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED']
    if (this.actionBusy() || !this.project.status || !allowed.includes(this.project.status)) return
    await this.runLifecycleAction(() => this.api.archive(this.project.projectId), strings.archiveSuccess)
  }

  canArchive(): boolean {
    return ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED'].includes(this.project.status ?? '')
  }

  statusLabel(): string {
    const status = this.project.status
    return status && projectStatuses.includes(status) ? strings.status[status] : ''
  }

  hasCurrentSector(): boolean { return this.sectors.some((sector) => sector.id === this.project.sectorId) }

  fieldError(control: keyof typeof this.form.controls, rule: string): boolean {
    const field = this.form.controls[control]
    return field.touched && field.hasError(rule)
  }

  private async runLifecycleAction(
    action: () => ReturnType<ProjectsApi['requestPublication']>,
    successMessage: string,
  ): Promise<void> {
    this.actionBusy.set(true)
    this.error.set(null)
    this.success.set(null)
    try {
      const status = await firstValueFrom(action())
      this.projectChanged.emit({ ...this.project, status })
      this.success.set(successMessage)
    } catch (error: unknown) {
      this.error.set(isConflict(error) ? strings.lifecycleConflict : strings.lifecycleFailed)
    } finally {
      this.actionBusy.set(false)
    }
  }
}

function changedProjectFields(
  current: CurrentProjectMembership,
  value: ReturnType<ProjectManagementComponent['form']['getRawValue']>,
): ProjectUpdateFormValue {
  const patch: ProjectUpdateFormValue = {}
  if (value.name.trim() !== current.name) patch.name = value.name
  if (value.tagline.trim() !== current.tagline) patch.tagline = value.tagline
  if (normalizeOptional(value.description) !== current.description) patch.description = value.description
  if (normalizeOptional(value.valueProposition) !== current.valueProposition) patch.valueProposition = value.valueProposition
  if (value.stage !== current.stage) patch.stage = value.stage
  if (value.sectorId !== current.sectorId) patch.sectorId = value.sectorId
  if (normalizeOptional(value.city) !== current.city) patch.city = value.city
  if (normalizeOptional(value.department) !== current.department) patch.department = value.department
  return patch
}

function normalizeOptional(value: string): string | null {
  const normalized = value.trim()
  return normalized === '' ? null : normalized
}

function isConflict(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'status' in error && (error as { status?: number }).status === 409
}

function requiredTrimmedText(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length > 0 ? null : { required: true }
}
