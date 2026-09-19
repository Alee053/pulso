import { CommonModule } from '@angular/common'
import {
  Component,
  inject,
  Input,
  signal,
} from '@angular/core'
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,

}
 
 from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import type { SectorCatalogItem } from '../../discovery/domain/catalog'
import { ProjectsApi } from '../data-access/projects-api'
import {
  projectStageOptions,
  type CreatedDraftProject,
  type ProjectCreateFormValue,
  type ProjectStage,
} from '../domain/project'

@Component({
  selector: 'pulso-project-create-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './project-create-form.html',
})
export class ProjectCreateFormComponent {
  private readonly projectsApi = inject(ProjectsApi)
  private readonly auth = inject(SupabaseAuthGateway)

  @Input() sectors: readonly SectorCatalogItem[] = []
  @Input() catalogsLoading = false
  @Input() catalogsLoaded = false
  @Input() catalogsErrorMessage: string | null = null

  readonly stages = projectStageOptions

  readonly isSubmitting = signal(false)
  readonly submitErrorMessage = signal<string | null>(null)
  readonly createdProject = signal<CreatedDraftProject | null>(null)

  readonly form = new FormGroup({
    slug: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(2),
        Validators.maxLength(120),
      ],
    }),

    name: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(160),
      ],
    }),

    tagline: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(240),
      ],
    }),

    description: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.maxLength(5000),
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
        Validators.maxLength(120),
      ],
    }),

    stage: new FormControl<ProjectStage | ''>('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),
  })

  async submit(): Promise<void> {
    if (this.isSubmitting()) {
      return
    }

    this.submitErrorMessage.set(null)
    this.createdProject.set(null)

    if (!this.auth.session()) {
      this.submitErrorMessage.set(
        'Debes iniciar sesión para crear un proyecto.',
      )
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
      this.submitErrorMessage.set(
        'No pudimos crear el proyecto. Revisa los datos e inténtalo nuevamente.',
      )
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