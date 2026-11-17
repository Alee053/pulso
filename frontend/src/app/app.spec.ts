import { signal } from '@angular/core'
import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { mockStartups } from './features/discovery/data-access/mock-startups'
import { MOCK_PUBLIC_PROJECTS } from './features/discovery/data-access/mock-public-projects'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import type { AuthenticatedUser } from './core/auth/supabase-auth.gateway'
import { AppComponent } from './app'
import { currentLanguage } from './core/i18n/language'
import { FeatureFlagsService } from './core/feature-flags/feature-flags.service'
import { PulsoSession } from './features/discovery/application/pulso-session'
import { emptyReferenceCatalogs } from './features/discovery/domain/catalog'
import { intentionOptions, projectStageOptions, type OnboardingIntention, type ProjectStage } from './features/discovery/domain/preferences'

afterEach(() => {
  history.replaceState(null, '', '/')
  localStorage.clear()
  currentLanguage.set('es')
})

describe('AppComponent', () => {
  it.each(['invitation', 'invite'])('accepts the %s link before onboarding and removes its secret from the location', (parameter) => {
    history.replaceState(null, '', '/#' + parameter + '=private-token')
    const session = createSessionStub()
    const auth = createAuthStub()
    auth.session.set({ id: 'invitee-1', email: 'ada@example.test' })
    TestBed.configureTestingModule({ imports: [AppComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
      { provide: PulsoSession, useValue: session }, { provide: SupabaseAuthGateway, useValue: auth },
    ] })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    expect(location.hash).toBe('')
    expect(fixture.nativeElement.querySelector('pulso-onboarding')).toBeNull()
    expect(fixture.nativeElement.innerHTML).not.toContain('private-token')
    fixture.nativeElement.querySelector('[data-action=accept]').click()
    const http = TestBed.inject(HttpTestingController)
    http.expectOne('http://localhost:8080/api/v1/project-members/accept')
      .flush({ projectId: 'project-1', role: 'MEMBER', membershipStatus: 'ACTIVE' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Ya formas parte del equipo')
    http.verify()
  })

  it('keeps the document title and language in step with the chosen language', () => {
    const auth = createAuthStub()
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
        { provide: PulsoSession, useValue: createSessionStub() },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    expect(document.title).toBe('Pulso · Descubre lo que está creciendo')

    currentLanguage.set('en')
    fixture.detectChanges()

    expect(document.title).toBe('Pulso · Discover what is growing')
    fixture.destroy()
  })

  it('shows the standalone Auth page before the authenticated application', () => {
    const session = createSessionStub()
    const auth = createAuthStub()

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
        { provide: PulsoSession, useValue: session },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })

    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('pulso-auth-page')).not.toBeNull()
    expect(session.loadReferenceCatalogs).not.toHaveBeenCalled()
    expect(auth.initialize).toHaveBeenCalled()
  })

  it('keeps the signed-out language selector hidden while flags load and when disabled', async () => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: PulsoSession, useValue: createSessionStub() },
        { provide: SupabaseAuthGateway, useValue: createAuthStub() },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)

    expect(fixture.nativeElement.querySelector('pulso-auth-page')).not.toBeNull()
    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).toBeNull()

    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({ flags: [
      { key: 'language_switcher', enabled: false },
      { key: 'project_creation', enabled: true },
    ] })
    await fixture.whenStable()
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).toBeNull()
    http.verify()
    fixture.destroy()
  })

  it('shows the signed-out language selector only after flags load enabled', async () => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: PulsoSession, useValue: createSessionStub() },
        { provide: SupabaseAuthGateway, useValue: createAuthStub() },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)

    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).toBeNull()
    http.expectOne('http://localhost:8080/api/v1/feature-flags').flush({ flags: [
      { key: 'language_switcher', enabled: true },
      { key: 'project_creation', enabled: true },
    ] })
    await fixture.whenStable()
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).not.toBeNull()
    http.verify()
    fixture.destroy()
  })

  it('keeps the signed-out language selector hidden when flag loading fails', async () => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: PulsoSession, useValue: createSessionStub() },
        { provide: SupabaseAuthGateway, useValue: createAuthStub() },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)

    http.expectOne('http://localhost:8080/api/v1/feature-flags')
      .flush({ detail: 'unavailable' }, { status: 503, statusText: 'Unavailable' })
    await fixture.whenStable()
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('pulso-auth-page')).not.toBeNull()
    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).toBeNull()
    http.verify()
    fixture.destroy()
  })

  it.each([
    ['discover', 'close'], ['discover', 'backdrop'], ['discover', 'escape'],
    ['saved', 'close'], ['saved', 'backdrop'], ['saved', 'escape'],
  ])('opens server detail from %s and dismisses via %s without losing the feed or persisted decisions', (view, dismissal) => {
    const session = createSessionStub()
    const auth = createAuthStub()
    auth.session.set({ id: 'user-1', email: 'person@example.test' })
    session.hasStarted.set(true)
    session.activeView.set(view)
    session.savedIds.set([mockStartups[0].id])
    session.interestedIds.set([mockStartups[1].id])
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
        { provide: PulsoSession, useValue: session },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    const feed = session.feed()
    const trigger = view === 'saved'
      ? fixture.nativeElement.querySelector('pulso-saved-view button')
      : fixture.nativeElement.querySelector('pulso-startup-card .discovery-card__profile-link')
    expect(trigger).not.toBeNull()
    trigger.focus()
    trigger.click()
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)
    http.expectOne(`http://localhost:8080/api/v1/projects/${mockStartups[0].id}`).flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('pulso-public-project-detail')).not.toBeNull()
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull()
    if (dismissal === 'escape') {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    } else {
      const selector = dismissal === 'backdrop' ? '.modal-backdrop' : 'button[aria-label="Cerrar detalle del proyecto"]'
      fixture.nativeElement.querySelector(`pulso-public-project-detail ${selector}`).click()
    }
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('pulso-public-project-detail')).toBeNull()
    expect(document.activeElement).toBe(trigger)
    expect(session.activeView()).toBe(view)
    expect(session.feed()).toBe(feed)
    expect(session.savedIds()).toEqual([mockStartups[0].id])
    expect(session.interestedIds()).toEqual([mockStartups[1].id])
    http.verify()
  })

  it('loads reference catalogs when authentication becomes available', () => {
    const session = createSessionStub()
    const auth = createAuthStub()

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: createFeatureFlagsStub() },
        { provide: PulsoSession, useValue: session },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })

    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()

    auth.session.set({ id: 'user-1', email: 'person@example.test' })
    fixture.detectChanges()

    expect(session.loadReferenceCatalogs).toHaveBeenCalledOnce()
    expect(session.loadOnboardingPreferences).toHaveBeenCalledOnce()
    expect(session.loadProjectCollections).toHaveBeenCalledOnce()
  })

  it('refreshes shared feature flags when the browser window regains focus', () => {
    const flags = createFeatureFlagsStub()
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: flags },
        { provide: PulsoSession, useValue: createSessionStub() },
        { provide: SupabaseAuthGateway, useValue: createAuthStub() },
      ],
    })
    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    expect(flags.load).toHaveBeenCalledOnce()

    window.dispatchEvent(new Event('focus'))

    expect(flags.load).toHaveBeenCalledTimes(2)
    fixture.destroy()
  })

  it('keeps language and project creation controls hidden until flags load', () => {
    const session = createSessionStub()
    const auth = createAuthStub()
    const flags = createFeatureFlagsStub({ language_switcher: false, project_creation: false })
    flags.load.mockReturnValue(new Promise<void>(() => undefined))
    session.goToView = vi.fn((view: string) => session.activeView.set(view))
    auth.session.set({ id: 'user-1', email: 'person@example.test' })
    session.hasStarted.set(true)
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: flags },
        { provide: PulsoSession, useValue: session },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })

    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).toBeNull()
    expect(fixture.nativeElement.querySelector('pulso-navigation-bar')?.textContent).not.toContain('Crear proyecto')
    expect(flags.load).toHaveBeenCalledOnce()

    session.activeView.set('create-project')
    fixture.detectChanges()
    expect(session.activeView()).toBe('discover')
    expect(fixture.nativeElement.querySelector('pulso-project-create-form')).toBeNull()
    fixture.destroy()
  })

  it('returns to discovery if project creation is disabled while its view is active', () => {
    const session = createSessionStub()
    const auth = createAuthStub()
    const flags = createFeatureFlagsStub({ language_switcher: true, project_creation: true })
    auth.session.set({ id: 'user-1', email: 'person@example.test' })
    session.hasStarted.set(true)
    session.activeView.set('create-project')
    session.goToView = vi.fn((view: string) => session.activeView.set(view))
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: FeatureFlagsService, useValue: flags },
        { provide: PulsoSession, useValue: session },
        { provide: SupabaseAuthGateway, useValue: auth },
      ],
    })

    const fixture = TestBed.createComponent(AppComponent)
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('pulso-language-switcher')).not.toBeNull()
    expect(fixture.nativeElement.querySelector('pulso-project-create-form')).not.toBeNull()

    flags.values.set({ language_switcher: true, project_creation: false })
    fixture.detectChanges()

    expect(session.activeView()).toBe('discover')
    expect(session.goToView).toHaveBeenCalledWith('discover')
    expect(fixture.nativeElement.querySelector('pulso-project-create-form')).toBeNull()
    fixture.destroy()
  })
})

