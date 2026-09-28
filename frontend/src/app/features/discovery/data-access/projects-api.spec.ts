import { provideHttpClient } from '@angular/common/http'
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { firstValueFrom } from 'rxjs'
import { describe, expect, it } from 'vitest'
import { ProjectsApi } from './projects-api'

describe('ProjectsApi', () => {
  it('creates a draft project using the exact PUL-52 contract', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    })

    const api = TestBed.inject(ProjectsApi)
    const controller = TestBed.inject(HttpTestingController)

    const responsePromise = firstValueFrom(
      api.createDraft({
        slug: '  Eco-Pack  ',
        name: '  EcoPack Bolivia  ',
        tagline: '  Envases sostenibles  ',
        description: '  Proyecto de prueba.  ',
        sectorId: '11111111-1111-1111-1111-111111111111',
        city: '  Cochabamba  ',
        stage: 'MVP',
      }),
    )

    const request = controller.expectOne(
      'http://localhost:8080/api/v1/projects',
    )

    expect(request.request.method).toBe('POST')

    expect(request.request.body).toEqual({
      slug: 'eco-pack',
      name: 'EcoPack Bolivia',
      tagline: 'Envases sostenibles',
      description: 'Proyecto de prueba.',
      sectorId: '11111111-1111-1111-1111-111111111111',
      city: 'Cochabamba',
      stage: 'MVP',
    })

    request.flush(
      {
        project: {
          id: 'project-1',
          slug: 'eco-pack',
          name: 'EcoPack Bolivia',
          tagline: 'Envases sostenibles',
          description: 'Proyecto de prueba.',
          sectorId: '11111111-1111-1111-1111-111111111111',
          city: 'Cochabamba',
          stage: 'MVP',
          status: 'DRAFT',
          createdByProfileId: 'profile-1',
        },
        owner: {
          id: 'member-1',
          projectId: 'project-1',
          profileId: 'profile-1',
          role: 'OWNER',
          membershipStatus: 'ACTIVE',
        },
      },
      { status: 201, statusText: 'Created' },
    )

    await expect(responsePromise).resolves.toEqual({
      project: {
        id: 'project-1',
        slug: 'eco-pack',
        name: 'EcoPack Bolivia',
        tagline: 'Envases sostenibles',
        description: 'Proyecto de prueba.',
        sectorId: '11111111-1111-1111-1111-111111111111',
        city: 'Cochabamba',
        stage: 'MVP',
        status: 'DRAFT',
      },
      owner: {
        id: 'member-1',
        projectId: 'project-1',
        profileId: 'profile-1',
        role: 'OWNER',
        membershipStatus: 'ACTIVE',
      },
    })

    controller.verify()
  })

  it('sends an empty optional description as null', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    })

    const api = TestBed.inject(ProjectsApi)
    const controller = TestBed.inject(HttpTestingController)

    const responsePromise = firstValueFrom(
      api.createDraft({
        slug: 'eco-pack',
        name: 'EcoPack',
        tagline: 'Envases sostenibles',
        description: '   ',
        sectorId: 'sector-1',
        city: 'Cochabamba',
        stage: 'IDEA',
      }),
    )

    const request = controller.expectOne(
      'http://localhost:8080/api/v1/projects',
    )

    expect(request.request.body.description).toBeNull()

    request.flush({
      project: {
        id: 'project-1',
        slug: 'eco-pack',
        name: 'EcoPack',
        tagline: 'Envases sostenibles',
        description: null,
        sectorId: 'sector-1',
        city: 'Cochabamba',
        stage: 'IDEA',
        status: 'DRAFT',
        createdByProfileId: 'profile-1',
      },
      owner: {
        id: 'member-1',
        projectId: 'project-1',
        profileId: 'profile-1',
        role: 'OWNER',
        membershipStatus: 'ACTIVE',
      },
    })

    await responsePromise
    controller.verify()
  })
})