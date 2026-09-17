import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent } from '../../../shared/icon/icon'
import type {
  SectorCatalogItem,
  SkillCatalogItem,
  TagCatalogItem,
} from '../domain/catalog'
import type { ProjectStage, ProjectStageOption } from '../domain/preferences'
import type { InterestId, InterestOption } from '../domain/startup'

@Component({
  selector: 'pulso-onboarding',
  standalone: true,
  imports: [CommonModule, BrandMarkComponent, IconComponent],
  templateUrl: './onboarding.html',
})
export class OnboardingComponent {
  @Input() interestOptions: readonly InterestOption[] = []
  @Input() selectedInterests: InterestId[] = []

  @Input() sectors: readonly SectorCatalogItem[] = []
  @Input() tags: readonly TagCatalogItem[] = []
  @Input() skills: readonly SkillCatalogItem[] = []
  @Input() selectedSectorIds: string[] = []
  @Input() selectedTagIds: string[] = []
  @Input() selectedSkillIds: string[] = []
  @Input() stages: readonly ProjectStageOption[] = []
  @Input() selectedStageIds: ProjectStage[] = []
  @Input() catalogsLoading = false
  @Input() catalogsLoaded = false
  @Input() catalogsErrorMessage: string | null = null
  @Input() preferencesLoading = false
  @Input() preferencesLoaded = false
  @Input() preferencesErrorMessage: string | null = null
  @Input() preferencesSaving = false
  @Input() preferencesSaveErrorMessage: string | null = null

  @Output() interestToggled = new EventEmitter<InterestId>()
  @Output() sectorToggled = new EventEmitter<string>()
  @Output() tagToggled = new EventEmitter<string>()
  @Output() skillToggled = new EventEmitter<string>()
  @Output() stageToggled = new EventEmitter<ProjectStage>()
  @Output() catalogsRetryRequested = new EventEmitter<void>()
  @Output() preferencesRetryRequested = new EventEmitter<void>()
  @Output() preferencesSaveRetryRequested = new EventEmitter<void>()
  @Output() started = new EventEmitter<void>()

  isSelected(interest: InterestOption): boolean {
    return this.selectedInterests.includes(interest.id)
  }

  isSectorSelected(sector: SectorCatalogItem): boolean {
    return this.selectedSectorIds.includes(sector.id)
  }

  isTagSelected(tag: TagCatalogItem): boolean {
    return this.selectedTagIds.includes(tag.id)
  }

  isSkillSelected(skill: SkillCatalogItem): boolean {
    return this.selectedSkillIds.includes(skill.id)
  }

  isStageSelected(stage: ProjectStageOption): boolean {
    return this.selectedStageIds.includes(stage.id)
  }
}