function createFeatureFlagsStub(initial = { language_switcher: true, project_creation: true }) {
  const values = signal(initial)
  return {
    values,
    isLoaded: signal(false),
    isLoading: signal(false),
    hasError: signal(false),
    isEnabled: (key: 'language_switcher' | 'project_creation') => values()[key],
    load: vi.fn().mockResolvedValue(undefined),
    update: vi.fn(),
  }
}

function createSessionStub() {
  return {
    intentionOptions: signal(intentionOptions),
    referenceCatalogs: signal(emptyReferenceCatalogs()),
    areCatalogsLoading: signal(false),
    hasLoadedReferenceCatalogs: signal(false),
    catalogsErrorMessage: signal<string | null>(null),
    selectedInterests: signal([]),
    selectedIntentions: signal<OnboardingIntention[]>([]),
    selectedTags: signal([]),
    selectedSkills: signal([]),
    isEditingPreferences: signal(false),
    preferenceValidationMessage: signal<string | null>(null),
    selectedSectorIds: signal<string[]>([]),
    selectedTagIds: signal<string[]>([]),
    selectedSkillIds: signal<string[]>([]),
    stageOptions: signal(projectStageOptions),
    selectedStageIds: signal<ProjectStage[]>([]),
    arePreferencesLoading: signal(false),
    hasLoadedPreferences: signal(false),
    preferencesErrorMessage: signal<string | null>(null),
    arePreferencesSaving: signal(false),
    preferencesSaveErrorMessage: signal<string | null>(null),
    preferencesSavedMessage: signal<string | null>(null),
    hasStarted: signal(false),
    activeView: signal('discover'),
    feed: signal(mockStartups.slice(0, 2)),
    catalog: signal(mockStartups.slice(0, 2)),
    currentStartup: signal(mockStartups[0]),
    activeSignals: signal<string[]>([]),
    errorMessage: signal<string | null>(null),
    isLoading: signal(false),
    savedStartups: signal([mockStartups[0]]),
    interestedStartups: signal([mockStartups[1]]),
    savedCount: signal(1),
    interestedCount: signal(1),
    savedHasMore: signal(false),
    interestedHasMore: signal(false),
    hasLoadedSavedProjects: signal(true),
    hasLoadedInterestedProjects: signal(true),
    isLoadingSavedProjects: signal(false),
    isLoadingInterestedProjects: signal(false),
    savedProjectsErrorMessage: signal<string | null>(null),
    interestedProjectsErrorMessage: signal<string | null>(null),
    isSavingInteraction: signal(false),
    interactionErrorMessage: signal<string | null>(null),
    feedRemaining: signal(2),
    totalStartups: signal(2),
    getReason: () => 'Proyecto recomendado',
    savedIds: signal<string[]>([]),
    isSearching: signal(false),
    searchError: signal<string | null>(null),
    searchHasMore: signal(false),
    searchQuery: signal(''),
    searchResults: signal([]),
    searchTotal: signal(0),
    searchFilters: signal({}),
    updateFilters: vi.fn(),
    search: vi.fn(),
    loadMoreSearch: vi.fn(),
    retrySearch: vi.fn(),
    clearSearch: vi.fn(),
    interestedIds: signal<string[]>([]),
    loadReferenceCatalogs: vi.fn().mockResolvedValue(undefined),
    loadOnboardingPreferences: vi.fn().mockResolvedValue(undefined),
    loadProjectCollections: vi.fn().mockResolvedValue(undefined),
    resetForUserChange: vi.fn(),
    goToView: vi.fn(),
    openProjectDetail: vi.fn(),
    closeProjectDetail: vi.fn(),
    toggleSaved: vi.fn(),
    makeDecision: vi.fn(),
    retryInteraction: vi.fn(),
    retryDiscovery: vi.fn(),
    restartDiscovery: vi.fn(),
    retrySavedProjects: vi.fn(),
    retryInterestedProjects: vi.fn(),
    loadMoreSavedProjects: vi.fn(),
    loadMoreInterestedProjects: vi.fn(),
    toggleIntention: vi.fn(),
    changeSkillProficiency: vi.fn(),
    editPreferences: vi.fn(),
    cancelPreferencesEditing: vi.fn(),
    toggleSector: vi.fn(),
    toggleTag: vi.fn(),
    toggleSkill: vi.fn(),
    toggleStage: vi.fn(),
    retryReferenceCatalogs: vi.fn(),
    retryOnboardingPreferences: vi.fn(),
    retryPreferencesSave: vi.fn(),
    startDiscovery: vi.fn(),
  }
}

function createAuthStub() {
  return {
    isConfigured: true,
    session: signal<AuthenticatedUser | null>(null),
    isLoading: signal(false),
    initialize: vi.fn().mockResolvedValue(undefined),
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
  }
}
