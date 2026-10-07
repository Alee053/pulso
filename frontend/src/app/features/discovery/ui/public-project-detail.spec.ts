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
    const project = {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      department: 'Santa Cruz',
      externalLinks: [
        ...MOCK_PUBLIC_PROJECTS['eco-pack'].externalLinks,
        { type: 'LINKEDIN', url: 'https://linkedin.example/ecopack', label: 'LinkedIn' },
      ],
      members: [
        ...MOCK_PUBLIC_PROJECTS['eco-pack'].members,
        { profileId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', displayName: 'Persona Dos', avatarStoragePath: null, displayTitle: 'Socio', isFeatured: false },
      ],
    }
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: project })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="dialog"]').textContent).toContain('EcoPack Bolivia')
    expect(fixture.nativeElement.textContent).toContain('Reduce residuos y mejora el valor de los desechos agrícolas.')
    expect(fixture.nativeElement.textContent).toContain('Cochabamba · Santa Cruz')
    expect(fixture.nativeElement.textContent).toContain('Persona Uno')
    expect(fixture.nativeElement.textContent).toContain('Miembro destacado')
    expect(fixture.nativeElement.textContent).toContain('Persona Dos')
    const links = Array.from(fixture.nativeElement.querySelectorAll('nav[aria-label="Enlaces públicos del proyecto"] a')) as HTMLAnchorElement[]
    expect(links.map(link => link.textContent?.trim())).toEqual(['Sitio web', 'LinkedIn'])
    expect(links.every(link => link.target === '_blank' && link.rel.includes('noopener'))).toBe(true)
    fixture.nativeElement.querySelector('button[aria-label]').click()
    expect(close).toHaveBeenCalledOnce()
    http.verify()
  })

  it.each([400, 403])('shows HTTP %s with a return action and no retry', (status) => {
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

  it('shows a published project that returns 404 as unavailable without offering retry', () => {
    const { fixture, http } = createFixture()
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({}, { status: 404, statusText: 'Not Found' })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Proyecto no disponible')
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull()
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar')
    http.verify()
  })

  it('renders API text as text and omits external links with unsafe schemes', () => {
    const { fixture, http } = createFixture()
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      name: '<img src=x onerror=alert(1)>',
      externalLinks: [
        { type: 'WEBSITE', url: 'javascript:alert(1)', label: 'Unsafe' },
        { type: 'WEBSITE', url: 'https://safe.example', label: '<b>Safe site</b>' },
      ],
    } })
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[onerror]')).toBeNull()
    expect(fixture.nativeElement.textContent).toContain('<img src=x onerror=alert(1)>')
    const links = Array.from(fixture.nativeElement.querySelectorAll('nav[aria-label="Enlaces públicos del proyecto"] a')) as HTMLAnchorElement[]
    expect(links).toHaveLength(1)
    expect(links[0]?.getAttribute('href')).toBe('https://safe.example')
    expect(links[0]?.textContent).toContain('<b>Safe site</b>')
    expect(fixture.nativeElement.querySelector('nav b')).toBeNull()
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
