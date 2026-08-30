import { TestBed } from '@angular/core/testing'
import { of, throwError } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { StartupsApi } from '../data-access/startups-api'
import { PulsoSession } from './pulso-session'

describe('PulsoSession', () => {
  it('loads the catalog through StartupsApi and keeps local interactions', async () => {
    const list = vi.fn().mockReturnValue(of({
      data: [
        {
          id: 'project-1',
          slug: 'eco-pack',
          name: 'EcoPack Bolivia',
          tagline: 'Envases compostables.',
          sector: 'Sostenibilidad',
          city: 'Cochabamba',
          stage: 'MVP',
        },
        {
          id: 'project-2',
          slug: 'ande-solar',
          name: 'AndeSolar',
          tagline: 'Energía para comunidades.',
          sector: 'Energía limpia',
          city: 'La Paz',
          stage: 'MVP',
        },
      ],
      meta: { offset: 0, limit: 20, hasMore: false },
    }))
    const session = createSession(list)

    session.toggleInterest('collaborate')
    await session.startDiscovery()

    expect(list).toHaveBeenCalledOnce()
    expect(session.catalog().map((startup) => startup.id)).toEqual([
      'project-1',
      'project-2',
    ])
    expect(session.currentStartup()).toBeDefined()
    expect(session.hasLoadedCatalog()).toBe(true)
    expect(session.isLoading()).toBe(false)
    expect(session.errorMessage()).toBeNull()

    session.toggleSaved('project-1')
    expect(session.savedStartups().map((startup) => startup.id)).toEqual(['project-1'])
  })

  it('exposes an error state without replacing an API failure with mock data', async () => {
    const list = vi.fn().mockReturnValue(
      throwError(() => new Error('backend unavailable')),
    )
    const session = createSession(list)

    await session.startDiscovery()

    expect(session.catalog()).toEqual([])
    expect(session.feed()).toEqual([])
    expect(session.hasLoadedCatalog()).toBe(false)
    expect(session.errorMessage()).toContain('backend esté disponible')
    expect(session.isLoading()).toBe(false)
  })
})

function createSession(list: ReturnType<typeof vi.fn>): PulsoSession {
  TestBed.configureTestingModule({
    providers: [
      PulsoSession,
      { provide: StartupsApi, useValue: { list } },
    ],
  })

  return TestBed.inject(PulsoSession)
}
