import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import type { AuthenticatedUser } from './core/auth/supabase-auth.gateway'
import { AppComponent } from './app'
import { PulsoSession } from './features/discovery/application/pulso-session'
import { emptyReferenceCatalogs } from './features/discovery/domain/catalog'
import { interestOptions } from './features/discovery/data-access/interests'

describe('AppComponent', () => {
  it('keeps the Auth panel available for the protected reference catalogs flow', () => {
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

    expect(fixture.nativeElement.textContent).toContain(
      'Conecta tu cuenta para probar Auth',
    )
    expect(session.loadReferenceCatalogs).not.toHaveBeenCalled()
    expect(auth.initialize).toHaveBeenCalledOnce()
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
    hasStarted: signal(false),
    activeView: signal('discover'),
    savedIds: signal<string[]>([]),
    interestedIds: signal<string[]>([]),
    loadReferenceCatalogs: vi.fn().mockResolvedValue(undefined),
    toggleInterest: vi.fn(),
    toggleSector: vi.fn(),
    toggleTag: vi.fn(),
    toggleSkill: vi.fn(),
    retryReferenceCatalogs: vi.fn(),
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
