import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentLanguage } from '../../../core/i18n/language'
import { NavigationBarComponent } from './navigation-bar'

afterEach(() => {
  currentLanguage.set('es')
  history.replaceState(null, '', '/')
})

describe('NavigationBarComponent', () => {
  it('translates the desktop destinations and the notification control with its unread count', () => {
    const fixture = TestBed.createComponent(NavigationBarComponent)
    fixture.componentRef.setInput('unreadCount', 2)
    fixture.detectChanges()
    const el: HTMLElement = fixture.nativeElement
    const labels = () => Array.from(el.querySelectorAll('.desktop-nav__item'), item => item.textContent.trim())
    expect(labels()).toEqual(['Crear proyecto', 'Descubrir', 'Guardados', 'Mensajes', 'Solicitudes', 'Mis equipos', 'Perfil'])
    expect(el.querySelector('.notice-link')?.getAttribute('aria-label')).toBe('Notificaciones, 2 sin leer')
    currentLanguage.set('en')
    fixture.detectChanges()
    expect(labels()).toEqual(['Create project', 'Discover', 'Saved', 'Messages', 'Requests', 'My teams', 'Profile'])
    expect(el.querySelector('aside')?.getAttribute('aria-label')).toBe('Main navigation')
    expect(el.querySelector('.notice-link')?.getAttribute('aria-label')).toBe('Notifications, 2 unread')
  })

  it('keeps Messages directly reachable and current on mobile and desktop inside a thread', () => {
    const fixture = TestBed.createComponent(NavigationBarComponent)
    history.replaceState(null, '', '/conversaciones/00000000-0000-4000-8000-000000000001')
    fixture.componentRef.setInput('activeView', 'conversation')
    fixture.detectChanges()
    const navigate = vi.fn()
    fixture.componentInstance.navigated.subscribe(navigate)
    const el: HTMLElement = fixture.nativeElement
    expect(el.querySelector<HTMLAnchorElement>('.skip-link')?.pathname).toBe(location.pathname)
    const items = el.querySelectorAll<HTMLButtonElement>('.mobile-nav__item')
    expect(Array.from(items, item => item.textContent.trim())).toEqual(['Descubrir', 'Guardados', 'Mensajes', 'Más'])
    expect(items[2].getAttribute('aria-current')).toBe('page')
    items[2].click()
    expect(navigate).toHaveBeenCalledWith('messages')
    expect(el.querySelector('.desktop-nav__item[aria-current]')?.getAttribute('aria-keyshortcuts')).toBe('Alt+Shift+M')
  })

  it('offers the additional mobile destinations and respects the creation flag in both layouts', () => {
    const fixture = TestBed.createComponent(NavigationBarComponent)
    fixture.componentRef.setInput('projectCreationEnabled', false)
    fixture.detectChanges()
    const el: HTMLElement = fixture.nativeElement
    expect(el.textContent).not.toContain('Crear proyecto')
    expect(Array.from(el.querySelectorAll('.mobile-more button'), item => item.textContent.trim()))
      .toEqual(['Solicitudes', 'Mis equipos', 'Perfil'])
  })
})
