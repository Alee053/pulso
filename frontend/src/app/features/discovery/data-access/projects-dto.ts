export interface ProjectCreateRequestDto {
  slug: string
  name: string
  tagline: string
  description: string | null
  sectorId: string
  city: string
  stage: string
}

export interface ProjectCreateResponseDto {
  project: {
    id: string
    slug: string
    name: string
    tagline: string
    description: string | null
    sectorId: string
    city: string
    stage: string
    status: string
    createdByProfileId: string
  }
  owner: {
    id: string
    projectId: string
    profileId: string
    role: string
    membershipStatus: string
  }
}
export interface MemberInvitationDto {
  id: string
  projectId: string
  inviteeEmail: string
  profileId: string | null
  role: string
  membershipStatus: 'INVITED' | 'ACTIVE'
  createdByProfileId: string
}