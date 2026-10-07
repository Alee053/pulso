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
}

export interface CurrentProjectsPage {
  data: CurrentProjectMembership[]
  meta: { offset: number; limit: number; hasMore: boolean }
}
