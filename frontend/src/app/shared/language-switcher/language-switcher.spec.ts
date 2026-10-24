import { TestBed } from '@angular/core/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { currentLanguage } from '../../core/i18n/language'
import { LanguageSwitcherComponent } from './language-switcher'

function options(element: HTMLElement): HTMLButtonElement[] {
  return Array.from(element.querySelectorAll<HTMLButtonElement>('.language-switcher__option'))
}

describe('LanguageSwitcherComponent', () => {
  beforeEach(() => {
    localStorage.clear()
    currentLanguage.set('es')
  })

  afterEach(() => {
    localStorage.clear()
    currentLanguage.set('es')
  })

  it('offers every language by its own name inside a labelled group', () => {
    const fixture = TestBed.createComponent(LanguageSwitcherComponent)
    fixture.detectChanges()
    const element = fixture.nativeElement as HTMLElement

    const group = element.querySelector('[role="group"]') as HTMLElement
    expect(group.getAttribute('aria-label')).toBe('Idioma')
    expect(options(element).map((option) => option.textContent?.trim())).toEqual(['Español', 'English'])
    expect(options(element).map((option) => option.lang)).toEqual(['es', 'en'])
    fixture.destroy()
  })

  it('marks the active language as pressed and switches when another one is chosen', () => {
    const fixture = TestBed.createComponent(LanguageSwitcherComponent)
    fixture.detectChanges()
    const element = fixture.nativeElement as HTMLElement
    const [spanish, english] = options(element) as [HTMLButtonElement, HTMLButtonElement]
    expect(spanish.getAttribute('aria-pressed')).toBe('true')
    expect(english.getAttribute('aria-pressed')).toBe('false')

    english.click()
    fixture.detectChanges()

    expect(currentLanguage()).toBe('en')
    expect(spanish.getAttribute('aria-pressed')).toBe('false')
    expect(english.getAttribute('aria-pressed')).toBe('true')
    expect(localStorage.getItem('pulso.language')).toBe('en')
    expect((element.querySelector('[role="group"]') as HTMLElement).getAttribute('aria-label')).toBe('Language')
    fixture.destroy()
  })
})
