import { TestBed } from '@angular/core/testing'
import { of, throwError } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { CatalogsApi } from '../data-access/catalogs-api'
import { StartupsApi } from '../data-access/startups-api'
import { PreferencesApi } from '../data-access/preferences-api'
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
    const session = createSession({ list })

    await session.loadOnboardingPreferences()
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

  it('exposes a discovery error state without replacing an API failure with mock data', async () => {
    const list = vi.fn().mockReturnValue(
      throwError(() => new Error('backend unavailable')),
    )
    const session = createSession({ list })

    await session.loadOnboardingPreferences()
    session.toggleInterest('discover')
    await session.startDiscovery()

    expect(session.catalog()).toEqual([])
    expect(session.feed()).toEqual([])
    expect(session.hasLoadedCatalog()).toBe(false)
    expect(session.errorMessage()).toContain('Inténtalo de nuevo')
    expect(session.isLoading()).toBe(false)
  })

  it('loads reference catalogs into onboarding state and keeps selections by backend id', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      sectors: [{
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      }],
      tags: [{
        id: 'tag-1',
        slug: 'ia',
        name: 'Inteligencia artificial',
        type: 'TOPIC',
      }],
      skills: [{
        id: 'skill-1',
        slug: 'java',
        name: 'Java',
        category: 'DESARROLLO',
      }],
    }))
    const session = createSession({ loadCatalogs })

    await session.loadReferenceCatalogs()
    session.toggleSector('sector-1')
    session.toggleTag('tag-1')
    session.toggleSkill('skill-1')

    expect(loadCatalogs).toHaveBeenCalledOnce()
    expect(session.hasLoadedReferenceCatalogs()).toBe(true)
    expect(session.catalogsErrorMessage()).toBeNull()
    expect(session.referenceCatalogs().sectors[0]?.name).toBe('Sostenibilidad')
    expect(session.selectedSectorIds()).toEqual(['sector-1'])
    expect(session.selectedTagIds()).toEqual(['tag-1'])
    expect(session.selectedSkillIds()).toEqual(['skill-1'])
  })

  it('restores saved intentions, sector references, and project stages', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      sectors: [{
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      }],
      tags: [],
      skills: [],
    }))
    const loadPreferences = vi.fn().mockReturnValue(of({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'INVEST'],
      preferredSectors: ['sostenibilidad'],
      preferredStages: ['mvp', 'EARLY_STAGE'],
    }))
    const session = createSession({ loadCatalogs, loadPreferences })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()

    expect(session.selectedInterests()).toEqual(['discover', 'invest'])
    expect(session.selectedSectorIds()).toEqual(['sector-1'])
    expect(session.selectedStageIds()).toEqual(['MVP', 'EARLY_STAGE'])
    expect(session.hasLoadedPreferences()).toBe(true)
  })

  it('saves the current onboarding selections before entering discovery', async () => {
    const savePreferences = vi.fn().mockImplementation((payload) => of({
      profileId: 'user-1',
      ...payload,
    }))
    const session = createSession({ savePreferences })

    await session.loadOnboardingPreferences()
    session.toggleInterest('discover')
    session.toggleSector('sector-1')
    session.toggleStage('MVP')

    await session.startDiscovery()

    expect(savePreferences).toHaveBeenCalledWith({
      intentions: ['DISCOVER'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
    })
    expect(session.hasStarted()).toBe(true)
    expect(session.preferencesSaveErrorMessage()).toBeNull()
  })

  it('keeps onboarding open when saving fails and retries the same flow', async () => {
    const savePreferences = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('503')))
      .mockImplementation((payload) => of({
        profileId: 'user-1',
        ...payload,
      }))
    const session = createSession({ savePreferences })

    await session.loadOnboardingPreferences()
    session.toggleInterest('collaborate')

    await session.startDiscovery()

    expect(session.hasStarted()).toBe(false)
    expect(session.preferencesSaveErrorMessage()).toContain('No pudimos guardar')
    expect(session.arePreferencesSaving()).toBe(false)

    session.retryPreferencesSave()
    await Promise.resolve()
    await Promise.resolve()

    expect(savePreferences).toHaveBeenCalledTimes(2)
    expect(session.hasStarted()).toBe(true)
    expect(session.preferencesSaveErrorMessage()).toBeNull()
  })

  it('does not save or complete onboarding when loading preferences fails', async () => {
    const loadPreferences = vi.fn().mockReturnValue(
      throwError(() => new Error('401')),
    )
    const savePreferences = vi.fn()
    const session = createSession({ loadPreferences, savePreferences })

    await session.loadOnboardingPreferences()
    session.toggleInterest('discover')
    await session.startDiscovery()

    expect(savePreferences).not.toHaveBeenCalled()
    expect(session.hasStarted()).toBe(false)
    expect(session.preferencesErrorMessage()).toContain('No pudimos cargar')
  })

  it('keeps a valid empty reference catalog visible as a loaded state', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      sectors: [],
      tags: [],
      skills: [],
    }))
    const session = createSession({ loadCatalogs })

    await session.loadReferenceCatalogs()

    expect(session.referenceCatalogs()).toEqual({
      sectors: [],
      tags: [],
      skills: [],
    })
    expect(session.hasLoadedReferenceCatalogs()).toBe(true)
    expect(session.catalogsErrorMessage()).toBeNull()
    expect(session.areCatalogsLoading()).toBe(false)
  })

  it('exposes catalog failures without mocks and can load again on retry', async () => {
    const loadCatalogs = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('503')))
      .mockReturnValueOnce(of({
        sectors: [{
          id: 'sector-1',
          slug: 'tecnologia',
          name: 'Tecnología',
          description: null,
        }],
        tags: [],
        skills: [],
      }))
    const session = createSession({ loadCatalogs })

    await session.loadReferenceCatalogs()

    expect(session.referenceCatalogs()).toEqual({
      sectors: [],
      tags: [],
      skills: [],
    })
    expect(session.hasLoadedReferenceCatalogs()).toBe(false)
    expect(session.catalogsErrorMessage()).toContain('No pudimos cargar')

    await session.loadReferenceCatalogs()

    expect(loadCatalogs).toHaveBeenCalledTimes(2)
    expect(session.referenceCatalogs().sectors[0]?.id).toBe('sector-1')
    expect(session.hasLoadedReferenceCatalogs()).toBe(true)
    expect(session.catalogsErrorMessage()).toBeNull()
  })
})

