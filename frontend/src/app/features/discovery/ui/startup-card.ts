import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { ShareButtonComponent } from './share-button'
import type { Decision, Startup } from '../domain/startup'
import { cardStrings } from '../strings/feed.strings'


const GESTURE_MAX_WIDTH = 1023
const DRAG_LIMIT = 135
const AXIS_LOCK_THRESHOLD = 10
const DECISION_THRESHOLD = 92

type DragAxis = 'x' | 'y'

function clampOffset(value: number): number {
    return Math.max(-DRAG_LIMIT, Math.min(DRAG_LIMIT, value))
}

@Component({
    selector: 'pulso-startup-card',
    standalone: true,
    imports: [CommonModule, IconComponent, ShareButtonComponent],
    templateUrl: './startup-card.html',
})
export class StartupCardComponent {
    readonly strings = cardStrings

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
    private lockedAxis: DragAxis | null = null
    private suppressClick = false

    get cardTransform(): string {
        const rotation = Math.max(-6, Math.min(6, this.dragX / 45))
        const scale = this.isDragging ? 1.02 : 1
        return `translate(${this.dragX}px, ${this.dragY}px) rotate(${rotation}deg) scale(${scale})`
    }

    private get supportsGestures(): boolean {
        return typeof window !== 'undefined' && window.innerWidth <= GESTURE_MAX_WIDTH
    }

    onPointerDown(event: PointerEvent): void {
        if (this.disabled || event.button !== 0 || !this.supportsGestures) {
            return
        }

        this.pointerId = event.pointerId
        this.startX = event.clientX
        this.startY = event.clientY
        this.lockedAxis = null
        this.dragX = 0
        this.dragY = 0
        this.isDragging = false
            ; (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
    }

    onPointerMove(event: PointerEvent): void {
        if (this.pointerId !== event.pointerId || this.disabled) {
            return
        }

        const deltaX = event.clientX - this.startX
        const deltaY = event.clientY - this.startY

        if (this.lockedAxis === null) {
            if (Math.abs(deltaX) < AXIS_LOCK_THRESHOLD && Math.abs(deltaY) < AXIS_LOCK_THRESHOLD) {
                return
            }

            this.lockedAxis = Math.abs(deltaX) >= Math.abs(deltaY) ? 'x' : 'y'
            this.isDragging = true
            this.suppressClick = true
        }

        this.dragX = this.lockedAxis === 'x' ? clampOffset(deltaX) : 0
        this.dragY = this.lockedAxis === 'y' ? clampOffset(deltaY) : 0
        this.dragChanged.emit({ x: this.dragX, y: this.dragY })
    }

    onPointerUp(event: PointerEvent): void {
        if (this.pointerId !== event.pointerId) {
            return
        }

        const axis = this.lockedAxis
        const offset = axis === 'y' ? this.dragY : this.dragX
        const target = event.currentTarget as HTMLElement

        this.resetDrag()
        this.dragChanged.emit({ x: 0, y: 0 })

        if (axis === 'y' && offset <= -DECISION_THRESHOLD) {
            this.selected.emit(this.startup)
        } else if (axis === 'y' && offset >= DECISION_THRESHOLD) {
            this.saved.emit()
        } else if (axis === 'x' && offset >= DECISION_THRESHOLD) {
            this.dragDecision.emit('interested')
        } else if (axis === 'x' && offset <= -DECISION_THRESHOLD) {
            this.dragDecision.emit('skip')
        }

        if (this.suppressClick) {
            window.setTimeout(() => {
                this.suppressClick = false
            }, 0)
        }

        if (target.hasPointerCapture(event.pointerId)) {
            target.releasePointerCapture(event.pointerId)
        }
    }

    onPointerCancel(event: PointerEvent): void {
        if (this.pointerId !== event.pointerId) {
            return
        }

        this.resetDrag()
        this.suppressClick = false
        this.dragChanged.emit({ x: 0, y: 0 })
    }

    private resetDrag(): void {
        this.pointerId = null
        this.lockedAxis = null
        this.isDragging = false
        this.dragX = 0
        this.dragY = 0
    }

    onCardClick(event: MouseEvent): void {
        if (this.suppressClick) {
            this.suppressClick = false
            return
        }

        if (!this.disabled) {
            ; (event.currentTarget as HTMLElement).focus()
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