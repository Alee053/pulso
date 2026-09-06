import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { OnboardingComponent } from './onboarding'

describe('OnboardingComponent', () => {
  it('renders domain catalog items and emits backend ids when options are selected', () => {
    const fixture = TestBed.createComponent(OnboardingComponent)
    const component = fixture.componentInstance
    component.catalogsLoaded = true
    component.sectors = [{
      id: 'sector-1',
      slug: 'sostenibilidad',
      name: 'Sostenibilidad',
      description: null,
    }]
    component.tags = [{
      id: 'tag-1',
      slug: 'ia',
      name: 'Inteligencia artificial',
      type: 'TOPIC',
    }]
    component.skills = [{
      id: 'skill-1',
      slug: 'java',
      name: 'Java',
      category: 'DESARROLLO',
    }]
    const sectors = vi.fn()
    const tags = vi.fn()
    const skills = vi.fn()
    component.sectorToggled.subscribe(sectors)
    component.tagToggled.subscribe(tags)
    component.skillToggled.subscribe(skills)

    fixture.detectChanges()

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
    buttons.find((button) => button.textContent?.includes('Sostenibilidad'))?.click()
    buttons.find((button) => button.textContent?.includes('Inteligencia artificial'))?.click()
    buttons.find((button) => button.textContent?.includes('Java'))?.click()

    expect(sectors).toHaveBeenCalledWith('sector-1')
    expect(tags).toHaveBeenCalledWith('tag-1')
    expect(skills).toHaveBeenCalledWith('skill-1')
  })

  it('shows loading, error with retry, and empty catalog states', () => {
    const fixture = TestBed.createComponent(OnboardingComponent)
    const component = fixture.componentInstance

    component.catalogsLoading = true
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain(
      'Cargando sectores, temas y habilidades',
    )

    component.catalogsLoading = false
    component.catalogsErrorMessage = 'Backend no disponible.'
    const retry = vi.fn()
    component.catalogsRetryRequested.subscribe(retry)
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Backend no disponible.')
    const retryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((button) => (button as HTMLButtonElement).textContent?.includes('Reintentar')) as HTMLButtonElement
    retryButton.click()
    expect(retry).toHaveBeenCalledOnce()

    component.catalogsErrorMessage = null
    component.catalogsLoaded = true
    component.sectors = []
    component.tags = []
    component.skills = []
    fixture.detectChanges()

    const text = fixture.nativeElement.textContent
    expect(text).toContain('No hay sectores activos disponibles.')
    expect(text).toContain('No hay tags activos disponibles.')
    expect(text).toContain('No hay habilidades activas disponibles.')
  })
})