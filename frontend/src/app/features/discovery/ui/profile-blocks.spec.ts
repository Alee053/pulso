import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { ProfileBlocksComponent } from './profile-blocks'

describe('ProfileBlocksComponent', () => {
  it('loads persisted outgoing blocks and removes a row only after a successful unblock', () => {
    TestBed.configureTestingModule({
      imports: [ProfileBlocksComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })
    const fixture = TestBed.createComponent(ProfileBlocksComponent)
    fixture.detectChanges()
    const http = TestBed.inject(HttpTestingController)
    const root = fixture.nativeElement as HTMLElement
    const list = 'http://localhost:8080/api/v1/me/profile-blocks?offset=0'
    const unblock = 'http://localhost:8080/api/v1/profiles/persisted/block'
    const section = root.querySelector('details')!
    http.expectNone(list)

    section.open = true
    section.dispatchEvent(new Event('toggle'))
    http.expectOne({ method: 'GET', url: list }).flush({
      data: [{ profileId: 'persisted', displayName: 'Perfil de prueba' }],
      meta: { offset: 0, limit: 20, hasMore: false },
    })
    fixture.detectChanges()
    const button = root.querySelector('button')!
    expect(button.getAttribute('aria-label')).toBe('Desbloquear a Perfil de prueba')
    button.click()
    fixture.detectChanges()
    expect(button.disabled).toBe(true)
    http.expectOne({ method: 'DELETE', url: unblock }).flush(null, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('No se pudo quitar el bloqueo')
    expect(button.disabled).toBe(false)

    button.click()
    http.expectOne({ method: 'DELETE', url: unblock }).flush(null, { status: 204, statusText: 'No Content' })
    fixture.detectChanges()
    expect(root.querySelector('li')).toBeNull()
    expect(root.textContent).toContain('No tienes perfiles bloqueados')
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Bloqueo eliminado')
    fixture.destroy()
    http.verify()
  })
})
