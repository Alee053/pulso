import type { OnboardingIntention, ProjectStage } from './preferences'

export interface SectorCatalogItem {
  id: string
  slug: string
  name: string
  description: string | null
}

export interface TagCatalogItem {
  id: string
  slug: string
  name: string
  type: string
}

export interface SkillCatalogItem {
  id: string
  slug: string
  name: string
  category: string | null
}

export interface ReferenceCatalogs {
  intentions: OnboardingIntention[]
  stages: ProjectStage[]
  sectors: SectorCatalogItem[]
  tags: TagCatalogItem[]
  skills: SkillCatalogItem[]
}

export const emptyReferenceCatalogs = (): ReferenceCatalogs => ({
  intentions: [],
  stages: [],
  sectors: [],
  tags: [],
  skills: [],
})
