import type { InterestId } from './startup'
import { intentionStrings, proficiencyStrings } from '../strings/onboarding.strings'
import { projectStageStrings } from '../strings/project.strings'

export type OnboardingIntention = typeof onboardingIntentions[number]

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
  preferredTags: string[]
  preferredSkills: string[]
  skills: ProfileSkill[]
  onboardingCompletedAt: string | null
  recommendationWeights: Record<string, number>
}

export interface OnboardingPreferencesPayload {
  intentions: OnboardingIntention[]
  preferredSectors: string[]
  preferredStages: ProjectStage[]
  preferredTags: string[]
  skills: { skillId: string; proficiency: SkillProficiency }[]
  completeOnboarding: true
}

export const projectStageOptions: readonly ProjectStageOption[] = [
  { id: 'IDEA', label: projectStageStrings.IDEA },
  { id: 'PROTOTYPE', label: projectStageStrings.PROTOTYPE },
  { id: 'MVP', label: projectStageStrings.MVP },
  { id: 'EARLY_STAGE', label: projectStageStrings.EARLY_STAGE },
  { id: 'GROWING', label: projectStageStrings.GROWING },
  { id: 'ESTABLISHED', label: projectStageStrings.ESTABLISHED },
]

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

export const onboardingIntentions = [
  'DISCOVER', 'BUY', 'COLLABORATE', 'WORK', 'INVEST', 'FIND_PARTNERS',
  'FIND_CUSTOMERS', 'FIND_PROVIDERS', 'EXPLORE_ECOSYSTEM',
] as const

export interface IntentionOption {
  id: OnboardingIntention
  label: string
}

export const intentionOptions: readonly IntentionOption[] = onboardingIntentions.map(
  (id) => ({ id, label: intentionStrings[id] }),
)

export const skillProficiencies = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'] as const
export type SkillProficiency = typeof skillProficiencies[number]
export interface ProfileSkill {
  skillId: string
  proficiency: SkillProficiency | null
}
export const proficiencyOptions = skillProficiencies.map(
  (id) => ({ id, label: proficiencyStrings[id] }),
)

export function isIntention(value: unknown): value is OnboardingIntention {
  return onboardingIntentions.some((id) => id === value)
}

export function isProjectStage(value: unknown): value is ProjectStage {
  return projectStageOptions.some((option) => option.id === value)
}

export function isProficiency(value: unknown): value is SkillProficiency {
  return skillProficiencies.some((id) => id === value)
}

export function toOnboardingPreferencesPayload(
  intentions: readonly OnboardingIntention[],
  preferredSectors: readonly string[],
  preferredStages: readonly ProjectStage[],
  preferredTags: readonly string[],
  skills: readonly ProfileSkill[],
): OnboardingPreferencesPayload {
  return {
    intentions: [...intentions],
    preferredSectors: [...preferredSectors],
    preferredStages: [...preferredStages],
    preferredTags: [...preferredTags],
    skills: skills.map((skill) => {
      if (!isProficiency(skill.proficiency)) throw new Error('Skill proficiency is required')
      return { skillId: skill.skillId, proficiency: skill.proficiency }
    }),
    completeOnboarding: true,
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
