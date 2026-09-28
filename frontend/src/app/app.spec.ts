import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import type { AuthenticatedUser } from './core/auth/supabase-auth.gateway'
import { AppComponent } from './app'
import { PulsoSession } from './features/discovery/application/pulso-session'
import { emptyReferenceCatalogs } from './features/discovery/domain/catalog'
import { interestOptions } from './features/discovery/data-access/interests'
import { projectStageOptions, type ProjectStage } from './features/discovery/domain/preferences'

describe('AppComponent', () => {
  it('shows the standalone Auth page before the authenticated application', () => {
    const session = createSessionStub()
    const auth = createAuthStub()

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
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

  it('loads reference catalogs when authentication becomes available', () => {
    const session = createSessionStub()
    const auth = createAuthStub()

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
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
