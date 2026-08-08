import { interestOptions } from '../data/interests'
import type { Decision, InterestId, Startup } from '../types/startup'

export type PreferenceWeights = Record<string, number>

const interestSignals = new Map(
  interestOptions.map((interest) => [interest.id, interest.signals]),
)

export function getMatchingSignals(
  startup: Startup,
  interests: InterestId[],
): string[] {
  const signals = new Set(interests.flatMap((interest) => interestSignals.get(interest) ?? []))
  return startup.tags.filter((tag) => signals.has(tag))
}

export function scoreStartup(
  startup: Startup,
  interests: InterestId[],
  preferenceWeights: PreferenceWeights = {},
): number {
  const matchingSignals = getMatchingSignals(startup, interests)
  const interestScore = matchingSignals.length * 1.8
  const learnedScore = startup.tags.reduce(
    (total, tag) => total + (preferenceWeights[tag] ?? 0),
    0,
  )
  const popularityScore = startup.popularity * 0.35
  const explorationScore = startup.exploration * 0.2

  return interestScore + learnedScore + popularityScore + explorationScore
}

export function rankStartups(
  startups: Startup[],
  interests: InterestId[],
  preferenceWeights: PreferenceWeights = {},
): Startup[] {
  return startups
    .map((startup, index) => ({
      startup,
      score: scoreStartup(startup, interests, preferenceWeights),
      index,
    }))
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .map(({ startup }) => startup)
}

export function adjustPreferenceWeights(
  startup: Startup,
  decision: Decision,
  preferenceWeights: PreferenceWeights,
): PreferenceWeights {
  const adjustment = decision === 'interested' ? 0.16 : -0.04

  return startup.tags.reduce(
    (weights, tag) => ({
      ...weights,
      [tag]: Number(((weights[tag] ?? 0) + adjustment).toFixed(2)),
    }),
    preferenceWeights,
  )
}

export function getRecommendationReason(
  startup: Startup,
  interests: InterestId[],
): string {
  if (interests.length === 0) {
    return startup.recommendation
  }

  const labels = interests
    .map((interest) => interestOptions.find((option) => option.id === interest)?.shortLabel)
    .filter(Boolean)

  if (labels.length === 0) {
    return startup.recommendation
  }

  return `Te interesa ${labels.join(', ').toLowerCase()} y ${startup.city}.`
}
