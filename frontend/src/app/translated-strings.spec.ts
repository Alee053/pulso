import { afterEach, describe, expect, it } from 'vitest'
import { authStrings } from './core/auth/strings/auth.strings'
import { currentLanguage, supportedLanguages, type Language } from './core/i18n/language'
import { cardStrings, feedStrings, searchStrings } from './features/discovery/strings/feed.strings'
import { detailStrings } from './features/discovery/strings/detail.strings'
import { navigationStrings } from './features/discovery/strings/navigation.strings'
import {
  interestStrings,
  intentionStrings,
  onboardingStrings,
  proficiencyStrings,
} from './features/discovery/strings/onboarding.strings'
import { profileStrings } from './features/discovery/strings/profile.strings'
import { projectStageStrings } from './features/discovery/strings/project.strings'
import { recommendationStrings } from './features/discovery/strings/recommendations.strings'
import { savedStrings } from './features/discovery/strings/saved.strings'
import { commonStrings } from './shared/strings/common.strings'
import { languageStrings } from './shared/strings/language.strings'

const modules: Record<string, object> = {
  authStrings,
  cardStrings,
  commonStrings,
  detailStrings,
  feedStrings,
  interestStrings,
  intentionStrings,
  languageStrings,
  navigationStrings,
  onboardingStrings,
  profileStrings,
  projectStageStrings,
  proficiencyStrings,
  recommendationStrings,
  savedStrings,
  searchStrings,
}

/** Reads every text of a module in the current language, calling functions with sample values. */
function readAll(value: unknown, path: string, into: Map<string, string | number | boolean>): void {
  if (typeof value === 'function') {
    into.set(path, (value as (...args: unknown[]) => string)(2, 3))
  } else if (typeof value === 'object' && value !== null) {
    for (const [key, child] of Object.entries(value)) {
      readAll(child, `${path}.${key}`, into)
    }
  } else {
    into.set(path, value as string | number | boolean)
  }
}

function snapshot(language: Language): Map<string, string | number | boolean> {
  currentLanguage.set(language)
  const texts = new Map<string, string | number | boolean>()
  for (const [name, module] of Object.entries(modules)) {
    readAll(module, name, texts)
  }
  return texts
}

describe('translated string modules', () => {
  afterEach(() => currentLanguage.set('es'))

  it('covers every supported language', () => {
    expect(supportedLanguages).toEqual(['es', 'en'])
  })

  it('offers the same texts, with the same kinds of value, in both languages', () => {
    const spanish = snapshot('es')
    const english = snapshot('en')

    expect([...english.keys()]).toEqual([...spanish.keys()])
    for (const [path, text] of spanish) {
      expect(typeof english.get(path), path).toBe(typeof text)
      if (typeof text === 'string') {
        expect((english.get(path) as string).trim(), path).not.toBe('')
      }
    }
  })

  it('does not leave Spanish copy in the English texts', () => {
    const english = snapshot('en')

    for (const [path, text] of english) {
      if (typeof text === 'string') {
        expect(text, path).not.toMatch(/[áéíóúñ¿¡Á-Ú]/)
      }
    }
  })

  it('translates most texts instead of repeating the Spanish ones', () => {
    const spanish = snapshot('es')
    const english = snapshot('en')
    const strings = [...spanish].filter(([, text]) => typeof text === 'string')
    const unchanged = strings.filter(([path, text]) => english.get(path) === text)

    expect(unchanged.length / strings.length).toBeLessThan(0.1)
  })
})
