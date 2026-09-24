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

export const customerRangeOptions = [
  { value: '0_100', label: '0 a 100' },
  { value: '100_300', label: '100 a 300' },
  { value: '300_600', label: '300 a 600' },
  { value: '600_1000', label: '600 a 1.000' },
  { value: '1000_3000', label: '1.000 a 3.000' },
  { value: '3000_PLUS', label: 'Más de 3.000' },
] as const

export type CustomerRange = (typeof customerRangeOptions)[number]['value']

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
