import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import type { Startup } from '../domain/startup'
import { savedStrings } from '../strings/saved.strings'
import { StartupThumbComponent } from './startup-thumb'

@Component({
  selector: 'pulso-saved-view',
  standalone: true,
  imports: [CommonModule, IconComponent, StartupThumbComponent],
  templateUrl: './saved-view.html',
})
export class SavedViewComponent {
  readonly strings = savedStrings
  readonly common = commonStrings
  @Input() savedStartups: Startup[] = []
  @Input() interestedStartups: Startup[] = []
  @Input() isSavingInteraction = false
  @Input() savedCount = 0
  @Input() interestedCount = 0
  @Input() savedHasMore = false
  @Input() interestedHasMore = false
  @Input() hasLoadedSavedProjects = false
  @Input() hasLoadedInterestedProjects = false
  @Input() isLoadingSavedProjects = false
  @Input() isLoadingInterestedProjects = false
  @Input() savedProjectsErrorMessage: string | null = null
  @Input() interestedProjectsErrorMessage: string | null = null
  @Output() startupSelected = new EventEmitter<Startup>()
  @Output() interestCleared = new EventEmitter<string>()
  @Output() retrySavedRequested = new EventEmitter<void>()
  @Output() retryInterestedRequested = new EventEmitter<void>()
  @Output() loadMoreSavedRequested = new EventEmitter<void>()
  @Output() loadMoreInterestedRequested = new EventEmitter<void>()
}
