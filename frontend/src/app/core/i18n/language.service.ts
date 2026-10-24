import { Injectable } from '@angular/core'
import { currentLanguage, isLanguage, supportedLanguages, type Language } from './language'

export const languageStorageKey = 'pulso.language'

/**
 * Owns the language preference: restores it from the browser, validates it,
 * persists changes and keeps the document language in sync for assistive
 * technology and browser tools. The preference is per browser, not per account.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly language = currentLanguage.asReadonly()
  readonly supported = supportedLanguages

  constructor() {
    const stored = readStoredLanguage()
    if (stored) {
      currentLanguage.set(stored)
    }
    this.syncDocument()
  }

  set(language: Language): void {
    if (!isLanguage(language)) {
      return
    }

    currentLanguage.set(language)
    storeLanguage(language)
    this.syncDocument()
  }

  private syncDocument(): void {
    document.documentElement.lang = currentLanguage()
  }
}

function readStoredLanguage(): Language | null {
  try {
    const stored = localStorage.getItem(languageStorageKey)
    return isLanguage(stored) ? stored : null
  } catch {
    return null
  }
}

function storeLanguage(language: Language): void {
  try {
    localStorage.setItem(languageStorageKey, language)
  } catch {
    // The preference still applies to this session when storage is unavailable.
  }
}
