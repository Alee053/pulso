import { TestBed } from '@angular/core/testing'
import { of, Subject, throwError } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { CatalogsApi } from '../data-access/catalogs-api'
import { PreferencesApi } from '../data-access/preferences-api'
import { ProjectInteractionsApi } from '../data-access/project-interactions-api'
import { toStartupViewModel } from '../data-access/startup-view-model'
import { onboardingIntentions } from '../domain/preferences'
import { PulsoSession } from './pulso-session'

describe('PulsoSession', () => {
  it('loads discovery from the backend and saves a project through the API', async () => {
    const loadDiscoveryFeed = vi.fn().mockReturnValue(of({
      data: [
        {
          startup: startup('project-1', 'EcoPack Bolivia', 'Sostenibilidad', 'Cochabamba'),
          saved: false,
          feedbackType: null,
        },
        {
          startup: startup('project-2', 'AndeSolar', 'Energía limpia', 'La Paz'),
          saved: false,
          feedbackType: null,
        },
      ],
      meta: { offset: 0, limit: 20, hasMore: false, totalCount: 2 },
    }))
    const saveProject = vi.fn().mockReturnValue(of({ projectId: 'project-1', saved: true }))
    const session = createSession({ loadDiscoveryFeed, saveProject })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('COLLABORATE')
    await session.startDiscovery()

    expect(loadDiscoveryFeed).toHaveBeenCalledOnce()
    expect(session.catalog().map((startup) => startup.id)).toEqual([
      'project-1',
      'project-2',
    ])
    expect(session.currentStartup()).toBeDefined()
    expect(session.hasLoadedCatalog()).toBe(true)
    expect(session.isLoading()).toBe(false)
    expect(session.errorMessage()).toBeNull()

    await session.toggleSaved('project-1')
    expect(saveProject).toHaveBeenCalledWith('project-1')
    expect(session.savedStartups().map((startup) => startup.id)).toEqual(['project-1'])
    expect(session.savedIds()).toEqual(['project-1'])
  })

  it('exposes a discovery error state without replacing an API failure with mock data', async () => {
    const loadDiscoveryFeed = vi.fn().mockReturnValue(
      throwError(() => new Error('backend unavailable')),
    )
    const session = createSession({ loadDiscoveryFeed })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('DISCOVER')
    await session.startDiscovery()

    expect(session.catalog()).toEqual([])
    expect(session.feed()).toEqual([])
    expect(session.hasLoadedCatalog()).toBe(false)
    expect(session.errorMessage()).toContain('Inténtalo de nuevo')
    expect(session.isLoading()).toBe(false)
  })

  it('loads reference catalogs into onboarding state and keeps selections by backend id', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      intentions: [...onboardingIntentions],
      stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
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
    await session.loadOnboardingPreferences()
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

  it('restores saved onboarding selections and learned recommendation weights', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      intentions: [...onboardingIntentions],
      stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
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
    const loadPreferences = vi.fn().mockReturnValue(of({
      profileId: 'user-1',
      intentions: ['DISCOVER', 'INVEST'],
      preferredSectors: ['sostenibilidad'],
      preferredStages: ['mvp', 'EARLY_STAGE'],
      preferredTags: ['tag-1'],
      preferredSkills: ['skill-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      recommendationWeights: { sustainability: 2.5 },
    }))
    const session = createSession({ loadCatalogs, loadPreferences })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    await session.loadReferenceCatalogs()

    expect(session.selectedInterests()).toEqual(['discover', 'invest'])
    expect(session.selectedSectorIds()).toEqual(['sector-1'])
    expect(session.selectedStageIds()).toEqual(['MVP', 'EARLY_STAGE'])
    expect(session.selectedTagIds()).toEqual(['tag-1'])
    expect(session.selectedSkillIds()).toEqual(['skill-1'])
    expect(session.preferenceWeights()).toEqual({ sustainability: 2.5 })
    expect(session.hasLoadedPreferences()).toBe(true)
    expect(session.hasStarted()).toBe(true)
  })

  it('saves the current onboarding selections before entering discovery', async () => {
    const savePreferences = vi.fn().mockImplementation((payload) => of({
      profileId: 'user-1',
      preferredSkills: payload.skills.map((skill: { skillId: string }) => skill.skillId),
      onboardingCompletedAt: '2026-10-09T12:00:00Z',
      ...payload,
    }))
    const session = createSession({ savePreferences })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('DISCOVER')
    session.toggleSector('sector-1')
    session.toggleTag('tag-1')
    session.toggleSkill('skill-1')
    session.changeSkillProficiency({ skillId: 'skill-1', proficiency: 'ADVANCED' })
    session.toggleStage('MVP')

    await session.startDiscovery()

    expect(savePreferences).toHaveBeenCalledWith({
      intentions: ['DISCOVER'],
      preferredSectors: ['sector-1'],
      preferredStages: ['MVP'],
      preferredTags: ['tag-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
      completeOnboarding: true,
    })
    expect(session.hasStarted()).toBe(true)
    expect(session.preferencesSaveErrorMessage()).toBeNull()
  })

  it('keeps onboarding open when saving fails and retries the same flow', async () => {
    const savePreferences = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('503')))
      .mockImplementation((payload) => of({
        profileId: 'user-1',
        preferredSkills: payload.skills.map((skill: { skillId: string }) => skill.skillId),
        onboardingCompletedAt: '2026-10-09T12:00:00Z',
        ...payload,
      }))
    const session = createSession({ savePreferences })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('COLLABORATE')

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

  it('does not infer completion from saved intentions', async () => {
    const session = createSession({ loadPreferences: vi.fn().mockReturnValue(of(savedPreferences({
      onboardingCompletedAt: null,
    }))) })
    await session.loadOnboardingPreferences()
    expect(session.selectedIntentions()).toEqual(['BUY'])
    expect(session.hasStarted()).toBe(false)
  })

  it('requires a skill level and leaves the form open without sending an invalid save', async () => {
    const savePreferences = vi.fn()
    const session = createSession({ savePreferences })
    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('BUY')
    session.toggleSkill('skill-1')
    await session.startDiscovery()
    expect(savePreferences).not.toHaveBeenCalled()
    expect(session.preferenceValidationMessage()).toContain('nivel para cada habilidad')
    expect(session.hasStarted()).toBe(false)
  })

  it('cancels a draft and saves later edits with the same validation and completion date', async () => {
    const savePreferences = vi.fn().mockImplementation((payload) => of(savedPreferences({ ...payload })))
    const session = createSession({
      loadPreferences: vi.fn().mockReturnValue(of(savedPreferences())), savePreferences,
    })
    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.goToView('profile')
    session.editPreferences()
    await Promise.resolve()
    session.toggleIntention('FIND_CUSTOMERS')
    session.changeSkillProficiency({ skillId: 'skill-1', proficiency: 'EXPERT' })
    session.cancelPreferencesEditing()
    expect(session.selectedIntentions()).toEqual(['BUY'])
    expect(session.selectedSkills()).toEqual([{ skillId: 'skill-1', proficiency: 'ADVANCED' }])
    expect(savePreferences).not.toHaveBeenCalled()
    session.editPreferences()
    await Promise.resolve()
    session.toggleIntention('FIND_CUSTOMERS')
    session.changeSkillProficiency({ skillId: 'skill-1', proficiency: 'EXPERT' })
    await session.startDiscovery()
    expect(savePreferences).toHaveBeenCalledWith({ intentions: ['BUY', 'FIND_CUSTOMERS'],
      preferredSectors: [], preferredStages: [], preferredTags: ['tag-1'],
      skills: [{ skillId: 'skill-1', proficiency: 'EXPERT' }], completeOnboarding: true })
    expect(session.isEditingPreferences()).toBe(false)
    expect(session.activeView()).toBe('profile')
    expect(session.onboardingCompletedAt()).toBe('2026-10-09T12:00:00Z')
    expect(session.preferenceWeights()).toEqual({ sustainability: 2.5 })
  })

  it('locks the draft while saving and discards a save result after the account changes', async () => {
    const response = new Subject<ReturnType<typeof savedPreferences>>()
    const session = createSession({ savePreferences: vi.fn().mockReturnValue(response) })
    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('BUY')
    const saving = session.startDiscovery()
    session.toggleIntention('INVEST')
    expect(session.selectedIntentions()).toEqual(['BUY'])
    session.resetForUserChange()
    response.next(savedPreferences())
    response.complete()
    await saving
    expect(session.selectedIntentions()).toEqual([])
    expect(session.selectedSkills()).toEqual([])
    expect(session.onboardingCompletedAt()).toBeNull()
    expect(session.hasStarted()).toBe(false)
    expect(session.isEditingPreferences()).toBe(false)
  })

  it('persists interested and dismissed decisions with recommendation weights', async () => {
    const loadDiscoveryFeed = vi.fn().mockReturnValue(of({
      data: [
        {
          startup: startup('project-1', 'EcoPack Bolivia', 'Sostenibilidad', 'Cochabamba'),
          saved: false,
          feedbackType: null,
        },
      ],
      meta: { offset: 0, limit: 20, hasMore: false, totalCount: 1 },
    }))
    const setFeedback = vi.fn().mockReturnValue(of({ projectId: 'project-1', feedbackType: 'INTERESTED' }))
    const session = createSession({ loadDiscoveryFeed, setFeedback })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('DISCOVER')
    await session.startDiscovery()
    await session.makeDecision('interested')

    expect(setFeedback).toHaveBeenCalledWith('project-1', 'INTERESTED', expect.any(Object))
    expect(session.interestedIds()).toEqual(['project-1'])
    expect(session.interestedStartups().map((item) => item.id)).toEqual(['project-1'])
    expect(session.interestedCount()).toBe(1)
  })

  it('discards a collection response from the previous authenticated account', async () => {
    const response = new Subject<{
      data: ReturnType<typeof startup>[]
      meta: { offset: number; limit: number; hasMore: boolean; totalCount: number }
    }>()
    const loadSavedProjects = vi.fn().mockReturnValue(response)
    const session = createSession({ loadSavedProjects })
    const loading = session.loadSavedProjects()

    session.resetForUserChange()
    response.next({
      data: [startup('old-project', 'Old account project', 'Sostenibilidad', 'La Paz')],
      meta: { offset: 0, limit: 20, hasMore: false, totalCount: 1 },
    })
    response.complete()
    await loading

    expect(session.savedProjects()).toEqual([])
    expect(session.savedCount()).toBe(0)
    expect(session.hasLoadedSavedProjects()).toBe(false)
    expect(session.isLoadingSavedProjects()).toBe(false)
  })

  it('does not save or complete onboarding when loading preferences fails', async () => {
    const loadPreferences = vi.fn().mockReturnValue(
      throwError(() => new Error('401')),
    )
    const savePreferences = vi.fn()
    const session = createSession({ loadPreferences, savePreferences })

    await session.loadOnboardingPreferences()
    await session.loadReferenceCatalogs()
    session.toggleIntention('DISCOVER')
    await session.startDiscovery()

    expect(savePreferences).not.toHaveBeenCalled()
    expect(session.hasStarted()).toBe(false)
    expect(session.preferencesErrorMessage()).toContain('No pudimos cargar')
  })

  it('keeps a valid empty reference catalog visible as a loaded state', async () => {
    const loadCatalogs = vi.fn().mockReturnValue(of({
      intentions: [...onboardingIntentions],
      stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
      sectors: [],
      tags: [],
      skills: [],
    }))
    const session = createSession({ loadCatalogs })

    await session.loadReferenceCatalogs()

    expect(session.referenceCatalogs()).toEqual({
      intentions: [...onboardingIntentions],
      stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
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
        intentions: [...onboardingIntentions],
      stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
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

    expect(session.referenceCatalogs()).toEqual({ intentions: [], stages: [], sectors: [], tags: [], skills: [] })
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
  loadDiscoveryFeed?: ReturnType<typeof vi.fn>
  loadSavedProjects?: ReturnType<typeof vi.fn>
  loadInterestedProjects?: ReturnType<typeof vi.fn>
  saveProject?: ReturnType<typeof vi.fn>
  unsaveProject?: ReturnType<typeof vi.fn>
  setFeedback?: ReturnType<typeof vi.fn>
  loadCatalogs?: ReturnType<typeof vi.fn>
  loadPreferences?: ReturnType<typeof vi.fn>
  savePreferences?: ReturnType<typeof vi.fn>
} = {}): PulsoSession {
  const loadDiscoveryFeed = overrides.loadDiscoveryFeed ?? vi.fn().mockReturnValue(of({
    data: [],
    meta: { offset: 0, limit: 20, hasMore: false, totalCount: 0 },
  }))
  const loadSavedProjects = overrides.loadSavedProjects ?? vi.fn().mockReturnValue(of({
    data: [],
    meta: { offset: 0, limit: 20, hasMore: false, totalCount: 0 },
  }))
  const loadInterestedProjects = overrides.loadInterestedProjects ?? vi.fn().mockReturnValue(of({
    data: [],
    meta: { offset: 0, limit: 20, hasMore: false, totalCount: 0 },
  }))
  const saveProject = overrides.saveProject ?? vi.fn().mockReturnValue(of({}))
  const unsaveProject = overrides.unsaveProject ?? vi.fn().mockReturnValue(of({}))
  const setFeedback = overrides.setFeedback ?? vi.fn().mockReturnValue(of({}))
  const loadCatalogs = overrides.loadCatalogs ?? vi.fn().mockReturnValue(of({
    intentions: [...onboardingIntentions],
    stages: ['IDEA', 'MVP', 'EARLY_STAGE'],
    sectors: [{ id: 'sector-1', slug: 'sostenibilidad', name: 'Sostenibilidad', description: null }],
    tags: [{ id: 'tag-1', slug: 'ia', name: 'IA', type: 'TOPIC' }],
    skills: [{ id: 'skill-1', slug: 'java', name: 'Java', category: null }],
  }))
  const loadPreferences = overrides.loadPreferences ?? vi.fn().mockReturnValue(of({
    profileId: 'user-1',
    intentions: [],
    preferredSectors: [],
    preferredStages: [],
    preferredTags: [],
    preferredSkills: [],
    skills: [],
    onboardingCompletedAt: null,
    recommendationWeights: {},
  }))
  const savePreferences = overrides.savePreferences ?? vi.fn().mockImplementation((payload) => of({
    profileId: 'user-1',
    preferredSkills: payload.skills.map((skill: { skillId: string }) => skill.skillId),
    onboardingCompletedAt: '2026-10-09T12:00:00Z',
    ...payload,
    recommendationWeights: {},
  }))

  TestBed.configureTestingModule({
    providers: [
      PulsoSession,
      { provide: ProjectInteractionsApi, useValue: {
        loadDiscoveryFeed,
        loadSavedProjects,
        loadInterestedProjects,
        saveProject,
        unsaveProject,
        setFeedback,
      } },
      { provide: CatalogsApi, useValue: { load: loadCatalogs } },
      { provide: PreferencesApi, useValue: { load: loadPreferences, save: savePreferences } },
    ],
  })

  return TestBed.inject(PulsoSession)
}

function startup(id: string, name: string, sector: string, city: string) {
  return toStartupViewModel({
    id,
    slug: id,
    name,
    tagline: `${name} description.`,
    sector,
    city,
    stage: 'MVP',
  })
}

function savedPreferences(overrides = {}) {
  return { profileId: 'user-1', intentions: ['BUY'], preferredSectors: [], preferredStages: [],
    preferredTags: ['tag-1'], preferredSkills: ['skill-1'],
    skills: [{ skillId: 'skill-1', proficiency: 'ADVANCED' }],
    onboardingCompletedAt: '2026-10-09T12:00:00Z', recommendationWeights: { sustainability: 2.5 }, ...overrides }
}
