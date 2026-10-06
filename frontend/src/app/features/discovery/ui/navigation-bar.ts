import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent, type IconName } from '../../../shared/icon/icon'
import type { AppView } from '../domain/startup'
import { commonStrings } from '../../../shared/strings/common.strings'
import { navigationStrings } from '../strings/navigation.strings'

interface NavigationItem {
  id: AppView
  label: string
  icon: IconName
}

@Component({
  selector: 'pulso-navigation-bar',
  standalone: true,
  imports: [CommonModule, BrandMarkComponent, IconComponent],
  templateUrl: './navigation-bar.html',
})
export class NavigationBarComponent {
  @Input() activeView: AppView = 'discover'
  @Input() savedCount = 0
  @Input() interestedCount = 0
  @Output() navigated = new EventEmitter<AppView>()

  @Output() exampleRequested = new EventEmitter<void>()

  readonly common = commonStrings
  readonly strings = navigationStrings

  readonly navigationItems: readonly NavigationItem[] = [
    { id: 'discover', label: navigationStrings.discover, icon: 'compass' },
    { id: 'create-project', label: navigationStrings.createProject, icon: 'sparkles' },
    { id: 'saved', label: navigationStrings.saved, icon: 'bookmark' },
    { id: 'profile', label: navigationStrings.profile, icon: 'user-round' },
  ]

  get totalSaved(): number {
    return this.savedCount + this.interestedCount
  }

  countFor(item: NavigationItem): number {
    return item.id === 'saved' ? this.savedCount : 0
  }
}
