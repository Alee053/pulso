import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import type { Decision, Startup } from '../domain/startup'

@Component({
  selector: 'pulso-startup-card',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './startup-card.html',
})
export class StartupCardComponent {
  @Input() startup!: Startup
  @Input() direction = 1
  @Input() disabled = false
  @Input() isSaved = false
  @Output() saved = new EventEmitter<void>()
  @Output() dragChanged = new EventEmitter<{ x: number; y: number }>()
  @Output() selected = new EventEmitter<Startup>()
  @Output() dragDecision = new EventEmitter<Decision>()

  hasImageError = false
  dragX = 0
  dragY = 0
  isDragging = false
  private pointerId: number | null = null
  private startX = 0
  private startY = 0
  private suppressClick = false

  get cardTransform(): string {
    const rotation = Math.max(-6, Math.min(6, this.dragX / 45))
    const scale = this.isDragging ? 1.02 : 1
    return `translate(${this.dragX}px, ${this.dragY}px) rotate(${rotation}deg) scale(${scale})`
  }

  onPointerDown(event: PointerEvent): void {
    if (this.disabled || event.button !== 0) {
      return
    }

    this.pointerId = event.pointerId
    this.startX = event.clientX
    this.startY = event.clientY
    this.dragX = 0
    this.dragY = 0
    this.isDragging = false
    ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  }

  onPointerMove(event: PointerEvent): void {
    if (this.pointerId !== event.pointerId || this.disabled) {
      return
    }

    const nextX = Math.max(-135, Math.min(135, event.clientX - this.startX))
    const nextY = Math.max(-135, Math.min(135, event.clientY - this.startY))
    this.dragX = nextX
    this.dragY = nextY
    this.isDragging = Math.abs(nextX) > 6 || Math.abs(nextY) > 6

    if (this.isDragging) {
      this.suppressClick = true
    }

    this.dragChanged.emit({ x: nextX, y: nextY })
  }

  onPointerUp(event: PointerEvent): void {
    if (this.pointerId !== event.pointerId) {
      return
    }

    const x = this.dragX
    const y = this.dragY
    const isVertical = Math.abs(y) > Math.abs(x)

    this.pointerId = null
    this.isDragging = false
    this.dragX = 0
    this.dragY = 0
    this.dragChanged.emit({ x: 0, y: 0 })

    if (isVertical && y < -92) {
      this.selected.emit(this.startup)
    } else if (isVertical && y > 92) {
      this.saved.emit()
    } else if (x > 92) {
      this.dragDecision.emit('interested')
    } else if (x < -92) {
      this.dragDecision.emit('skip')
    }

    if (this.suppressClick) {
      window.setTimeout(() => {
        this.suppressClick = false
      }, 0)
    }

    const target = event.currentTarget as HTMLElement
    if (target.hasPointerCapture(event.pointerId)) {
      target.releasePointerCapture(event.pointerId)
    }
  }

  onPointerCancel(event: PointerEvent): void {
    if (this.pointerId !== event.pointerId) {
      return
    }

    this.pointerId = null
    this.isDragging = false
    this.dragX = 0
    this.dragY = 0
    this.suppressClick = false
    this.dragChanged.emit({ x: 0, y: 0 })
  }

  onCardClick(event: MouseEvent): void {
    if (this.suppressClick) {
      this.suppressClick = false
      return
    }

    if (!this.disabled) {
      ;(event.currentTarget as HTMLElement).focus()
      this.selected.emit(this.startup)
    }
  }

  saveFromButton(event: MouseEvent): void {
    event.stopPropagation()
    if (!this.disabled) {
      this.saved.emit()
    }
  }

  selectFromButton(event: MouseEvent): void {
    event.stopPropagation()
    if (!this.disabled) {
      this.selected.emit(this.startup)
    }
  }
}
