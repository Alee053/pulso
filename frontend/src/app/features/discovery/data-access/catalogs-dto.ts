export interface SectorCatalogItemDto {
  id: string
  slug: string
  name: string
  description: string | null
}

export interface TagCatalogItemDto {
  id: string
  slug: string
  name: string
  type: string
}

export interface SkillCatalogItemDto {
  id: string
  slug: string
  name: string
  category: string | null
}

export interface CatalogsResponseDto {
  data: {
    intentions: string[]
    stages: string[]
    sectors: SectorCatalogItemDto[]
    tags: TagCatalogItemDto[]
    skills: SkillCatalogItemDto[]
  }
}