function createSession(overrides: {
  list?: ReturnType<typeof vi.fn>
  loadCatalogs?: ReturnType<typeof vi.fn>
  loadPreferences?: ReturnType<typeof vi.fn>
  savePreferences?: ReturnType<typeof vi.fn>
} = {}): PulsoSession {
  const list = overrides.list ?? vi.fn().mockReturnValue(of({
    data: [],
    meta: { offset: 0, limit: 20, hasMore: false },
  }))
  const loadCatalogs = overrides.loadCatalogs ?? vi.fn().mockReturnValue(of({
    sectors: [],
    tags: [],
    skills: [],
  }))
  const loadPreferences = overrides.loadPreferences ?? vi.fn().mockReturnValue(of({
    profileId: 'user-1',
    intentions: [],
    preferredSectors: [],
    preferredStages: [],
  }))
  const savePreferences = overrides.savePreferences ?? vi.fn().mockImplementation((payload) => of({
    profileId: 'user-1',
    ...payload,
  }))

  TestBed.configureTestingModule({
    providers: [
      PulsoSession,
      { provide: StartupsApi, useValue: { list } },
      { provide: CatalogsApi, useValue: { load: loadCatalogs } },
      { provide: PreferencesApi, useValue: { load: loadPreferences, save: savePreferences } },
    ],
  })

  return TestBed.inject(PulsoSession)
}
