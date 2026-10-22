import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { SkillCatalogItem } from '../domain/catalog'
import { ProjectOpportunitySkillsComponent } from './project-opportunity-skills'

const projectId = 'project-1'
const opportunitiesUrl = `http://localhost:8080/api/v1/projects/${projectId}/opportunities?offset=0&limit=50`
const skillsUrl = `http://localhost:8080/api/v1/projects/${projectId}/opportunities/opp-1/skills`

const catalog: SkillCatalogItem[] = [
  { id: 'skill-angular', slug: 'angular', name: 'Angular', category: 'Frontend' },
  { id: 'skill-ts', slug: 'typescript', name: 'TypeScript', category: 'Frontend' },
]

function setup() {
  TestBed.configureTestingModule({
    imports: [ProjectOpportunitySkillsComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(ProjectOpportunitySkillsComponent)
  fixture.componentRef.setInput('projectId', projectId)
  fixture.componentRef.setInput('projectName', 'Pulso')
  fixture.componentRef.setInput('skills', catalog)
  fixture.componentRef.setInput('catalogsLoaded', true)
  fixture.detectChanges()
  return { fixture, http: TestBed.inject(HttpTestingController) }
}

function loadOneOpportunity(
  fixture: ReturnType<typeof setup>['fixture'],
  http: HttpTestingController,
): void {
  fixture.nativeElement.querySelector('button').click()
  http.expectOne(opportunitiesUrl).flush({
    data: [{ id: 'opp-1', projectId, title: 'Desarrollador Frontend', status: 'OPEN' }],
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
    expect(fixture.nativeElement.textContent).toContain('REQUIRED')

    fixture.nativeElement.querySelector('article button').click()
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
})
