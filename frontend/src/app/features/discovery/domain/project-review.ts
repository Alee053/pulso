export const projectStatuses = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'SUSPENDED'] as const
export type ProjectStatus = typeof projectStatuses[number]
export type BasicVerification = 'VERIFIED' | 'UNVERIFIED'

export interface ProjectReviewCandidate {
  id: string
  name: string
  tagline: string
  description: string | null
  valueProposition: string | null
  sector: string
  sectorActive: boolean
  stage: string
  city: string | null
  department: string | null
  status: 'PENDING_REVIEW'
  verificationStatus: string
  creatorName: string | null
  activeOwnerCount: number
  canReview: boolean
  createdAt: string
  publishedAt: string | null
}

export interface ProjectReviewPage {
  data: ProjectReviewCandidate[]
  meta: { offset: number; limit: number; hasMore: boolean; noteMaxLength: number }
}

export type ProjectReviewDecision =
  | { decision: 'APPROVE'; verificationStatus: BasicVerification; note?: string }
  | { decision: 'REJECT'; note: string }

export interface ProjectReviewResult {
  projectId: string
  status: 'PUBLISHED' | 'REJECTED'
  verificationStatus: string
  reviewNote: string | null
  reviewedByProfileId: string
  reviewedAt: string
  publishedAt: string | null
}
