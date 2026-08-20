import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import type { InterestId, InterestOption } from '../domain/startup'

@Component({
  selector: 'pulso-profile-view',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './profile-view.html',
})
export class ProfileViewComponent {
  @Input() interestOptions: readonly InterestOption[] = []
  @Input() selectedInterests: InterestId[] = []

  get selectedOptions(): readonly InterestOption[] {
    return this.interestOptions.filter((option) =>
      this.selectedInterests.includes(option.id),
    )
  }
}
