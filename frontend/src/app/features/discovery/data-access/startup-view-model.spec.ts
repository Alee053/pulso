import { describe, expect, it } from 'vitest'
import { toStartupViewModel } from './startup-view-model'

describe('toStartupViewModel', () => {
  it('maps API summaries to the domain model without exposing transport fields', () => {
    const startup = toStartupViewModel({
      id: 'project-1',
      slug: 'eco-pack',
      name: 'EcoPack Bolivia',
      tagline: 'Envases compostables.',
      sector: 'Sostenibilidad',
      city: 'Cochabamba',
      stage: 'MVP',
    })

    expect(startup).toMatchObject({
      id: 'project-1',
      name: 'EcoPack Bolivia',
      pitch: 'Envases compostables.',
      sector: 'Sostenibilidad',
      tags: ['sustainability', 'impact'],
    })
    expect(startup).not.toHaveProperty('slug')
  })

  it('derives a compact mark and safe defaults for incomplete presentation data', () => {
    const startup = toStartupViewModel({
      id: 'project-2',
      slug: 'new-project',
      name: '  Nuevo Proyecto  ',
      tagline: 'Una idea.',
      sector: 'Otro',
      city: 'La Paz',
      stage: 'DRAFT',
    })

    expect(startup.logoMark).toBe('NP')
    expect(startup.image).toBe('')
    expect(startup.founders).toEqual([])
  })
})
