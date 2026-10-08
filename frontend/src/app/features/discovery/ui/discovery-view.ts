import { CommonModule } from '@angular/common'
import { Component, EventEmitter, HostListener, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import type { Decision, Startup } from '../domain/startup'
import { feedStrings } from '../strings/feed.strings'
import { StartupCardComponent } from './startup-card'

@Component({
  selector: 'pulso-discovery-view',
  standalone: true,
  imports: [CommonModule, IconComponent, StartupCardComponent],
  templateUrl: './discovery-view.html',
})
export class DiscoveryViewComponent implements OnChanges, OnDestroy {
  @Input() activeSignals: string[] = []
  @Input() currentStartup: Startup | undefined
  @Input() errorMessage: string | null = null
  @Input() feedRemaining = 0
  @Input() isLoading = false
  @Input() isModalOpen = false
  @Input() totalStartups = 0
  @Input() savedIds: string[] = []
  @Input() isSavingInteraction = false
  @Output() decisionMade = new EventEmitter<Decision>()
  @Output() restarted = new EventEmitter<void>()
  @Output() retryRequested = new EventEmitter<void>()
  @Output() startupSaved = new EventEmitter<string>()
  @Output() startupSelected = new EventEmitter<Startup>()

  readonly strings = feedStrings
  readonly common = commonStrings

  direction = 1
  isTransitioning = false
  toast: string | null = null
  dragOffset = { x: 0, y: 0 }
  private toastTimeout: number | undefined
  private transitionTimeout: number | undefined
  private pendingSaveAction: { startupId: string; shouldSave: boolean } | null = null

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['savedIds'] || !this.pendingSaveAction) {
      return
    }

    const { startupId, shouldSave } = this.pendingSaveAction
    if (this.currentStartup?.id === startupId
      && this.savedIds.includes(startupId) === shouldSave) {
      this.showToast(shouldSave ? this.strings.toastSaved : this.strings.toastRemoved)
      this.pendingSaveAction = null
    }
  }

  get isSaved(): boolean {
    return this.currentStartup
      ? this.savedIds.includes(this.currentStartup.id)
      : false
  }

  get activeNumber(): number {
    return Math.min(this.totalStartups - this.feedRemaining + 1, this.totalStartups)
  }

  get progress(): number {
    return this.totalStartups > 0 ? (this.activeNumber / this.totalStartups) * 100 : 0
  }

  get horizontalIntensity(): number {
    return Math.min(Math.abs(this.dragOffset.x) / 90, 1)
  }

  get verticalIntensity(): number {
    return Math.min(Math.abs(this.dragOffset.y) / 90, 1)
  }

  get isDraggingLeft(): boolean {
    return this.dragOffset.x < -12
  }

  get isDraggingRight(): boolean {
    return this.dragOffset.x > 12
  }

  get isDraggingUp(): boolean {
    return this.dragOffset.y < -12
  }

  get isDraggingDown(): boolean {
    return this.dragOffset.y > 12
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if (this.isModalOpen || event.metaKey || event.ctrlKey || event.altKey || event.repeat) {
      return
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      this.handleDecision('skip')
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      this.handleDecision('interested')
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      this.showCurrentStartup()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      this.handleSave()
    } else if (event.key.toLowerCase() === 's') {
      event.preventDefault()
      this.handleSave()
    }
  }

  showCurrentStartup(): void {
    if (!this.currentStartup || this.isTransitioning || this.isSavingInteraction) {
      return
    }

    this.startupSelected.emit(this.currentStartup)
  }

  handleDecision(decision: Decision): void {
    if (!this.currentStartup || this.isTransitioning || this.isSavingInteraction) {
      return
    }

    this.direction = decision === 'interested' ? 1 : -1
    this.isTransitioning = true
    this.decisionMade.emit(decision)
    this.transitionTimeout = window.setTimeout(() => {
      this.isTransitioning = false
    }, 250)
  }

  handleSave(): void {
    if (!this.currentStartup || this.isTransitioning || this.isSavingInteraction) {
      return
    }

    const wasSaved = this.isSaved
    this.pendingSaveAction = {
      startupId: this.currentStartup.id,
      shouldSave: !wasSaved,
    }
    this.startupSaved.emit(this.currentStartup.id)
  }

  updateDragOffset(offset: { x: number; y: number }): void {
    this.dragOffset = offset
  }

  private showToast(message: string): void {
    this.toast = message
    if (this.toastTimeout !== undefined) {
      window.clearTimeout(this.toastTimeout)
    }
    this.toastTimeout = window.setTimeout(() => {
      this.toast = null
      this.toastTimeout = undefined
    }, 2600)
  }

  ngOnDestroy(): void {
    if (this.toastTimeout !== undefined) {
      window.clearTimeout(this.toastTimeout)
    }
    if (this.transitionTimeout !== undefined) {
      window.clearTimeout(this.transitionTimeout)
    }
  }
}
