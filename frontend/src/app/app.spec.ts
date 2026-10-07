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
import { PulsoSession } from './features/discovery/application/pulso-session'
import { emptyReferenceCatalogs } from './features/discovery/domain/catalog'
import { interestOptions } from './features/discovery/data-access/interests'
import { projectStageOptions, type ProjectStage } from './features/discovery/domain/preferences'

afterEach(() => history.replaceState(null, '', '/'))

describe('AppComponent', () => {
  it('accepts an invitation before onboarding and removes its secret from the location', () => {
    history.replaceState(null, '', '/#invitation=private-token')
    const session = createSessionStub()
    const auth = createAuthStub()
    auth.session.set({ id: 'invitee-1', email: 'ada@example.test' })
    TestBed.configureTestingModule({ imports: [AppComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
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

  it('shows the standalone Auth page before the authenticated application', () => {
    const session = createSessionStub()
    const auth = createAuthStub()

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
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

  it.each(['discover', 'saved'])('opens server detail from %s without losing the feed or local decisions', (view) => {
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
    trigger.click()
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)
    http.expectOne(`http://localhost:8080/api/v1/projects/${mockStartups[0].id}`).flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('pulso-public-project-detail [role="dialog"]')).not.toBeNull()
    fixture.nativeElement.querySelector('pulso-public-project-detail button[aria-label]').click()
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('pulso-public-project-detail')).toBeNull()
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
  })
})

function createSessionStub() {
  return {
    interestOptions,
    referenceCatalogs: signal(emptyReferenceCatalogs()),
    areCatalogsLoading: signal(false),
    hasLoadedReferenceCatalogs: signal(false),
    catalogsErrorMessage: signal<string | null>(null),
    selectedInterests: signal([]),
    selectedSectorIds: signal<string[]>([]),
    selectedTagIds: signal<string[]>([]),
    selectedSkillIds: signal<string[]>([]),
    stageOptions: projectStageOptions,
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
    getReason: () => 'Proyecto recomendado',
    savedIds: signal<string[]>([]),
    interestedIds: signal<string[]>([]),
    loadReferenceCatalogs: vi.fn().mockResolvedValue(undefined),
    loadOnboardingPreferences: vi.fn().mockResolvedValue(undefined),
    toggleInterest: vi.fn(),
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
