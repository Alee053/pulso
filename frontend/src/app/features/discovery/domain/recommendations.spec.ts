import { describe, expect, it } from 'vitest'

import { mockStartups } from '../data-access/mock-startups'
import {
  getActiveSignals,
  getRecommendationReason,
  rankStartups,
  scoreStartup,
} from './recommendations'

describe('discovery recommendations', () => {
  it('ranks every startup without mutating the source collection', () => {
    const originalOrder = mockStartups.map((startup) => startup.id)
    const ranked = rankStartups(mockStartups, ['discover'])
    const scores = ranked.map((startup) => scoreStartup(startup, ['discover']))

    expect(ranked).toHaveLength(mockStartups.length)
    expect(scores).toEqual([...scores].sort((left, right) => right - left))
    expect(mockStartups.map((startup) => startup.id)).toEqual(originalOrder)
  })

  it('exposes translated active signals and a useful reason', () => {
    const startup = mockStartups[0]

    expect(getActiveSignals(['discover', 'collaborate'])).toContain('Productos')
    expect(getRecommendationReason(startup, ['collaborate'])).toContain(startup.city)
  })
})
