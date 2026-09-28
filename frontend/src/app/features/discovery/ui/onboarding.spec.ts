import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { projectStageOptions } from '../domain/preferences'
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
    component.stages = projectStageOptions.slice(0, 1)
    const sectors = vi.fn()
    const tags = vi.fn()
    const skills = vi.fn()
    const stages = vi.fn()
    component.sectorToggled.subscribe(sectors)
    component.tagToggled.subscribe(tags)
    component.skillToggled.subscribe(skills)
    component.stageToggled.subscribe(stages)

    fixture.detectChanges()

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
    buttons.find((button) => button.textContent?.includes('Sostenibilidad'))?.click()
    buttons.find((button) => button.textContent?.includes('Inteligencia artificial'))?.click()
    buttons.find((button) => button.textContent?.includes('Java'))?.click()
    buttons.find((button) => button.textContent?.includes('Idea'))?.click()

    expect(sectors).toHaveBeenCalledWith('sector-1')
    expect(tags).toHaveBeenCalledWith('tag-1')
    expect(skills).toHaveBeenCalledWith('skill-1')
    expect(stages).toHaveBeenCalledWith('IDEA')
  })

  it('shows loading, error with retry, and empty catalog states', () => {
    const fixture = TestBed.createComponent(OnboardingComponent)
    const component = fixture.componentInstance

    fixture.componentRef.setInput('catalogsLoading', true)
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain(
    'Cargando sectores, temas y habilidades',
    )

    fixture.componentRef.setInput('catalogsLoading', false)
    fixture.componentRef.setInput(
    'catalogsErrorMessage',
    'Backend no disponible.',
    )
    const retry = vi.fn()
    component.catalogsRetryRequested.subscribe(retry)
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Backend no disponible.')
    const retryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((button) => (button as HTMLButtonElement).textContent?.includes('Reintentar')) as HTMLButtonElement
    retryButton.click()
    expect(retry).toHaveBeenCalledOnce()

    fixture.componentRef.setInput('catalogsErrorMessage', null)
    fixture.componentRef.setInput('catalogsLoaded', true)
    fixture.componentRef.setInput('sectors', [])
    fixture.componentRef.setInput('tags', [])
    fixture.componentRef.setInput('skills', [])
    fixture.detectChanges()

    const text = fixture.nativeElement.textContent
    expect(text).toContain('No hay sectores activos disponibles.')
    expect(text).toContain('No hay tags activos disponibles.')
    expect(text).toContain('No hay habilidades activas disponibles.')
  })

  it('shows preference loading, save error, and retry controls', () => {
    const fixture = TestBed.createComponent(OnboardingComponent)
    const component = fixture.componentInstance
    const preferenceRetry = vi.fn()
    const saveRetry = vi.fn()
    component.preferencesRetryRequested.subscribe(preferenceRetry)
    component.preferencesSaveRetryRequested.subscribe(saveRetry)

    fixture.componentRef.setInput('preferencesLoading', true)
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain(
      'Cargando tus preferencias guardadas',
    )

    fixture.componentRef.setInput('preferencesLoading', false)
    fixture.componentRef.setInput(
      'preferencesErrorMessage',
      'Preferencias no disponibles.',
    )
    fixture.detectChanges()

    const preferenceRetryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((button) => (button as HTMLButtonElement).textContent?.trim() === 'Reintentar') as HTMLButtonElement
    preferenceRetryButton.click()
    expect(preferenceRetry).toHaveBeenCalledOnce()

    fixture.componentRef.setInput('preferencesErrorMessage', null)
    fixture.componentRef.setInput('preferencesLoaded', true)
    fixture.componentRef.setInput('selectedInterests', ['discover'])
    fixture.componentRef.setInput(
      'preferencesSaveErrorMessage',
      'No se pudo guardar.',
    )
    fixture.detectChanges()

    const saveRetryButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((button) => (button as HTMLButtonElement).textContent?.includes('Reintentar guardado')) as HTMLButtonElement
    saveRetryButton.click()
    expect(saveRetry).toHaveBeenCalledOnce()

    fixture.componentRef.setInput('preferencesSaving', true)
    fixture.detectChanges()

    const startButton = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((button) => (button as HTMLButtonElement).textContent?.includes('Guardando preferencias')) as HTMLButtonElement
    expect(startButton.disabled).toBe(true)
  })
})
