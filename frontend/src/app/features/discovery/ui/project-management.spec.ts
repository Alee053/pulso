import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import { ProjectManagementComponent } from './project-management'

const project: CurrentProjectMembership = {
  projectId: 'project-1',
  name: 'EcoPack',
  tagline: 'Envases reutilizables',
  description: 'Descripción actual',
  valueProposition: 'Menos residuos',
  sectorId: 'sector-1',
  sectorName: 'Tecnología',
  stage: 'MVP',
  city: 'Cochabamba',
  department: 'Cochabamba',
  role: 'OWNER',
  membershipStatus: 'ACTIVE',
  status: 'DRAFT',
  verificationStatus: 'UNVERIFIED',
}

const sectors = [
  { id: 'sector-1', slug: 'technology', name: 'Tecnología', description: null },
  { id: 'sector-2', slug: 'sustainability', name: 'Sostenibilidad', description: null },
]

function setup(initial = project) {
  TestBed.configureTestingModule({
    imports: [ProjectManagementComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(ProjectManagementComponent)
  fixture.componentRef.setInput('project', initial)
  fixture.componentRef.setInput('sectors', sectors)
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

describe('ProjectManagementComponent', () => {
  it('edits only changed project fields and publishes the returned project to the team view', async () => {
    const { fixture, http } = setup()
    let changed: CurrentProjectMembership | undefined
    fixture.componentInstance.projectChanged.subscribe((value) => { changed = value })
    fixture.nativeElement.querySelector('[data-action="edit-project"]').click()
    fixture.detectChanges()
    fixture.componentInstance.form.patchValue({ name: '  EcoPack nuevo  ' })

    const saving = fixture.componentInstance.save()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/project-1')
    expect(request.request.method).toBe('PATCH')
    expect(request.request.body).toEqual({ name: 'EcoPack nuevo' })
    request.flush({ project: {
      id: project.projectId, slug: 'ecopack', name: 'EcoPack nuevo', tagline: project.tagline,
      description: project.description, valueProposition: project.valueProposition,
      sectorId: project.sectorId, stage: project.stage, city: project.city,
      department: project.department, status: 'DRAFT', verificationStatus: 'UNVERIFIED',
      createdByProfileId: 'owner-1', createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z',
    } })
    await saving
    fixture.detectChanges()

    expect(changed?.name).toBe('EcoPack nuevo')
    expect(fixture.nativeElement.textContent).toContain('La información del proyecto se guardó.')
    http.verify()
  })

  it('requests publication and archives through the authorized lifecycle endpoints', async () => {
    const { fixture, http } = setup()
    let changed: CurrentProjectMembership | undefined
    fixture.componentInstance.projectChanged.subscribe((value) => { changed = value })

    const publication = fixture.componentInstance.requestPublication()
    const request = http.expectOne('http://localhost:8080/api/v1/projects/project-1/publication-request')
    expect(request.request.method).toBe('POST')
    expect(request.request.body).toEqual({})
    request.flush({ projectId: project.projectId, status: 'PENDING_REVIEW' })
    await publication
    expect(changed?.status).toBe('PENDING_REVIEW')

    fixture.componentRef.setInput('project', { ...project, status: 'PUBLISHED' })
    fixture.detectChanges()
    const archiving = fixture.componentInstance.archive()
    const archive = http.expectOne('http://localhost:8080/api/v1/projects/project-1/archive')
    expect(archive.request.method).toBe('POST')
    archive.flush({ projectId: project.projectId, status: 'ARCHIVED' })
    await archiving

    expect(changed?.status).toBe('ARCHIVED')
    http.verify()
  })

  it('keeps the edit form open and explains lifecycle conflicts', async () => {
    const { fixture, http } = setup()
    fixture.componentInstance.beginEdit()
    fixture.componentInstance.form.controls.tagline.setValue('Nueva propuesta breve')
    const saving = fixture.componentInstance.save()
    http.expectOne('http://localhost:8080/api/v1/projects/project-1')
      .flush({}, { status: 409, statusText: 'Conflict' })
    await saving
    fixture.detectChanges()

    expect(fixture.componentInstance.isEditing()).toBe(true)
    expect(fixture.nativeElement.textContent).toContain('El proyecto cambió de estado. Actualiza la lista e inténtalo de nuevo.')
    http.verify()
  })

  it('requires a city before sending a project update', async () => {
    const { fixture, http } = setup()
    fixture.componentInstance.beginEdit()
    fixture.componentInstance.form.controls.city.setValue(' ')

    await fixture.componentInstance.save()
    fixture.detectChanges()

    expect(fixture.componentInstance.form.controls.city.hasError('required')).toBe(true)
    expect(fixture.nativeElement.textContent).toContain('La ciudad es obligatoria.')
    http.expectNone('http://localhost:8080/api/v1/projects/project-1')
    http.verify()
  })
})
