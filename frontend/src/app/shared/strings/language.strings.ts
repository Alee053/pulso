import { defineStrings } from '../../core/i18n/define-strings'

/** Language names are shown in their own language so anyone can find theirs. */
export const languageNames = {
  es: 'Español',
  en: 'English',
} as const

export const languageStrings = defineStrings(
  { label: 'Idioma' },
  { label: 'Language' },
)
