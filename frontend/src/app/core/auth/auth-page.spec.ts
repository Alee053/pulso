import { provideHttpClient } from '@angular/common/http'
import { provideHttpClientTesting } from '@angular/common/http/testing'
import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { currentLanguage } from '../i18n/language'
import { AuthPageComponent } from './auth-page'
import { SupabaseAuthGateway } from './supabase-auth.gateway'
import { authStrings } from './strings/auth.strings'

describe('AuthPageComponent language', () => {
  beforeEach(() => {
    localStorage.clear()
    currentLanguage.set('es')
    TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SupabaseAuthGateway,
          useValue: {
            session: signal(null),
            isLoading: signal(false),
            isConfigured: signal(true),
            initialize: () => Promise.resolve(),
          },
        },
      ],
    })
  })

  afterEach(() => {
    localStorage.clear()
    currentLanguage.set('es')
  })

  it('lets a visitor switch the page copy between Spanish and English before signing in', () => {
    const fixture = TestBed.createComponent(AuthPageComponent)
    fixture.detectChanges()
    const element = fixture.nativeElement as HTMLElement
    const title = element.querySelector('#auth-page-title') as HTMLElement
    expect(title.textContent).toContain('Conecta con las ideas')

    const english = Array.from(element.querySelectorAll<HTMLButtonElement>('.language-switcher__option'))
      .find((option) => option.lang === 'en') as HTMLButtonElement
    english.click()
    fixture.detectChanges()

    expect(title.textContent).toContain('Connect with the ideas')
    expect(element.textContent).toContain('Your community of projects')
    expect(document.documentElement.lang).toBe('en')
    fixture.destroy()
  })

  it('keeps the English module aligned with the Spanish one', () => {
    currentLanguage.set('en')

    expect(authStrings.passwordMinLength).toBe(6)
    expect(authStrings.submitSignIn).toBe('Sign in')
  })
})
