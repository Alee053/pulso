import { TestBed } from '@angular/core/testing'
import { Subject, of, throwError } from 'rxjs'
import { describe, expect, it, vi } from 'vitest'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProjectsApi } from '../data-access/projects-api'
import type { CreatedDraftProject } from '../domain/project'
import { ProjectCreateFormComponent } from './project-create-form'

const createdProject: CreatedDraftProject = {
  project: {
    id: 'project-1',
    slug: 'eco-pack',
    name: 'EcoPack Bolivia',
    tagline: 'Envases sostenibles',
    description: null,
    sectorId: 'sector-1',
    city: 'Cochabamba',
    stage: 'MVP',
    status: 'DRAFT',
  },
  owner: {
    id: 'member-1',
    projectId: 'project-1',
    profileId: 'profile-1',
    role: 'OWNER',
    membershipStatus: 'ACTIVE',
  },
}

describe('ProjectCreateFormComponent', () => {
  it('renders sectors and valid project stages', () => {
    const { fixture, component } = createComponent()

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    fixture.detectChanges()

    const text = fixture.nativeElement.textContent

    expect(text).toContain('Sostenibilidad')
    expect(text).toContain('Idea')
    expect(text).toContain('Prototipo')
    expect(text).toContain('MVP')
    expect(text).toContain('Etapa temprana')
    expect(text).toContain('En crecimiento')
    expect(text).toContain('Establecido')
    expect(text).toContain('Clientes actuales o potenciales')
    expect(text).toContain('0 a 100')
    expect(text).toContain('100 a 300')
    expect(text).toContain('300 a 600')
    expect(text).toContain('600 a 1.000')
    expect(text).toContain('1.000 a 3.000')
    expect(text).toContain('Más de 3.000')
  })

  it('shows validation messages for required fields', async () => {
    const { fixture, component, createDraft } = createComponent()

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    await component.submit()
    fixture.detectChanges()

    expect(createDraft).not.toHaveBeenCalled()

    const text = fixture.nativeElement.textContent

    expect(text).toContain('El nombre del proyecto es obligatorio.')
    expect(text).toContain('El identificador es obligatorio.')
    expect(text).toContain('La ciudad es obligatoria.')
    expect(text).toContain('La descripción corta es obligatoria.')
    expect(text).toContain('Debes seleccionar un sector.')
    expect(text).toContain('Debes seleccionar una etapa.')
    expect(text).toContain('Debes seleccionar un rango de clientes.')
  })

  it('creates a project only after a successful backend response', async () => {
    const { fixture, component, createDraft } = createComponent({
      createDraft: vi.fn().mockReturnValue(of(createdProject)),
    })

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    component.form.setValue({
      slug: 'eco-pack',
      name: 'EcoPack Bolivia',
      tagline: 'Envases sostenibles',
      description: '',
      sectorId: 'sector-1',
      city: 'Cochabamba',
      stage: 'MVP',
      customerRange: '0_100',
    })

    await component.submit()
    fixture.detectChanges()

    expect(createDraft).toHaveBeenCalledOnce()
    expect(createDraft).toHaveBeenCalledWith({
      slug: 'eco-pack',
      name: 'EcoPack Bolivia',
      tagline: 'Envases sostenibles',
      description: '',
      sectorId: 'sector-1',
      city: 'Cochabamba',
      stage: 'MVP',
    })

    expect(component.createdProject()).toEqual(createdProject)
    expect(fixture.nativeElement.textContent).toContain(
      'Proyecto creado correctamente.',
    )
  })

  it('shows a backend error without false success', async () => {
    const { fixture, component } = createComponent({
      createDraft: vi.fn().mockReturnValue(
        throwError(() => new Error('500')),
      ),
    })

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    component.form.setValue({
      slug: 'eco-pack',
      name: 'EcoPack',
      tagline: 'Envases sostenibles',
      description: '',
      sectorId: 'sector-1',
      city: 'Cochabamba',
      stage: 'IDEA',
      customerRange: '0_100',
    })

    await component.submit()
    fixture.detectChanges()

    expect(component.createdProject()).toBeNull()
    expect(fixture.nativeElement.textContent).toContain(
      'No pudimos crear el proyecto',
    )
    expect(fixture.nativeElement.textContent).not.toContain(
      'Proyecto creado correctamente.',
    )
  })

  it('prevents duplicate submissions while creation is pending', async () => {
    const pending = new Subject<CreatedDraftProject>()
    const createDraft = vi.fn().mockReturnValue(pending)

    const { fixture, component } = createComponent({ createDraft })

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    component.form.setValue({
      slug: 'eco-pack',
      name: 'EcoPack',
      tagline: 'Envases sostenibles',
      description: '',
      sectorId: 'sector-1',
      city: 'Cochabamba',
      stage: 'MVP',
      customerRange: '0_100',
    })

    const firstSubmission = component.submit()

    expect(component.isSubmitting()).toBe(true)

    await component.submit()

    expect(createDraft).toHaveBeenCalledOnce()

    pending.next(createdProject)
    pending.complete()

    await firstSubmission

    expect(component.isSubmitting()).toBe(false)
  })

  it('does not submit when there is no authenticated session', async () => {
    const { fixture, component, createDraft } = createComponent({
      authenticated: false,
    })

    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [
      {
        id: 'sector-1',
        slug: 'sostenibilidad',
        name: 'Sostenibilidad',
        description: null,
      },
    ])

    component.form.setValue({
      slug: 'eco-pack',
      name: 'EcoPack',
      tagline: 'Envases sostenibles',
      description: '',
      sectorId: 'sector-1',
      city: 'Cochabamba',
      stage: 'MVP',
      customerRange: '0_100',
    })

    await component.submit()
    fixture.detectChanges()

    expect(createDraft).not.toHaveBeenCalled()
    expect(fixture.nativeElement.textContent).toContain(
      'Debes iniciar sesión para crear un proyecto.',
    )
  })
})

function createComponent(options: {
  createDraft?: ReturnType<typeof vi.fn>
  authenticated?: boolean
} = {}) {
  const createDraft =
    options.createDraft ??
    vi.fn().mockReturnValue(of(createdProject))

  const authenticated = options.authenticated ?? true

  TestBed.configureTestingModule({
    providers: [
      {
        provide: ProjectsApi,
        useValue: { createDraft },
      },
      {
        provide: SupabaseAuthGateway,
        useValue: {
          session: () =>
            authenticated
              ? {
                  id: 'profile-1',
                  email: 'test@pulso.local',
                }
              : null,
        },
      },
    ],
  })

  const fixture = TestBed.createComponent(
    ProjectCreateFormComponent,
  )

  return {
    fixture,
    component: fixture.componentInstance,
    createDraft,
  }
}
