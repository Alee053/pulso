export type ProjectMembershipRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type ProjectMembershipStatus = 'INVITED' | 'ACTIVE' | 'LEFT' | 'REMOVED'

export interface ManagedProjectMember {
  id: string
  projectId: string
  profileId: string | null
  displayName: string | null
  inviteeEmail: string | null
  role: ProjectMembershipRole
  membershipStatus: ProjectMembershipStatus
}

export interface ManagedProjectMembersPage {
  data: ManagedProjectMember[]
  meta: { offset: number; limit: number; hasMore: boolean }
}

export interface MembershipMutationResult {
  id: string
  projectId: string
  profileId: string | null
  role: ProjectMembershipRole
  membershipStatus: ProjectMembershipStatus
}

export interface ActorMembershipChange {
  projectId: string
  role: ProjectMembershipRole
}
