import { CommonModule } from '@angular/common'
import { Component, inject, Input, OnInit, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { IconComponent } from '../../../shared/icon/icon'
import type { InterestId, InterestOption } from '../domain/startup'
import {
  ProfileApi,
  type CurrentUserDto,
  type ProfilePatchRequest,
} from '../data-access/profile-api'

@Component({
  selector: 'pulso-profile-view',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './profile-view.html',
})
export class ProfileViewComponent implements OnInit {
  @Input() interestOptions: readonly InterestOption[] = []
  @Input() selectedInterests: InterestId[] = []

  readonly auth = inject(SupabaseAuthGateway)
  private readonly profileApi = inject(ProfileApi)
  readonly profile = signal<CurrentUserDto | null>(null)
  readonly isLoading = signal(true)
  readonly isSaving = signal(false)
  readonly errorMessage = signal<string | null>(null)
  readonly validationMessage = signal<string | null>(null)
  readonly feedbackMessage = signal<string | null>(null)

  displayName = ''
  shortBio = ''
  city = ''
  department = ''

  ngOnInit(): void {
    void this.loadProfile()
  }

  get selectedOptions(): readonly InterestOption[] {
    return this.interestOptions.filter((option) =>
      this.selectedInterests.includes(option.id),
    )
  }

  get avatarInitial(): string {
    return (this.displayName.trim().charAt(0) || 'U').toUpperCase()
  }

  async loadProfile(): Promise<void> {
    if (this.isSaving()) {
      return
    }

    this.isLoading.set(true)
    this.errorMessage.set(null)
    this.feedbackMessage.set(null)

    try {
      const currentUser = await firstValueFrom(this.profileApi.getCurrent())
      this.profile.set(currentUser)
      this.hydrateForm(currentUser)
    } catch {
      this.profile.set(null)
      this.errorMessage.set(
        'No pudimos cargar tu perfil. Inténtalo de nuevo.',
      )
    } finally {
      this.isLoading.set(false)
    }
  }

  async saveProfile(event?: SubmitEvent): Promise<void> {
    event?.preventDefault()
    if (this.isSaving()) {
      return
    }

    const normalizedDisplayName = this.displayName.trim()
    if (!normalizedDisplayName) {
      this.validationMessage.set('El nombre visible es obligatorio.')
      return
    }

    this.validationMessage.set(null)
    this.feedbackMessage.set(null)
    this.isSaving.set(true)

    try {
      const patch: ProfilePatchRequest = {
        displayName: normalizedDisplayName,
      }
      addOptionalField(patch, 'shortBio', this.shortBio)
      addOptionalField(patch, 'city', this.city)
      addOptionalField(patch, 'department', this.department)

      const currentUser = await firstValueFrom(this.profileApi.update(patch))
      this.profile.set(currentUser)
      this.hydrateForm(currentUser)
      this.feedbackMessage.set('Perfil guardado correctamente.')
    } catch {
      this.feedbackMessage.set(
        'No se pudo guardar el perfil. Revisa los campos e inténtalo de nuevo.',
      )
    } finally {
      this.isSaving.set(false)
    }
  }

  async signOut(): Promise<void> {
    if (this.isSaving()) {
      return
    }

    this.isSaving.set(true)
    this.feedbackMessage.set(null)
    try {
      await this.auth.signOut()
    } catch {
      this.feedbackMessage.set('No se pudo cerrar la sesión. Inténtalo de nuevo.')
    } finally {
      this.isSaving.set(false)
    }
  }

  private hydrateForm(currentUser: CurrentUserDto): void {
    this.displayName = currentUser.profile.displayName ?? ''
    this.shortBio = currentUser.profile.shortBio ?? ''
    this.city = currentUser.profile.city ?? ''
    this.department = currentUser.profile.department ?? ''
    this.validationMessage.set(null)
  }
}

function addOptionalField(
  patch: ProfilePatchRequest,
  field: 'shortBio' | 'city' | 'department',
  value: string,
): void {
  const normalized = value.trim()
  if (normalized) {
    patch[field] = normalized
  }
}
