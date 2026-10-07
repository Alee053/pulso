import { projectStageStrings } from '../strings/project.strings'

export const projectStages = [
  'IDEA',
  'PROTOTYPE',
  'MVP',
  'EARLY_STAGE',
  'GROWING',
  'ESTABLISHED',
] as const

export type ProjectStage = (typeof projectStages)[number]

export interface ProjectCreateFormValue {
  slug: string
  name: string
  tagline: string
  description: string
  sectorId: string
  city: string
  stage: ProjectStage
}

export interface CreatedProject {
  id: string
  slug: string
  name: string
  tagline: string
  description: string | null
  sectorId: string
  city: string
  stage: ProjectStage
  status: string
}

export interface CreatedProjectOwner {
  id: string
  projectId: string
  profileId: string
  role: string
  membershipStatus: string
}

export interface CreatedDraftProject {
  project: CreatedProject
  owner: CreatedProjectOwner
}

export interface ProjectUpdateFormValue {
  name?: string
  tagline?: string
  description?: string | null
  valueProposition?: string | null
  stage?: ProjectStage
  sectorId?: string
  city?: string | null
  department?: string | null
}

export interface UpdatedProject {
  id: string
  slug: string
  name: string
  tagline: string
  description: string | null
  valueProposition: string | null
  sectorId: string
  stage: ProjectStage
  city: string | null
  department: string | null
  status: string
  verificationStatus: string
  createdByProfileId: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectStageOption {
  value: ProjectStage
  label: string
}

export const projectStageOptions: readonly ProjectStageOption[] = [
  { value: 'IDEA', label: projectStageStrings.IDEA },
  { value: 'PROTOTYPE', label: projectStageStrings.PROTOTYPE },
  { value: 'MVP', label: projectStageStrings.MVP },
  { value: 'EARLY_STAGE', label: projectStageStrings.EARLY_STAGE },
  { value: 'GROWING', label: projectStageStrings.GROWING },
  { value: 'ESTABLISHED', label: projectStageStrings.ESTABLISHED },
]
