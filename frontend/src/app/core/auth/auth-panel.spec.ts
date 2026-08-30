import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { AuthPanelComponent } from './auth-panel'
import { SupabaseAuthGateway } from './supabase-auth.gateway'
import type { AuthenticatedUser } from './supabase-auth.gateway'

describe('AuthPanelComponent', () => {
  it('explains when local Auth is not configured', () => {
    const auth = createGatewayStub(false)
    const fixture = createFixture(auth)

    expect(fixture.nativeElement.textContent).toContain('Auth está inactivo')
    expect(auth.initialize).toHaveBeenCalledOnce()
  })

  it('submits credentials through the gateway and clears the password', async () => {
    const auth = createGatewayStub(true)
    auth.signIn.mockResolvedValue({
      user: { id: 'user-1', email: 'person@example.test' },
      requiresEmailConfirmation: false,
    })
    const fixture = createFixture(auth)
    const component = fixture.componentInstance
    component.email = ' person@example.test '
    component.password = 'local-password'

    await component.submit()

    expect(auth.signIn).toHaveBeenCalledWith('person@example.test', 'local-password')
    expect(component.password).toBe('')
    expect(component.message()).toContain('Sesión iniciada')
  })
})

function createGatewayStub(isConfigured: boolean) {
  const session = signal<AuthenticatedUser | null>(null)
  const isLoading = signal(false)

  return {
    isConfigured,
    session,
    isLoading,
    initialize: vi.fn().mockResolvedValue(undefined),
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
  }
}

function createFixture(auth: ReturnType<typeof createGatewayStub>) {
  TestBed.configureTestingModule({
    imports: [AuthPanelComponent],
    providers: [{ provide: SupabaseAuthGateway, useValue: auth }],
  })

  const fixture = TestBed.createComponent(AuthPanelComponent)
  fixture.detectChanges()
  return fixture
}
