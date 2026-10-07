import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { MOCK_PUBLIC_PROJECTS } from '../data-access/mock-public-projects'
import { PublicProjectDetailComponent } from './public-project-detail'

function createFixture() {
  TestBed.configureTestingModule({ imports: [PublicProjectDetailComponent], providers: [provideHttpClient(), provideHttpClientTesting()] })
  const fixture = TestBed.createComponent(PublicProjectDetailComponent)
  fixture.componentRef.setInput('projectId', 'eco-pack')
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

describe('PublicProjectDetailComponent', () => {
  it('shows loading then renders the server project and public team', () => {
    const { fixture, http } = createFixture()
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Cargando proyecto')
    const close = vi.fn()
    fixture.componentInstance.closed.subscribe(close)
    const back = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(button => button.textContent?.includes('Volver'))
    expect(back).toBeDefined()
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="dialog"]').textContent).toContain('EcoPack Bolivia')
    expect(fixture.nativeElement.textContent).toContain('Persona Uno')
    fixture.nativeElement.querySelector('button[aria-label]').click()
    expect(close).toHaveBeenCalledOnce()
    http.verify()
  })

  it.each([400, 403, 404])('shows HTTP %s with a return action and no retry', (status) => {
    const { fixture, http } = createFixture()
    const closed = vi.fn()
    fixture.componentInstance.closed.subscribe(closed)
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({}, { status, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull()
    expect(fixture.nativeElement.querySelector('pulso-startup-detail-modal')).toBeNull()
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar')
    fixture.nativeElement.querySelector('button').click()
    expect(closed).toHaveBeenCalledOnce()
    http.verify()
  })

  it.each([0, 500])('lets the visitor retry HTTP %s and recover', (status) => {
    const { fixture, http } = createFixture()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    if (status === 0) request.error(new ProgressEvent('error'))
    else request.flush({}, { status, statusText: 'Unavailable' })
    fixture.detectChanges()
    const retry = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(button => button.textContent?.includes('Reintentar'))!
    retry.click()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Cargando proyecto')
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull()
    http.verify()
  })

  it('contains keyboard focus during loading and errors and restores focus on close', () => {
    const feedButton = document.createElement('button')
    document.body.append(feedButton)
    feedButton.focus()
    const { fixture, http } = createFixture()
    const closed = vi.fn()
    fixture.componentInstance.closed.subscribe(closed)
    const back = fixture.nativeElement.querySelector('button')
    expect(document.activeElement).toBe(back)
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    document.dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(back)
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({}, { status: 404, statusText: 'Not Found' })
    fixture.detectChanges()
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('button'))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(closed).toHaveBeenCalledOnce()
    fixture.destroy()
    expect(document.activeElement).toBe(feedButton)
    expect(document.body.style.overflow).not.toBe('hidden')
    feedButton.remove()
    http.verify()
  })

  it('cancels the previous request when a different project is selected', () => {
    const { fixture, http } = createFixture()
    const previous = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    fixture.componentRef.setInput('projectId', 'different-project')
    fixture.detectChanges()
    expect(previous.cancelled).toBe(true)
    http.expectOne('http://localhost:8080/api/v1/projects/different-project').flush({ data: { ...MOCK_PUBLIC_PROJECTS['eco-pack'], name: 'Otro proyecto' } })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Otro proyecto')
    expect(fixture.nativeElement.textContent).not.toContain('EcoPack Bolivia')
    http.verify()
  })

  it('cancels its request when closed during loading', () => {
    const { fixture, http } = createFixture()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    fixture.destroy()
    expect(request.cancelled).toBe(true)
    http.verify()
  })
})
