export const matchFactorKeys = [
  'SKILLS',
  'INTENTION',
  'INTERESTS',
  'SECTOR',
  'STAGE',
  'LOCATION',
] as const
export type MatchFactorKey = typeof matchFactorKeys[number]

export type MatchFactorStatus =
  | 'MATCHED'
  | 'PARTIAL'
  | 'NOT_MATCHED'
  | 'MISSING_PROFILE_DATA'
  | 'NOT_APPLICABLE'

export interface MatchFactor {
  key: MatchFactorKey
  status: MatchFactorStatus
  weight: number
  points: number
  matched: string[]
  unmatched: string[]
  reason: string | null
}

export interface OpportunityMatch {
  opportunityId: string
  title: string
  opportunityType: string
  score: number
  onboardingCompleted: boolean
  missingFactors: MatchFactorKey[]
  factors: MatchFactor[]
}
