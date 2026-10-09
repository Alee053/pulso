import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { map, type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import type {
  ReferenceCatalogs,
  SectorCatalogItem,
  SkillCatalogItem,
  TagCatalogItem,
} from '../domain/catalog'
import type {
  CatalogsResponseDto,
  SectorCatalogItemDto,
  SkillCatalogItemDto,
  TagCatalogItemDto,
} from './catalogs-dto'
import { isIntention, isProjectStage } from '../domain/preferences'
import { apiPaths } from './discovery.catalog.contract'

@Injectable({ providedIn: 'root' })
export class CatalogsApi {
  private readonly http = inject(HttpClient)

  load(): Observable<ReferenceCatalogs> {
    return this.http.get<CatalogsResponseDto>(
      `${appConfig.apiBaseUrl}${apiPaths.catalogs}`,
    ).pipe(
      map((response) => {
        if (!Array.isArray(response.data.intentions) || !response.data.intentions.every(isIntention)
          || !Array.isArray(response.data.stages) || !response.data.stages.every(isProjectStage)) {
          throw new Error('Invalid onboarding catalogs response')
        }
        return {
          intentions: response.data.intentions.filter(isIntention),
          stages: response.data.stages.filter(isProjectStage),
          sectors: response.data.sectors.map(toSectorCatalogItem),
          tags: response.data.tags.map(toTagCatalogItem),
          skills: response.data.skills.map(toSkillCatalogItem),
        }
      }),
    )
  }
}

function toSectorCatalogItem(dto: SectorCatalogItemDto): SectorCatalogItem {
  return {
    id: dto.id,
    slug: dto.slug,
    name: dto.name,
    description: dto.description,
  }
}

function toTagCatalogItem(dto: TagCatalogItemDto): TagCatalogItem {
  return {
    id: dto.id,
    slug: dto.slug,
    name: dto.name,
    type: dto.type,
  }
}

function toSkillCatalogItem(dto: SkillCatalogItemDto): SkillCatalogItem {
  return {
    id: dto.id,
    slug: dto.slug,
    name: dto.name,
    category: dto.category,
  }
}
