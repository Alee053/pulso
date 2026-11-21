import type { ProjectStatus } from './project-review'
import type { ProjectStage } from './project'

export interface MembershipResult {
  projectId: string
  membershipStatus: 'INVITED' | 'ACTIVE'
}

export interface InvitationFailure {
  message: string
  retryable: boolean
}

export interface CurrentProjectMembership {
  projectId: string
  name: string
  tagline: string
  description: string | null
  valueProposition: string | null
  sectorId: string
  sectorName: string
  stage: ProjectStage
  city: string | null
  department: string | null
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  membershipStatus: 'ACTIVE'
  status?: ProjectStatus
  verificationStatus?: string
  reviewNote?: string | null
  reviewedAt?: string | null
}

export interface CurrentProjectsPage {
  data: CurrentProjectMembership[]
  meta: { offset: number; limit: number; hasMore: boolean }
}
