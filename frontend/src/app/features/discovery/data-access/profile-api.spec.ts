import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProfileApi } from './profile-api'

describe('ProfileApi', () => {
  it('gets the authenticated profile through the protected contract', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(ProfileApi)
    const controller = TestBed.inject(HttpTestingController)
    const profile = {
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
      },
    }

    const responsePromise = firstValueFrom(api.getCurrent())
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/me',
    )

    expect(request.request.method).toBe('GET')
    request.flush(profile)

    await expect(responsePromise).resolves.toEqual(profile)
    controller.verify()
  })

  it('patches the editable profile fields through the protected contract', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const api = TestBed.inject(ProfileApi)
    const controller = TestBed.inject(HttpTestingController)
    const patch = {
      displayName: 'Nuevo nombre',
      shortBio: 'Una historia breve.',
      city: 'Cochabamba',
      department: 'Cochabamba',
    }
    const responsePromise = firstValueFrom(api.update(patch))
    const request = controller.expectOne(
      'http://localhost:8080/api/v1/me',
    )

    expect(request.request.method).toBe('PATCH')
    expect(request.request.body).toEqual(patch)
    request.flush({ subject: 'user-1', profile: patch })

    await expect(responsePromise).resolves.toEqual({
      subject: 'user-1',
      profile: patch,
    })
    controller.verify()
  })
})
