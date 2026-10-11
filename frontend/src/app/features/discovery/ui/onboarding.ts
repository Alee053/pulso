import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import type {
  SectorCatalogItem,
  SkillCatalogItem,
  TagCatalogItem,
} from '../domain/catalog'
import { proficiencyOptions, type IntentionOption, type OnboardingIntention, type ProfileSkill, type ProjectStage, type ProjectStageOption } from '../domain/preferences'
import { onboardingStrings } from '../strings/onboarding.strings'

@Component({
  selector: 'pulso-onboarding',
  standalone: true,
  imports: [CommonModule, BrandMarkComponent, IconComponent],
  templateUrl: './onboarding.html',
})
export class OnboardingComponent {
  @Input() intentionOptions: readonly IntentionOption[] = []
  @Input() selectedIntentions: OnboardingIntention[] = []
  @Input() editing = false
  @Input() validationMessage: string | null = null

  @Input() sectors: readonly SectorCatalogItem[] = []
  @Input() tags: readonly TagCatalogItem[] = []
  @Input() skills: readonly SkillCatalogItem[] = []
  @Input() selectedSectorIds: string[] = []
  @Input() selectedTagIds: string[] = []
  @Input() selectedSkills: ProfileSkill[] = []
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

  @Output() intentionToggled = new EventEmitter<OnboardingIntention>()
  @Output() cancelled = new EventEmitter<void>()
  @Output() proficiencyChanged = new EventEmitter<{ skillId: string; proficiency: string }>()
  @Output() sectorToggled = new EventEmitter<string>()
  @Output() tagToggled = new EventEmitter<string>()
  @Output() skillToggled = new EventEmitter<string>()
  @Output() stageToggled = new EventEmitter<ProjectStage>()
  @Output() catalogsRetryRequested = new EventEmitter<void>()
  @Output() preferencesRetryRequested = new EventEmitter<void>()
  @Output() preferencesSaveRetryRequested = new EventEmitter<void>()
  @Output() started = new EventEmitter<void>()

  readonly strings = onboardingStrings
  readonly common = commonStrings

  readonly proficiencyOptions = proficiencyOptions

  get controlsDisabled(): boolean {
    return this.preferencesLoading || !this.preferencesLoaded || this.preferencesSaving
      || this.catalogsLoading || !this.catalogsLoaded
  }

  isSelected(intention: IntentionOption): boolean {
    return this.selectedIntentions.includes(intention.id)
  }

  proficiencyFor(skillId: string): string {
    return this.selectedSkills.find((skill) => skill.skillId === skillId)?.proficiency ?? ''
  }

  changeProficiency(skillId: string, event: Event): void {
    if (event.target instanceof HTMLSelectElement) {
      this.proficiencyChanged.emit({ skillId, proficiency: event.target.value })
    }
  }

  isSectorSelected(sector: SectorCatalogItem): boolean {
    return this.selectedSectorIds.includes(sector.id)
  }

  isTagSelected(tag: TagCatalogItem): boolean {
    return this.selectedTagIds.includes(tag.id)
  }

  isSkillSelected(skill: SkillCatalogItem): boolean {
    return this.selectedSkills.some((selected) => selected.skillId === skill.id)
  }

  isStageSelected(stage: ProjectStageOption): boolean {
    return this.selectedStageIds.includes(stage.id)
  }
}
