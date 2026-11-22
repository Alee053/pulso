import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { SkillCatalogItem } from '../domain/catalog'
import { opportunitySkillsStrings } from '../strings/opportunity-skills.strings'
import { ProjectOpportunitySkillsComponent } from './project-opportunity-skills'

const projectId = 'project-1'
const opportunitiesUrl = `http://localhost:8080/api/v1/projects/${projectId}/opportunities?offset=0&limit=50`
const skillsUrl = `http://localhost:8080/api/v1/projects/${projectId}/opportunities/opp-1/skills`

const catalog: SkillCatalogItem[] = [
  { id: 'skill-angular', slug: 'angular', name: 'Angular', category: 'Frontend' },
  { id: 'skill-ts', slug: 'typescript', name: 'TypeScript', category: 'Frontend' },
]

function setup(projectStatus: 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED' | 'SUSPENDED' = 'DRAFT') {
  TestBed.configureTestingModule({
    imports: [ProjectOpportunitySkillsComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(ProjectOpportunitySkillsComponent)
  fixture.componentRef.setInput('projectId', projectId)
  fixture.componentRef.setInput('projectName', 'Pulso')
  fixture.componentRef.setInput('skills', catalog)
  fixture.componentRef.setInput('catalogsLoaded', true)
  fixture.componentRef.setInput('projectStatus', projectStatus)
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

function loadOneOpportunity(
  fixture: ReturnType<typeof setup>['fixture'],
  http: HttpTestingController,
  status: 'DRAFT' | 'OPEN' = 'OPEN',
): void {
  fixture.nativeElement.querySelector('button').click()
  http.expectOne(opportunitiesUrl).flush({
    data: [{ id: 'opp-1', projectId, opportunityType: 'TALENT', title: 'Desarrollador Frontend',
      description: 'Construir el producto', status, city: 'Cochabamba', department: null,
      remoteAllowed: true, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z' }],
    meta: { offset: 0, limit: 50, hasMore: false },
  })
  http.expectOne(skillsUrl).flush([{
    skillId: 'skill-angular',
    slug: 'angular',
    name: 'Angular',
    importance: 'REQUIRED',
    active: true,
  }])
  fixture.detectChanges()
}

describe('ProjectOpportunitySkillsComponent', () => {
  it('lists associated skills, excludes duplicates, creates, reclassifies, and removes associations', async () => {
    const { fixture, http } = setup()
    loadOneOpportunity(fixture, http)

    expect(fixture.nativeElement.textContent).toContain('Angular')
    expect(fixture.nativeElement.textContent).toContain(opportunitySkillsStrings.required)

    fixture.nativeElement.querySelector('[data-action="add-skill"]').click()
    fixture.detectChanges()
    const skillSelect = fixture.nativeElement.querySelector('#skill-opp-1') as HTMLSelectElement
    expect([...skillSelect.options].map((option) => option.value)).toEqual(['', 'skill-ts'])
    skillSelect.value = 'skill-ts'
    skillSelect.dispatchEvent(new Event('change'))
    const importanceSelect = fixture.nativeElement.querySelector('#new-importance-opp-1') as HTMLSelectElement
    importanceSelect.value = 'PREFERRED'
    importanceSelect.dispatchEvent(new Event('change'))
    fixture.detectChanges()
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'))

    const create = http.expectOne(skillsUrl)
    expect(create.request.method).toBe('POST')
    expect(create.request.body).toEqual({ skillId: 'skill-ts', importance: 'PREFERRED' })
    create.flush({
      skillId: 'skill-ts',
      slug: 'typescript',
      name: 'TypeScript',
      importance: 'PREFERRED',
      active: true,
    })
    await Promise.resolve()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Habilidad guardada.')
    expect(fixture.nativeElement.textContent).toContain('TypeScript')

    const currentImportance = fixture.nativeElement.querySelector(
      '#importance-opp-1-skill-ts',
    ) as HTMLSelectElement
    currentImportance.value = 'REQUIRED'
    currentImportance.dispatchEvent(new Event('change'))
    const update = http.expectOne(`${skillsUrl}/skill-ts`)
    expect(update.request.method).toBe('PUT')
    expect(update.request.body).toEqual({ importance: 'REQUIRED' })
    update.flush({
      skillId: 'skill-ts',
      slug: 'typescript',
      name: 'TypeScript',
      importance: 'REQUIRED',
      active: true,
    })
    await Promise.resolve()
    fixture.detectChanges()
    expect((fixture.nativeElement.querySelector('#importance-opp-1-skill-ts') as HTMLSelectElement).value)
      .toBe('REQUIRED')

    fixture.nativeElement.querySelector('[aria-label="Quitar TypeScript de la oportunidad"]').click()
    const remove = http.expectOne(`${skillsUrl}/skill-ts`)
    expect(remove.request.method).toBe('DELETE')
    remove.flush(null)
    await Promise.resolve()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).not.toContain('TypeScript')
    http.verify()
  })

  it('keeps failures visible and offers retry without showing backend details', () => {
    const { fixture, http } = setup()
    fixture.nativeElement.querySelector('button').click()
    http.expectOne(opportunitiesUrl).flush(
      { detail: 'private SQL error' },
      { status: 503, statusText: 'Unavailable' },
    )
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar las oportunidades')
    expect(fixture.nativeElement.textContent).not.toContain('private SQL error')
    fixture.nativeElement.querySelector('[role="alert"] button').click()
    http.expectOne(opportunitiesUrl).flush({
      data: [],
      meta: { offset: 0, limit: 50, hasMore: false },
    })
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Este proyecto todavía no tiene oportunidades.')
    http.verify()
  })

  it('creates, edits, and changes an opportunity status using the project lifecycle', async () => {
    const { fixture, http } = setup('PUBLISHED')
    loadOneOpportunity(fixture, http)
    fixture.componentInstance.beginCreate()
    fixture.componentInstance.opportunityForm.patchValue({
      opportunityType: 'COLLABORATION',
      title: '  Validar el producto  ',
      description: '  Buscamos equipos piloto.  ',
      city: '  La Paz  ',
      department: '  La Paz  ',
      remoteAllowed: false,
    })
    const creating = fixture.componentInstance.saveOpportunity()
    const create = http.expectOne('http://localhost:8080/api/v1/projects/project-1/opportunities')
    expect(create.request.method).toBe('POST')
    expect(create.request.body).toEqual({
      opportunityType: 'COLLABORATION', title: 'Validar el producto',
      description: 'Buscamos equipos piloto.', city: 'La Paz', department: 'La Paz',
      remoteAllowed: false, status: 'OPEN',
    })
    create.flush({
      id: 'opp-2', projectId, opportunityType: 'COLLABORATION', title: 'Validar el producto',
      description: 'Buscamos equipos piloto.', status: 'OPEN', city: 'La Paz', department: 'La Paz',
      remoteAllowed: false, createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z',
    })
    await creating
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Validar el producto')

    const created = fixture.componentInstance.opportunities()[0]
    fixture.componentInstance.beginEdit(created)
    fixture.componentInstance.opportunityForm.controls.title.setValue('Producto piloto')
    const updating = fixture.componentInstance.saveOpportunity()
    const update = http.expectOne('http://localhost:8080/api/v1/projects/project-1/opportunities/opp-2')
    expect(update.request.method).toBe('PUT')
    expect(update.request.body).toEqual({
      opportunityType: 'COLLABORATION', title: 'Producto piloto',
      description: 'Buscamos equipos piloto.', city: 'La Paz', department: 'La Paz', remoteAllowed: false,
    })
    update.flush({ ...created, title: 'Producto piloto', updatedAt: '2026-11-03T00:00:00Z' })
    await updating
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Producto piloto')

    const changed = fixture.componentInstance.opportunities()[0]
    const pausing = fixture.componentInstance.changeOpportunityStatus(changed, 'PAUSE')
    const transition = http.expectOne('http://localhost:8080/api/v1/projects/project-1/opportunities/opp-2/actions')
    expect(transition.request.method).toBe('POST')
    expect(transition.request.body).toEqual({ action: 'PAUSE' })
    transition.flush({ ...changed, status: 'PAUSED', updatedAt: '2026-11-04T00:00:00Z' })
    await pausing
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Estado: Pausada')
    http.verify()
  })

  it('does not offer or send OPEN for a draft opportunity until the project is published', async () => {
    const { fixture, http } = setup('PENDING_REVIEW')
    loadOneOpportunity(fixture, http, 'DRAFT')

    expect(fixture.nativeElement.querySelector('[data-status-action="OPEN"]')).toBeNull()
    expect(fixture.nativeElement.querySelector('[data-status-action="ARCHIVE"]')).not.toBeNull()

    await fixture.componentInstance.changeOpportunityStatus(
      fixture.componentInstance.opportunities()[0],
      'OPEN',
    )

    http.expectNone('http://localhost:8080/api/v1/projects/project-1/opportunities/opp-1/actions')
    expect(fixture.nativeElement.textContent).not.toContain('No pudimos actualizar el estado.')
    http.verify()
  })

  it('requires a location when an opportunity is not remote', async () => {
    const { fixture, http } = setup()
    loadOneOpportunity(fixture, http)
    fixture.componentInstance.beginCreate()
    fixture.componentInstance.opportunityForm.patchValue({
      title: 'Oportunidad local', description: 'Descripción', remoteAllowed: false,
      city: '', department: '',
    })

    await fixture.componentInstance.saveOpportunity()

    expect(fixture.componentInstance.errorMessage()).toBe('Indica una ubicación o habilita la participación remota.')
    http.verify()
  })
})
