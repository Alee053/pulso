import { CommonModule } from '@angular/common'
import {
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  Output,
  ViewChild,
  signal,
} from '@angular/core'
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import type { SectorCatalogItem } from '../../discovery/domain/catalog'
import { projectContract } from '../data-access/project.contract'
import { IconComponent } from '../../../shared/icon/icon'
import { ProjectsApi } from '../data-access/projects-api'
import { MemberInvitationPanelComponent } from './member-invitation-panel'
import {
  projectStageOptions,
  type CreatedDraftProject,
  type ProjectCreateFormValue,
  type ProjectStage,
} from '../domain/project'
import { projectStrings } from '../strings/project.strings'

@Component({
  selector: 'pulso-project-create-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MemberInvitationPanelComponent, IconComponent],
  templateUrl: './project-create-form.html',
  styleUrl: './project-create-form.css',
})
export class ProjectCreateFormComponent {
  readonly strings = projectStrings
  readonly projectContract = projectContract

  private readonly projectsApi = inject(ProjectsApi)
  private readonly auth = inject(SupabaseAuthGateway)

  @Input() sectors: readonly SectorCatalogItem[] = []
  @Input() catalogsLoading = false
  @Input() catalogsLoaded = false
  @Input() catalogsErrorMessage: string | null = null

  @Output() teamsRequested = new EventEmitter<void>()
  @Output() catalogsRetryRequested = new EventEmitter<void>()

  @ViewChild('successTitle') set successTitle(element: ElementRef<HTMLElement> | undefined) {
    element?.nativeElement.focus()
  }

  readonly stages = projectStageOptions

  readonly isSubmitting = signal(false)
  readonly submitErrorMessage = signal<string | null>(null)
  readonly createdProject = signal<CreatedDraftProject | null>(null)

  readonly form = new FormGroup({
    slug: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(projectContract.slugMinLength),
        Validators.maxLength(projectContract.slugMaxLength),
      ],
    }),

    name: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(projectContract.nameMaxLength),
      ],
    }),

    tagline: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(projectContract.taglineMaxLength),
      ],
    }),

    description: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.maxLength(projectContract.descriptionMaxLength),
      ],
    }),

    sectorId: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),

    city: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(projectContract.cityMaxLength),
      ],
    }),

    stage: new FormControl<ProjectStage | ''>('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),
  })

  startAnother(): void {
    this.form.reset()
    this.createdProject.set(null)
  }

  async submit(): Promise<void> {
    if (this.isSubmitting() || this.createdProject()) {
      return
    }

    this.submitErrorMessage.set(null)
    this.createdProject.set(null)

    if (!this.auth.session()) {
      this.submitErrorMessage.set(this.strings.authRequired)
      return
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched()
      return
    }

    const value = this.form.getRawValue()

    const sectorExists = this.sectors.some(
      (sector) => sector.id === value.sectorId,
    )

    if (!sectorExists) {
      this.form.controls.sectorId.setErrors({
        invalidSector: true,
      })
      return
    }

    if (!value.stage) {
      return
    }

    const request: ProjectCreateFormValue = {
      slug: value.slug,
      name: value.name,
      tagline: value.tagline,
      description: value.description,
      sectorId: value.sectorId,
      city: value.city,
      stage: value.stage,
    }

    this.isSubmitting.set(true)

    try {
      const created = await firstValueFrom(
        this.projectsApi.createDraft(request),
      )

      this.createdProject.set(created)
    } catch {
      this.submitErrorMessage.set(this.strings.createFailed)
    } finally {
      this.isSubmitting.set(false)
    }
  }

  hasError(
    controlName: keyof typeof this.form.controls,
    error: string,
  ): boolean {
    const control = this.form.controls[controlName]

    return control.touched && control.hasError(error)
  }
}
