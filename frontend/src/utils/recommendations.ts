import { interestOptions } from '../data/interests'
import type { Decision, InterestId, Startup } from '../types/startup'

export type PreferenceWeights = Record<string, number>

export const signalLabels: Record<string, string> = {
  agriculture: 'Agrotech',
  collaboration: 'Colaboración',
  'clean-energy': 'Energía limpia',
  commerce: 'Comercio',
  community: 'Comunidad',
  education: 'Educación',
  food: 'Alimentos',
  growth: 'Crecimiento',
  impact: 'Impacto',
  innovation: 'Innovación',
  investment: 'Inversión',
  local: 'Local',
  mobility: 'Movilidad',
  products: 'Productos',
  sustainability: 'Sostenibilidad',
  technology: 'Tecnología',
  opportunities: 'Oportunidades',
}

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

export function getActiveSignals(interests: InterestId[]): string[] {
  return Array.from(
    new Set(
      interests.flatMap((interest) => interestSignals.get(interest) ?? []),
    ),
  )
    .map((signal) => signalLabels[signal] ?? signal)
    .slice(0, 5)
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
  preferenceWeights: PreferenceWeights = {},
): string {
  const selectedInterestIds = new Set(interests)
  const matchingLabels = interestOptions
    .filter(
      (interest) =>
        selectedInterestIds.has(interest.id) &&
        interest.signals.some((signal) => startup.tags.includes(signal)),
    )
    .map((interest) => interest.shortLabel.toLowerCase())
  const learnedLabels = startup.tags
    .filter((tag) => (preferenceWeights[tag] ?? 0) > 0)
    .map((tag) => (signalLabels[tag] ?? tag).toLowerCase())
  const labels = Array.from(new Set([...matchingLabels, ...learnedLabels])).slice(0, 2)

  if (labels.length > 0) {
    return `Tus intereses conectan con ${labels.join(' y ')} en ${startup.city}.`
  }

  return `Te mostramos ${startup.name} como una exploración nueva en ${startup.city}, cerca de proyectos que están creciendo.`
}
