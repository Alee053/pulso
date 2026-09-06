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
  sectors: SectorCatalogItem[]
  tags: TagCatalogItem[]
  skills: SkillCatalogItem[]
}

export const emptyReferenceCatalogs = (): ReferenceCatalogs => ({
  sectors: [],
  tags: [],
  skills: [],
})