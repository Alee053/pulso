import { signal } from '@angular/core'

export const supportedLanguages = ['es', 'en'] as const
export type Language = typeof supportedLanguages[number]
export const defaultLanguage: Language = 'es'

export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (supportedLanguages as readonly string[]).includes(value)
}

/**
 * The language every translatable string module reads. It lives outside Angular's
 * injector because string modules are plain constants imported by components,
 * domain files and services alike. Only `LanguageService` should write to it.
 */
export const currentLanguage = signal<Language>(defaultLanguage)
