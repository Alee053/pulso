import { DOCUMENT } from '@angular/common'
import { Component, EventEmitter, Input, Output, inject } from '@angular/core'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent, type IconName } from '../../../shared/icon/icon'
import { LanguageSwitcherComponent } from '../../../shared/language-switcher/language-switcher'
import { ThemeSwitcherComponent } from '../../../shared/theme-switcher/theme-switcher'
import type { AppView } from '../domain/startup'
import { navigationStrings } from '../strings/navigation.strings'

interface Destination {
  id: AppView
  label: string
  icon: IconName
}

@Component({
  selector: 'pulso-navigation-bar',
  imports: [BrandMarkComponent, IconComponent, ThemeSwitcherComponent, LanguageSwitcherComponent],
  templateUrl: './navigation-bar.html',
})
export class NavigationBarComponent {
  @Input() activeView: AppView = 'discover'
  @Input() projectCreationEnabled = true
  @Input() languageSwitcherEnabled = false
  @Input() savedCount = 0
  @Input() unreadCount = 0
  @Output() navigated = new EventEmitter<AppView>()

  readonly strings = navigationStrings
  private readonly document = inject(DOCUMENT)

  get skipHref(): string {
    const { pathname, search } = this.document.location
    return pathname + search + '#workspace-content'
  }

  get explore(): Destination[] {
    return [
      { id: 'discover', label: this.strings.discover, icon: 'compass' },
      { id: 'saved', label: this.strings.saved, icon: 'bookmark' },
    ]
  }

  get community(): Destination[] {
    return [
      { id: 'messages', label: this.strings.messages, icon: 'message-circle' },
      { id: 'requests', label: this.strings.requests, icon: 'handshake' },
      { id: 'teams', label: this.strings.teams, icon: 'users-round' },
    ]
  }

  get mobilePrimary(): Destination[] {
    return [...this.explore, this.community[0]]
  }

  get mobileMore(): Destination[] {
    const items = this.community.slice(1)
    if (this.projectCreationEnabled) items.push({ id: 'create-project', label: this.strings.createProject, icon: 'plus' })
    items.push({ id: 'profile', label: this.strings.profile, icon: 'circle-user' })
    return items
  }

  get currentLabel(): string {
    if (this.activeView === 'notifications') return this.strings.notifications
    if (this.activeView === 'detail') return this.strings.project
    return [...this.mobilePrimary, ...this.mobileMore].find(item => this.isCurrent(item.id))?.label ?? ''
  }

  get inMore(): boolean {
    return this.mobileMore.some(item => this.isCurrent(item.id))
  }

  isCurrent(id: AppView): boolean {
    return this.activeView === id || (id === 'messages' && this.activeView === 'conversation')
  }

  chooseMore(item: Destination, popover: HTMLElement): void {
    popover.hidePopover()
    this.navigated.emit(item.id)
  }
}
