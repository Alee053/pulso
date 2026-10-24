import { computed } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it } from 'vitest'
import { defineStrings } from './define-strings'
import { currentLanguage } from './language'

const strings = defineStrings(
  {
    title: 'Hola',
    count: (total: number): string => `${total} proyectos`,
    nested: { greeting: 'Buenos días' },
  },
  {
    title: 'Hello',
    count: (total: number): string => `${total} projects`,
    nested: { greeting: 'Good morning' },
  },
)

describe('defineStrings', () => {
  afterEach(() => currentLanguage.set('es'))

  it('reads the reference language by default and keeps the original shape', () => {
    expect(strings.title).toBe('Hola')
    expect(strings.count(2)).toBe('2 proyectos')
    expect(strings.nested.greeting).toBe('Buenos días')
    expect(Object.keys(strings)).toEqual(['title', 'count', 'nested'])
  })

  it('switches text, interpolated text and nested text with the current language', () => {
    currentLanguage.set('en')

    expect(strings.title).toBe('Hello')
    expect(strings.count(2)).toBe('2 projects')
    expect(strings.nested.greeting).toBe('Good morning')
  })

  it('makes derived values react to a language change', () => {
    TestBed.runInInjectionContext(() => {
      const title = computed(() => strings.title)

      expect(title()).toBe('Hola')
      currentLanguage.set('en')
      expect(title()).toBe('Hello')
    })
  })

  it('falls back to the reference language when a translation is missing at runtime', () => {
    const partial = defineStrings(
      { title: 'Hola', note: 'Nota' },
      // Simulates untyped data, such as a JSON file, that lost a key.
      { title: 'Hello' } as unknown as { title: string; note: string },
    )
    currentLanguage.set('en')

    expect(partial.title).toBe('Hello')
    expect(partial.note).toBe('Nota')
  })

  it('requires every key and function signature of the reference in the translation', () => {
    defineStrings(
      { title: 'Hola', count: (total: number): string => `${total}` },
      // @ts-expect-error The English module is missing the `count` key.
      { title: 'Hello' },
    )
    defineStrings(
      { title: 'Hola' },
      // @ts-expect-error The English module has a key the reference does not have.
      { title: 'Hello', extra: 'Extra' },
    )
    defineStrings(
      { count: (total: number): string => `${total}` },
      // @ts-expect-error The English function takes a different parameter.
      { count: (total: string): string => total },
    )
  })
})
