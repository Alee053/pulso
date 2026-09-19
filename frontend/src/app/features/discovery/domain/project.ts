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

export interface ProjectStageOption {
  value: ProjectStage
  label: string
}

export const projectStageOptions: readonly ProjectStageOption[] = [
  { value: 'IDEA', label: 'Idea' },
  { value: 'PROTOTYPE', label: 'Prototipo' },
  { value: 'MVP', label: 'MVP' },
  { value: 'EARLY_STAGE', label: 'Etapa temprana' },
  { value: 'GROWING', label: 'En crecimiento' },
  { value: 'ESTABLISHED', label: 'Establecido' },
]