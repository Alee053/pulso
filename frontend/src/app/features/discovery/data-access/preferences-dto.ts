export interface OnboardingPreferencesDto {
  profileId: string
  intentions: string[]
  preferredSectors: string[]
  preferredStages: string[]
  preferredTags: string[]
  preferredSkills: string[]
  skills: { skillId: string; proficiency: string | null }[]
  onboardingCompletedAt: string | null
  recommendationWeights: Record<string, number>
}
