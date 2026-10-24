import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway, type AuthenticatedUser } from '../../../core/auth/supabase-auth.gateway'
import { MOCK_PUBLIC_PROJECTS } from '../data-access/mock-public-projects'
import { PublicProjectDetailComponent } from './public-project-detail'
import { detailStrings } from '../strings/detail.strings'
import { matchStrings } from '../strings/match.strings'

function createFixture() {
  TestBed.configureTestingModule({
    imports: [PublicProjectDetailComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(PublicProjectDetailComponent)
  fixture.componentRef.setInput('projectId', 'eco-pack')
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

describe('PublicProjectDetailComponent', () => {
  it('renders the API cover with alternative text and hides a failed image', () => {
    const { fixture, http } = createFixture()
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      imageUrl: 'https://example.test/cover.jpg',
      imageAlt: 'Portada EcoPack',
    } })
    fixture.detectChanges()
    const image = fixture.nativeElement.querySelector('img') as HTMLImageElement
    expect(image.src).toBe('https://example.test/cover.jpg')
    expect(image.alt).toBe('Portada EcoPack')
    image.dispatchEvent(new Event('error'))
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('img')).toBeNull()
    expect(fixture.nativeElement.textContent).toContain(MOCK_PUBLIC_PROJECTS['eco-pack'].name)
    fixture.destroy()
    http.verify()
  })

  it.each(['loading', 'loaded', 'empty', 'error'])('keeps a named dialog and all dismissal options in the %s state', (state) => {
    const { fixture, http } = createFixture()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    if (state === 'loaded') request.flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    if (state === 'empty') request.flush({}, { status: 404, statusText: 'Not Found' })
    if (state === 'error') request.flush({}, { status: 500, statusText: 'Unavailable' })
    fixture.detectChanges()

    const closed = vi.fn()
    fixture.componentInstance.closed.subscribe(closed)
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement
    expect(dialog).not.toBeNull()
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.textContent).toBe(detailStrings.publicTitle)
    dialog.click()
    expect(closed).not.toHaveBeenCalled()
    fixture.nativeElement.querySelector('.modal-backdrop').click()
    expect(closed).toHaveBeenCalledOnce()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    expect(closed).toHaveBeenCalledTimes(2)
    fixture.nativeElement.querySelector(`button[aria-label="${detailStrings.publicCloseAria}"]`).click()
    expect(closed).toHaveBeenCalledTimes(3)

    fixture.destroy()
    http.verify()
  })

  it('focuses the close control while loading, traps Tab after loading, and restores focus and scrolling', () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const previousOverflow = document.body.style.overflow
    const { fixture, http } = createFixture()
    const close = fixture.nativeElement.querySelector(`button[aria-label="${detailStrings.publicCloseAria}"]`)
    expect(document.activeElement).toBe(close)
    expect(document.body.style.overflow).toBe('hidden')

    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()
    expect(document.activeElement).toBe(close)
    const actions = fixture.nativeElement.querySelectorAll('button:not([disabled]), a[href]') as NodeListOf<HTMLElement>
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true }))
    expect(document.activeElement).toBe(actions[actions.length - 1])
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true }))
    expect(document.activeElement).toBe(close)

    fixture.destroy()
    expect(document.activeElement).toBe(opener)
    expect(document.body.style.overflow).toBe(previousOverflow)
    opener.remove()
    http.verify()
  })

  it('shows loading then renders the server project and public team', () => {
    const { fixture, http } = createFixture()
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Cargando proyecto')

    const close = vi.fn()
    fixture.componentInstance.closed.subscribe(close)

    const project = {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      department: 'Santa Cruz',
      externalLinks: [
        ...MOCK_PUBLIC_PROJECTS['eco-pack'].externalLinks,
        { type: 'LINKEDIN', url: 'https://linkedin.example/ecopack', label: 'LinkedIn' },
      ],
      members: [
        ...MOCK_PUBLIC_PROJECTS['eco-pack'].members,
        {
          profileId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
          displayName: 'Persona Dos',
          avatarStoragePath: null,
          displayTitle: 'Socio',
          isFeatured: false,
        },
      ],
      opportunities: [{
        title: 'Desarrollador Frontend',
        opportunityType: 'TALENT',
        description: 'Implementa la experiencia web.',
        city: 'Cochabamba',
        department: 'Cochabamba',
        remoteAllowed: true,
        requiredSkills: ['Angular', 'TypeScript'],
      }],
    }

    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: project })
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('EcoPack Bolivia')
    expect(fixture.nativeElement.textContent).toContain('Reduce residuos')
    expect(fixture.nativeElement.textContent).toContain('Persona Uno')
    expect(fixture.nativeElement.textContent).toContain('Persona Dos')
    expect(fixture.nativeElement.textContent).toContain('Desarrollador Frontend')
    expect(fixture.nativeElement.textContent).toContain('Habilidades requeridas')
    expect(fixture.nativeElement.textContent).toContain('Angular')
    expect(fixture.nativeElement.textContent).toContain('TypeScript')

    // Cerrar con el botón
    const closeButton = fixture.nativeElement.querySelector('button[aria-label*="Cerrar"], button[aria-label*="Volver"]')
    expect(closeButton).not.toBeNull()
    closeButton.click()
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
    expect(fixture.nativeElement.textContent).not.toContain('Reintentar')

    const button = fixture.nativeElement.querySelector('button')
    expect(button).not.toBeNull()
    button.click()
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
    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({
      data: {
        ...MOCK_PUBLIC_PROJECTS['eco-pack'],
        name: '<img src=x onerror=alert(1)>',
        externalLinks: [
          { type: 'WEBSITE', url: 'javascript:alert(1)', label: 'Unsafe' },
          { type: 'WEBSITE', url: 'https://safe.example', label: '<b>Safe site</b>' },
        ],
      },
    })
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('[onerror]')).toBeNull()
    expect(fixture.nativeElement.textContent).toContain('<img src=x onerror=alert(1)>')

    http.verify()
  })

  it.each([0, 500])('lets the visitor retry HTTP %s and recover', (status) => {
    const { fixture, http } = createFixture()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    if (status === 0) request.error(new ProgressEvent('error'))
    else request.flush({}, { status, statusText: 'Unavailable' })
    fixture.detectChanges()

    const retry = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (button) => button.textContent?.includes('Reintentar'),
    )
    expect(retry).toBeDefined()
    retry!.click()
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Cargando proyecto')

    http.expectOne('http://localhost:8080/api/v1/projects/eco-pack').flush({ data: MOCK_PUBLIC_PROJECTS['eco-pack'] })
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('EcoPack Bolivia')

    http.verify()
  })

  it('cancels the previous request when a different project is selected', () => {
    const { fixture, http } = createFixture()
    const previous = http.expectOne('http://localhost:8080/api/v1/projects/eco-pack')
    fixture.componentRef.setInput('projectId', 'different-project')
    fixture.detectChanges()
    expect(previous.cancelled).toBe(true)

    http
      .expectOne('http://localhost:8080/api/v1/projects/different-project')
      .flush({ data: { ...MOCK_PUBLIC_PROJECTS['eco-pack'], name: 'Otro proyecto' } })
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

  describe('opportunity match', () => {
    const PROJECT_URL = 'http://localhost:8080/api/v1/projects/eco-pack'
    const project = {
      ...MOCK_PUBLIC_PROJECTS['eco-pack'],
      opportunities: [{
        title: 'Desarrollador Frontend',
        opportunityType: 'TALENT',
        description: 'Implementa la experiencia web.',
        city: null,
        department: null,
        remoteAllowed: true,
        requiredSkills: ['Angular'],
      }],
    }
    const match = {
      opportunityId: 'opportunity-1',
      title: 'Desarrollador Frontend',
      opportunityType: 'TALENT',
      score: 72,
      onboardingCompleted: true,
      missingFactors: [],
      factors: [],
    }

    function createWithUser(user: AuthenticatedUser | null) {
      TestBed.configureTestingModule({
        imports: [PublicProjectDetailComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: SupabaseAuthGateway, useValue: { session: signal(user) } },
        ],
      })
      const fixture = TestBed.createComponent(PublicProjectDetailComponent)
      fixture.componentRef.setInput('projectId', 'eco-pack')
      fixture.detectChanges()
      const http = TestBed.inject(HttpTestingController)
      http.expectOne(PROJECT_URL).flush({ data: project })
      fixture.detectChanges()
      return { fixture, http }
    }

    it('shows the explained match under each opportunity for a signed-in person', () => {
      const { fixture, http } = createWithUser({ id: 'user-1', email: null })
      http.expectOne('http://localhost:8080/api/v1/projects/' + project.id + '/opportunity-matches')
        .flush({ data: [match] })
      fixture.detectChanges()

      const element = fixture.nativeElement as HTMLElement
      expect(element.querySelector('pulso-opportunity-match')?.textContent)
        .toContain(matchStrings.scoreLabel(72))
      expect(element.textContent).not.toContain(matchStrings.signedOut)
      fixture.destroy()
      http.verify()
    })

    it('invites an anonymous visitor to sign in instead of requesting a personal match', () => {
      const { fixture, http } = createWithUser(null)

      expect((fixture.nativeElement as HTMLElement).textContent).toContain(matchStrings.signedOut)
      expect(fixture.nativeElement.querySelector('pulso-opportunity-match')).toBeNull()
      http.verify()
      fixture.destroy()
    })

    it('keeps the project readable when the match cannot be loaded', () => {
      const { fixture, http } = createWithUser({ id: 'user-1', email: null })
      http.expectOne('http://localhost:8080/api/v1/projects/' + project.id + '/opportunity-matches')
        .flush({}, { status: 500, statusText: 'Unavailable' })
      fixture.detectChanges()

      expect(fixture.nativeElement.querySelector('pulso-opportunity-match')).toBeNull()
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Desarrollador Frontend')
      fixture.destroy()
      http.verify()
    })

    it('ignores a match whose title does not belong to the opportunity at that position', () => {
      const { fixture, http } = createWithUser({ id: 'user-1', email: null })
      http.expectOne('http://localhost:8080/api/v1/projects/' + project.id + '/opportunity-matches')
        .flush({ data: [{ ...match, title: 'Otra oportunidad' }] })
      fixture.detectChanges()

      expect(fixture.nativeElement.querySelector('pulso-opportunity-match')).toBeNull()
      fixture.destroy()
      http.verify()
    })
  })
})
