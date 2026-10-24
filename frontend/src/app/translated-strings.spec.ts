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
import { interestOptions } from './features/discovery/data-access/interests'
import { signalLabels } from './features/discovery/domain/recommendations'
import { intentionOptions, proficiencyOptions, projectStageOptions as preferenceStageOptions } from './features/discovery/domain/preferences'
import { projectStageOptions as formStageOptions } from './features/discovery/domain/project'
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

describe('option lists built from translated strings', () => {
  afterEach(() => currentLanguage.set('es'))

  it('follow the current language instead of the one active when the module loaded', () => {
    const stage = preferenceStageOptions.find((option) => option.id === 'EARLY_STAGE')!
    const formStage = formStageOptions.find((option) => option.value === 'EARLY_STAGE')!
    const intention = intentionOptions.find((option) => option.id === 'WORK')!
    const level = proficiencyOptions.find((option) => option.id === 'EXPERT')!
    const interest = interestOptions.find((option) => option.id === 'invest')!
    expect([stage.label, formStage.label, intention.label, level.label, interest.label, interest.shortLabel])
      .toEqual(['Etapa temprana', 'Etapa temprana', 'Trabajar', 'Experto', 'Invertir', 'Invertir'])

    currentLanguage.set('en')

    expect([stage.label, formStage.label, intention.label, level.label])
      .toEqual(['Early stage', 'Early stage', 'Work', 'Expert'])
    expect(interest.label).toBe('Invest')
    expect(interest.description).toBe('Teams with potential and a story to follow.')
    expect(signalLabels['clean-energy']).toBe('Clean energy')
  })

  it('explains a recommendation in the current language', () => {
    currentLanguage.set('en')

    expect(recommendationStrings.matched('Technology', 'La Paz'))
      .toBe('Your interests connect with Technology in La Paz.')
  })
})
