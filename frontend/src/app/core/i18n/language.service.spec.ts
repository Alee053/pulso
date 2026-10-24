import { TestBed } from '@angular/core/testing'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentLanguage, type Language } from './language'
import { LanguageService, languageStorageKey } from './language.service'

function create(): LanguageService {
  return TestBed.inject(LanguageService)
}

describe('LanguageService', () => {
  beforeEach(() => {
    localStorage.clear()
    currentLanguage.set('es')
    document.documentElement.lang = ''
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    currentLanguage.set('es')
  })

  it('starts in Spanish and declares it on the document', () => {
    const service = create()

    expect(service.language()).toBe('es')
    expect(document.documentElement.lang).toBe('es')
    expect(service.supported).toEqual(['es', 'en'])
  })

  it('restores the stored language', () => {
    localStorage.setItem(languageStorageKey, 'en')

    const service = create()

    expect(service.language()).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it.each(['fr', '', 'EN', '{"language":"en"}'])('ignores the invalid stored value %j', (stored) => {
    localStorage.setItem(languageStorageKey, stored)

    expect(create().language()).toBe('es')
  })

  it('changes, persists and declares the new language', () => {
    const service = create()

    service.set('en')

    expect(service.language()).toBe('en')
    expect(localStorage.getItem(languageStorageKey)).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })

  it('ignores a language that is not supported', () => {
    const service = create()

    service.set('fr' as Language)

    expect(service.language()).toBe('es')
    expect(localStorage.getItem(languageStorageKey)).toBeNull()
  })

  it('keeps working for the session when the browser blocks storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const service = create()

    service.set('en')

    expect(service.language()).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
