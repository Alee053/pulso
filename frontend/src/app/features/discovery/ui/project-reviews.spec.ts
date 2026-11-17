import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { ProjectReviewCandidate } from '../domain/project-review'
import { ProjectReviewsComponent } from './project-reviews'

const path = 'http://localhost:8080/api/v1/admin/project-reviews'
const pagePath = `${path}?offset=0&limit=20`
const candidate: ProjectReviewCandidate = {
  id: 'project-1', name: 'EcoPack', tagline: 'Envases sostenibles', description: 'Una descripción completa.',
  valueProposition: 'Reducir residuos.', sector: 'Sostenibilidad', sectorActive: true, stage: 'EARLY_STAGE',
  city: 'La Paz', department: 'La Paz', status: 'PENDING_REVIEW', verificationStatus: 'UNVERIFIED',
  creatorName: 'Persona Pulso', activeOwnerCount: 1, canReview: true, createdAt: '2026-11-16T18:00:00Z', publishedAt: null,
}

function setup() {
  TestBed.configureTestingModule({ imports: [ProjectReviewsComponent], providers: [provideHttpClient(), provideHttpClientTesting()] })
  const fixture = TestBed.createComponent(ProjectReviewsComponent)
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

function page(data: ProjectReviewCandidate[] = [candidate], offset = 0, hasMore = false) {
  return { data, meta: { offset, limit: 20, hasMore, noteMaxLength: 2000 } }
}

describe('ProjectReviewsComponent', () => {
  it('loads review information and labels the note and basic-verification controls', () => {
    const { fixture, http } = setup()
    expect(fixture.nativeElement.textContent).toContain('Cargando proyectos pendientes')
    http.expectOne(pagePath).flush(page())
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Una descripción completa.')
    expect(fixture.nativeElement.textContent).toContain('Reducir residuos.')
    expect(fixture.nativeElement.textContent).toContain('Etapa temprana')
    const note: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea')
    expect(fixture.nativeElement.querySelector(`label[for="${note.id}"]`).textContent).toContain('Nota para el equipo')
    expect(note.getAttribute('aria-describedby')).toContain('review-hint-')
    expect(fixture.nativeElement.querySelector('input[type=checkbox]').closest('label').textContent).toContain('Verificación básica')
    http.verify()
  })

  it('requires a rejection note and sends only the decision and normalized note', () => {
    const { fixture, http } = setup()
    http.expectOne(pagePath).flush(page())
    fixture.detectChanges()
    fixture.componentInstance.decide(candidate, 'REJECT')
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('textarea').getAttribute('aria-invalid')).toBe('true')
    expect(fixture.nativeElement.textContent).toContain('Escribe una nota')
    http.expectNone(`${path}/project-1/decision`)
    fixture.componentInstance.draft(candidate.id).note = '  Completar la descripción.  '
    fixture.componentInstance.decide(candidate, 'REJECT')
    fixture.detectChanges()
    const request = http.expectOne(`${path}/project-1/decision`)
    expect(request.request.body).toEqual({ decision: 'REJECT', note: 'Completar la descripción.' })
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true)
    request.flush({ projectId: candidate.id, status: 'REJECTED', verificationStatus: 'UNVERIFIED',
      reviewNote: 'Completar la descripción.', reviewedByProfileId: 'moderator', reviewedAt: '2026-11-16T18:00:00Z', publishedAt: null })
    http.expectOne(pagePath).flush(page([]))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Decisión registrada.')
    expect(fixture.nativeElement.textContent).toContain('No hay proyectos pendientes')
    http.verify()
  })

  it('approves with an explicit basic verification and reloads the queue after a decision', () => {
    const { fixture, http } = setup()
    http.expectOne(pagePath).flush(page([candidate], 0, true))
    fixture.detectChanges()
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('input[type=checkbox]')
    checkbox.checked = true
    checkbox.dispatchEvent(new Event('change'))
    fixture.componentInstance.decide(candidate, 'APPROVE')
    fixture.componentInstance.decide(candidate, 'APPROVE')
    const request = http.expectOne(`${path}/project-1/decision`)
    expect(request.request.body).toEqual({ decision: 'APPROVE', verificationStatus: 'VERIFIED' })
    request.flush({ projectId: candidate.id, status: 'PUBLISHED', verificationStatus: 'VERIFIED',
      reviewNote: null, reviewedByProfileId: 'moderator', reviewedAt: '2026-11-16T18:00:00Z', publishedAt: '2026-11-16T18:00:00Z' })
    const next = { ...candidate, id: 'project-2', name: 'Proyecto siguiente' }
    http.expectOne(pagePath).flush(page([next]))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Proyecto siguiente')
    expect(fixture.nativeElement.querySelector('[data-review-project="project-1"]')).toBeNull()
    http.verify()
  })

  it('disables self-review and publication without an active owner', () => {
    const { fixture, http } = setup()
    const ownProject = { ...candidate, canReview: false }
    http.expectOne(pagePath).flush(page([ownProject, { ...candidate, id: 'no-owner', activeOwnerCount: 0 }]))
    fixture.detectChanges()
    expect(fixture.nativeElement.querySelector('[data-review-project="project-1"] fieldset').disabled).toBe(true)
    expect(fixture.nativeElement.querySelector('[data-review-project="no-owner"] .solid-button').disabled).toBe(true)
    fixture.componentInstance.decide(ownProject, 'APPROVE')
    http.verify()
  })

  it('keeps the note and offers refresh when another reviewer decided first', () => {
    const { fixture, http } = setup()
    http.expectOne(pagePath).flush(page())
    fixture.componentInstance.draft(candidate.id).note = 'Nota conservada'
    fixture.componentInstance.decide(candidate, 'REJECT')
    http.expectOne(`${path}/project-1/decision`).flush({ detail: 'internal database error' }, { status: 409, statusText: 'Conflict' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('El proyecto cambió')
    expect(fixture.nativeElement.textContent).not.toContain('internal database error')
    expect(fixture.componentInstance.saved()).toBe(false)
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('Nota conservada')
    fixture.nativeElement.querySelector('[role=alert] button').click()
    http.expectOne(pagePath).flush(page([]))
    http.verify()
  })

  it('retries queue errors and renders an empty state without exposing provider details', () => {
    const { fixture, http } = setup()
    http.expectOne(pagePath).flush({ detail: 'private SQL' }, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).not.toContain('private SQL')
    fixture.nativeElement.querySelector('[role=alert] button').click()
    http.expectOne(pagePath).flush(page([]))
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('No hay proyectos pendientes')
    http.verify()
  })

  it('clears private review information if platform access is revoked', () => {
    const { fixture, http } = setup()
    http.expectOne(pagePath).flush(page())
    fixture.componentInstance.decide(candidate, 'APPROVE')
    http.expectOne(`${path}/project-1/decision`).flush({}, { status: 403, statusText: 'Forbidden' })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('ya no tiene permisos')
    expect(fixture.nativeElement.querySelector('article')).toBeNull()
    http.verify()
  })

  it('cancels pending reads when leaving the profile', () => {
    const { fixture, http } = setup()
    const pending = http.expectOne(pagePath)
    fixture.destroy()
    expect(pending.cancelled).toBe(true)
    http.verify()
  })
})
