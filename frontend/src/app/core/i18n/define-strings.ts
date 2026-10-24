import { currentLanguage, defaultLanguage, type Language } from './language'

/**
 * The shape a translation must have: the same keys as the reference language,
 * strings for text and functions with the same parameters for interpolated text.
 */
export type Translated<T> = T extends (...args: infer Args) => string
  ? (...args: Args) => string
  : T extends string
    ? string
    : { readonly [Key in keyof T]: Translated<T[Key]> }

type Tree = Record<string, unknown>

/**
 * Declares a translatable string module. Spanish is the reference language and
 * English must provide exactly the same keys, so a missing translation is a
 * compile error. The returned object keeps the shape of the reference, so
 * `strings.title` and `strings.count(2)` work unchanged in templates and code.
 * Each read resolves the current language, which makes reads inside templates,
 * `computed` and `effect` reactive to a language change.
 */
export function defineStrings<const T extends Tree>(
  es: T,
  en: Translated<T>,
): Translated<T> {
  return build({ es, en: en as unknown as Tree }) as unknown as Translated<T>
}

function build(dictionaries: Record<Language, Tree>): Tree {
  const reference = dictionaries[defaultLanguage]
  const result: Tree = {}

  for (const key of Object.keys(reference)) {
    const sample = reference[key]
    if (typeof sample === 'object' && sample !== null && !Array.isArray(sample)) {
      Object.defineProperty(result, key, {
        enumerable: true,
        value: build({
          es: sample as Tree,
          en: (dictionaries.en[key] ?? sample) as Tree,
        }),
      })
      continue
    }

    Object.defineProperty(result, key, {
      enumerable: true,
      get: () => dictionaries[currentLanguage()][key] ?? sample,
    })
  }

  return result
}
