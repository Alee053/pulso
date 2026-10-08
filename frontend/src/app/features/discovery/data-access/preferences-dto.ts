export interface OnboardingPreferencesDto {
  profileId: string
  intentions: string[]
  preferredSectors: string[]
  preferredStages: string[]
  preferredTags: string[]
  preferredSkills: string[]
  recommendationWeights: Record<string, number>
}
