import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent } from '../../../shared/icon/icon'
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
  @Output() interestToggled = new EventEmitter<InterestId>()
  @Output() started = new EventEmitter<void>()

  isSelected(interest: InterestOption): boolean {
    return this.selectedInterests.includes(interest.id)
  }
}
