import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { of, throwError } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { FeatureFlagsService } from '../../../core/feature-flags/feature-flags.service'
import type { FeatureFlagValues } from '../../../core/feature-flags/feature-flags-api'
import {
  ProfileApi,
  type CurrentUserDto,
  type ProfileDto,
} from '../data-access/profile-api'
import { ProfileViewComponent } from './profile-view'

describe('ProfileViewComponent', () => {
  it('loads the profile and renders labeled editable fields', async () => {
    const profile = createCurrentUser()
    const fixture = createFixture(createProfileApiStub(profile))

    await fixture.whenStable()
    fixture.detectChanges()

    const component = fixture.componentInstance
    expect(component.displayName).toBe('Persona Pulso')
    expect(component.shortBio).toBe('Me interesan los proyectos locales.')
    expect(component.city).toBe('La Paz')
    expect(component.department).toBe('La Paz')
    expect(
      fixture.nativeElement.querySelector('input[name="displayName"]'),
    ).not.toBeNull()
    expect(
      fixture.nativeElement.querySelector('textarea[name="shortBio"]'),
    ).not.toBeNull()
    expect(fixture.nativeElement.textContent).toContain('person@example.test')
  })

  it('maps trimmed form values and exposes the save confirmation', async () => {
    const profile = createCurrentUser()
    const savedProfile = createCurrentUser({
      displayName: 'Nombre actualizado',
      shortBio: 'Una historia breve.',
      city: 'Cochabamba',
      department: 'Cochabamba',
    })
    const update = vi.fn().mockReturnValue(of(savedProfile))
    const fixture = createFixture(createProfileApiStub(profile, update))

    await fixture.whenStable()
    const component = fixture.componentInstance
    component.displayName = '  Nombre actualizado  '
    component.shortBio = '  Una historia breve. '
    component.city = ' Cochabamba '
    component.department = ' Cochabamba '

    await component.saveProfile()

    expect(update).toHaveBeenCalledWith({
      displayName: 'Nombre actualizado',
      shortBio: 'Una historia breve.',
      city: 'Cochabamba',
      department: 'Cochabamba',
    })
    expect(component.feedbackMessage()).toBe('Perfil guardado correctamente.')
    expect(component.displayName).toBe('Nombre actualizado')
  })

  it('shows validation and save errors without reporting a false success', async () => {
    const profile = createCurrentUser()
    const update = vi.fn().mockReturnValue(
      throwError(() => new Error('save failed')),
    )
    const fixture = createFixture(createProfileApiStub(profile, update))

    await fixture.whenStable()
    const component = fixture.componentInstance
    component.displayName = '   '

    await component.saveProfile()

    expect(update).not.toHaveBeenCalled()
    expect(component.validationMessage()).toBe(
      'El nombre visible es obligatorio.',
    )

    component.displayName = 'Nombre válido'
    await component.saveProfile()

    expect(component.feedbackMessage()).toContain('No se pudo guardar')
    expect(component.feedbackMessage()).not.toContain('correctamente')
  })

  it('shows a retry action when loading the profile fails', async () => {
    const getCurrent = vi.fn().mockReturnValue(
      throwError(() => new Error('load failed')),
    )
    const fixture = createFixture(
      createProfileApiStub(createCurrentUser(), vi.fn(), getCurrent),
    )

    await fixture.whenStable()
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain(
      'No pudimos cargar tu perfil. Inténtalo de nuevo.',
    )
    expect(
      fixture.nativeElement.querySelector('button')?.textContent,
    ).toContain('Reintentar')
  })

  it('shows global feature controls when the loaded profile has the platform ADMIN role', async () => {
    const administrator = createFixture(createProfileApiStub(createCurrentUser({ platformRole: 'ADMIN' })))
    await administrator.whenStable()
    administrator.detectChanges()
    expect(administrator.nativeElement.querySelector('pulso-feature-flag-settings')).not.toBeNull()
  })

  it('hides global feature controls for a regular platform profile', async () => {
    const regularUser = createFixture(createProfileApiStub(createCurrentUser({ platformRole: 'USER' })))
    await regularUser.whenStable()
    regularUser.detectChanges()
    expect(regularUser.nativeElement.querySelector('pulso-feature-flag-settings')).toBeNull()
  })
})

function createFixture(
  profileApi: ReturnType<typeof createProfileApiStub>,
) {
  TestBed.configureTestingModule({
    imports: [ProfileViewComponent],
    providers: [
      { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
      { provide: ProfileApi, useValue: profileApi },
      {
        provide: SupabaseAuthGateway,
        useValue: {
          session: signal({ id: 'user-1', email: 'person@example.test' }),
          signOut: vi.fn().mockResolvedValue(undefined),
        },
      },
    ],
  })

  const fixture = TestBed.createComponent(ProfileViewComponent)
  fixture.detectChanges()
  return fixture
}

function createFeatureFlagsStub() {
  const values = signal<FeatureFlagValues>({ language_switcher: true, project_creation: true })
  return {
    values,
    isLoaded: signal(true),
    isLoading: signal(false),
    hasError: signal(false),
    hasLoadError: signal(false),
    isEnabled: (key: keyof FeatureFlagValues) => values()[key],
    load: vi.fn().mockResolvedValue(undefined),
    update: vi.fn(),
  }
}

function createProfileApiStub(
  profile: CurrentUserDto,
  update = vi.fn().mockReturnValue(of(profile)),
  getCurrent = vi.fn().mockReturnValue(of(profile)),
) {
  return { getCurrent, update }
}

function createCurrentUser(
  profileOverrides: Partial<ProfileDto> = {},
): CurrentUserDto {
  return {
    subject: 'user-1',
    email: 'person@example.test',
    role: 'authenticated',
    audience: ['authenticated'],
    profile: {
      id: 'user-1',
      profileKind: 'PERSON',
      displayName: 'Persona Pulso',
      avatarStoragePath: null,
      shortBio: 'Me interesan los proyectos locales.',
      city: 'La Paz',
      department: 'La Paz',
      profileVisibility: 'PUBLIC',
      verificationStatus: 'UNVERIFIED',
      accountStatus: 'ACTIVE',
      platformRole: 'USER',
      onboardingCompletedAt: null,
      createdAt: '2026-09-01T00:00:00Z',
      updatedAt: '2026-09-01T00:00:00Z',
      ...profileOverrides,
    },
  }
}
