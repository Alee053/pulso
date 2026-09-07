import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent } from '../../../shared/icon/icon'
import type {
  SectorCatalogItem,
  SkillCatalogItem,
  TagCatalogItem,
} from '../domain/catalog'
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
  @Input() catalogsLoading = false
  @Input() catalogsLoaded = false
  @Input() catalogsErrorMessage: string | null = null

  @Output() interestToggled = new EventEmitter<InterestId>()
  @Output() sectorToggled = new EventEmitter<string>()
  @Output() tagToggled = new EventEmitter<string>()
  @Output() skillToggled = new EventEmitter<string>()
  @Output() catalogsRetryRequested = new EventEmitter<void>()
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
}