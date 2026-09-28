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

export interface ProjectUpdateRequestDto {
  name?: string
  tagline?: string
  description?: string | null
  valueProposition?: string | null
  stage?: string
  sectorId?: string
  city?: string | null
  department?: string | null
}

export interface ProjectUpdateResponseDto {
  project: {
    id: string
    slug: string
    name: string
    tagline: string
    description: string | null
    valueProposition: string | null
    sectorId: string
    stage: string
    city: string | null
    department: string | null
    status: string
    verificationStatus: string
    createdByProfileId: string | null
    createdAt: string
    updatedAt: string
  }
}
