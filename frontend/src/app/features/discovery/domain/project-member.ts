export const membershipRoles = ['OWNER', 'ADMIN', 'MEMBER'] as const
export const membershipStatuses = ['INVITED', 'ACTIVE', 'LEFT', 'REMOVED'] as const
export type MembershipRole = (typeof membershipRoles)[number]
export type MembershipStatus = (typeof membershipStatuses)[number]

export interface ProjectMember {
  id: string
  projectId: string
  profileId: string | null
  role: MembershipRole
  membershipStatus: MembershipStatus
  displayName: string | null
  displayTitle: string | null
}

export interface ProjectMembersPage {
  data: ProjectMember[]
  actor: { role: MembershipRole; membershipStatus: 'ACTIVE' }
  meta: { offset: number; limit: number; hasMore: boolean }
}

export type MembershipChange =
  | { action: 'activate' }
  | { action: 'changeRole'; role: MembershipRole }
  | { action: 'withdraw' }

export type UpdatedMembership = Pick<ProjectMember, 'id' | 'projectId' | 'profileId' | 'role' | 'membershipStatus'>

export interface MembershipFailure {
  message: string
  refreshPermissions: boolean
}
