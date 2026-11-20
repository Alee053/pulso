import type { ProjectStatus } from './project-review'

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
