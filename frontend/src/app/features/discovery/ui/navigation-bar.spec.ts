import { TestBed } from '@angular/core/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { currentLanguage } from '../../../core/i18n/language'
import { NavigationBarComponent } from './navigation-bar'

function labels(element: HTMLElement): string[] {
  return Array.from(element.querySelectorAll('.desktop-nav__item'))
    .map((item) => item.textContent?.trim() ?? '')
}

describe('NavigationBarComponent language', () => {
  beforeEach(() => currentLanguage.set('es'))
  afterEach(() => currentLanguage.set('es'))

  it('renames the menu and its accessible names when the language changes', () => {
    const fixture = TestBed.createComponent(NavigationBarComponent)
    fixture.detectChanges()
    const element = fixture.nativeElement as HTMLElement
    expect(labels(element)).toEqual(['Descubrir', 'Crear proyecto', 'Mis equipos', 'Guardados', 'Perfil'])
    expect(element.querySelector('aside')?.getAttribute('aria-label')).toBe('Navegación principal')

    currentLanguage.set('en')
    fixture.detectChanges()

    expect(labels(element)).toEqual(['Discover', 'Create project', 'My teams', 'Saved', 'Profile'])
    expect(element.querySelector('aside')?.getAttribute('aria-label')).toBe('Main navigation')
    fixture.destroy()
  })

  it('keeps the active item while the labels change', () => {
    const fixture = TestBed.createComponent(NavigationBarComponent)
    fixture.componentRef.setInput('activeView', 'saved')
    fixture.detectChanges()

    currentLanguage.set('en')
    fixture.detectChanges()

    const active = fixture.nativeElement.querySelector('.desktop-nav__item--active') as HTMLElement
    expect(active.textContent).toContain('Saved')
    expect(active.getAttribute('aria-current')).toBe('page')
    fixture.destroy()
  })
})
