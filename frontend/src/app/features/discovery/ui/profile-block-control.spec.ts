import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProfileBlockControlComponent } from './profile-block-control'

const target = 'profile-other'
const path = `http://localhost:8080/api/v1/me/blocks/${target}`

function setup(actor = 'profile-current') {
  const auth = { session: signal({ id: actor, email: null }) }
  TestBed.configureTestingModule({
    imports: [ProfileBlockControlComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SupabaseAuthGateway, useValue: auth }],
  })
  const fixture = TestBed.createComponent(ProfileBlockControlComponent)
  fixture.componentRef.setInput('profileId', target)
  fixture.componentRef.setInput('displayName', 'Ada')
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

describe('ProfileBlockControlComponent', () => {
  it('loads, blocks, and unblocks a profile with idempotent API responses', async () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({ targetProfileId: target, blocked: false, createdAt: null })
    await fixture.whenStable()
    fixture.detectChanges()
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement
    expect(button.textContent).toContain('Bloquear a Ada')

    button.click()
    const blocking = http.expectOne(path)
    expect(blocking.request.method).toBe('PUT')
    expect(blocking.request.body).toEqual({})
    blocking.flush({ targetProfileId: target, blocked: true, createdAt: '2026-11-21T10:00:00Z' })
    await fixture.whenStable()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Perfil bloqueado. No se podrán crear nuevas solicitudes ni enviar mensajes.')
    expect(fixture.nativeElement.querySelector('button').textContent).toContain('Desbloquear a Ada')

    fixture.nativeElement.querySelector('button').click()
    const unblocking = http.expectOne(path)
    expect(unblocking.request.method).toBe('DELETE')
    unblocking.flush({ targetProfileId: target, blocked: false, createdAt: null })
    await fixture.whenStable()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Perfil desbloqueado. Se puede volver a solicitar contacto.')
    http.verify()
  })

  it('hides block controls on the current user profile', () => {
    const { fixture, http } = setup(target)
    expect(fixture.nativeElement.querySelector('button')).toBeNull()
    expect(fixture.nativeElement.textContent).toBe('')
    http.verify()
  })

  it('offers a retry when block status cannot be loaded', async () => {
    const { fixture, http } = setup()
    http.expectOne(path).flush({}, { status: 503, statusText: 'Unavailable' })
    await fixture.whenStable()
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('No pudimos comprobar el estado de contacto.')
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Reintentar')
    http.verify()
  })
})
