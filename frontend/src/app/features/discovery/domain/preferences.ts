import type { InterestId } from './startup'
import { projectStageStrings } from '../strings/project.strings'

export type OnboardingIntention =
  | 'DISCOVER'
  | 'BUY'
  | 'COLLABORATE'
  | 'WORK'
  | 'INVEST'
  | 'FIND_PARTNERS'
  | 'FIND_CUSTOMERS'
  | 'FIND_PROVIDERS'
  | 'EXPLORE_ECOSYSTEM'

export type ProjectStage =
  | 'IDEA'
  | 'PROTOTYPE'
  | 'MVP'
  | 'EARLY_STAGE'
  | 'GROWING'
  | 'ESTABLISHED'

export interface ProjectStageOption {
  id: ProjectStage
  label: string
}

export interface OnboardingPreferences {
  profileId: string
  intentions: OnboardingIntention[]
  preferredSectors: string[]
  preferredStages: ProjectStage[]
}

export interface OnboardingPreferencesPayload {
  intentions: OnboardingIntention[]
  preferredSectors: string[]
  preferredStages: ProjectStage[]
}

export const projectStageOptions: readonly ProjectStageOption[] = [
  { id: 'IDEA', label: projectStageStrings.IDEA },
  { id: 'PROTOTYPE', label: projectStageStrings.PROTOTYPE },
  { id: 'MVP', label: projectStageStrings.MVP },
  { id: 'EARLY_STAGE', label: projectStageStrings.EARLY_STAGE },
  { id: 'GROWING', label: projectStageStrings.GROWING },
  { id: 'ESTABLISHED', label: projectStageStrings.ESTABLISHED },
]

const intentionByInterest: Readonly<Record<InterestId, OnboardingIntention>> = {
  discover: 'DISCOVER',
  invest: 'INVEST',
  collaborate: 'COLLABORATE',
  opportunities: 'WORK',
  inspiration: 'EXPLORE_ECOSYSTEM',
}

const interestByIntention: Readonly<Partial<Record<OnboardingIntention, InterestId>>> = {
  DISCOVER: 'discover',
  WORK: 'opportunities',
  COLLABORATE: 'collaborate',
  INVEST: 'invest',
  EXPLORE_ECOSYSTEM: 'inspiration',
}

const projectStages = new Set<ProjectStage>(
  projectStageOptions.map((option) => option.id),
)

export function toOnboardingPreferencesPayload(
  interests: readonly InterestId[],
  preferredSectors: readonly string[],
  preferredStages: readonly ProjectStage[],
): OnboardingPreferencesPayload {
  return {
    intentions: interests.map((interest) => intentionByInterest[interest]),
    preferredSectors: [...preferredSectors],
    preferredStages: [...preferredStages],
  }
}

export function toInterestIds(
  intentions: readonly string[],
): InterestId[] {
  const selected = new Set<InterestId>()

  for (const intention of intentions) {
    const interest = interestByIntention[
      intention.trim().toUpperCase() as OnboardingIntention
    ]
    if (interest) {
      selected.add(interest)
    }
  }

  return [...selected]
}

export function toProjectStages(
  stages: readonly string[],
): ProjectStage[] {
  const selected = new Set<ProjectStage>()

  for (const stage of stages) {
    const normalized = stage.trim().toUpperCase() as ProjectStage
    if (projectStages.has(normalized)) {
      selected.add(normalized)
    }
  }

  return [...selected]
}
