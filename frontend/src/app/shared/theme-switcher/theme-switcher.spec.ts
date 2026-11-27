import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it } from 'vitest'
import { ThemeService, ThemeSwitcherComponent } from './theme-switcher'

afterEach(() => {
  localStorage.removeItem('pulso.theme')
  delete document.documentElement.dataset['theme']
})

describe('Appearance preference', () => {
  it('follows the system for a new or unknown stored preference', () => {
    localStorage.setItem('pulso.theme', 'unknown')
    expect(TestBed.inject(ThemeService).preference()).toBe('system')
    expect(document.documentElement.dataset['theme']).toBe('system')
  })

  it('restores a saved choice and switches the document back to system through the control', () => {
    localStorage.setItem('pulso.theme', 'dark')
    const fixture = TestBed.createComponent(ThemeSwitcherComponent)
    fixture.detectChanges()
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select')
    expect(select.value).toBe('dark')
    expect(document.documentElement.dataset['theme']).toBe('dark')
    select.value = 'system'
    select.dispatchEvent(new Event('change'))
    expect(document.documentElement.dataset['theme']).toBe('system')
    expect(localStorage.getItem('pulso.theme')).toBe('system')
  })
})
