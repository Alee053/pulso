import { CommonModule } from '@angular/common'
import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  EventEmitter,
  ViewChild,
} from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import type { Startup } from '../domain/startup'
import { detailStrings } from '../strings/detail.strings'

@Component({
  selector: 'pulso-startup-detail-modal',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './startup-detail-modal.html',
})
export class StartupDetailModalComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly strings = detailStrings
  @Input() startup!: Startup
  @Input() recommendation = ''
  @Input() manageFocus = true
  @Output() closed = new EventEmitter<void>()
  @ViewChild('modal') modalRef?: ElementRef<HTMLElement>
  @ViewChild('closeButton') closeButtonRef?: ElementRef<HTMLButtonElement>

  hasImageError = false
  contactStarted = false
  private previousActiveElement: HTMLElement | null = null
  private originalOverflow = ''

  ngOnInit(): void {
    if (!this.manageFocus) return
    this.originalOverflow = document.body.style.overflow
    this.previousActiveElement =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    document.body.style.overflow = 'hidden'
  }

  ngAfterViewInit(): void {
    if (!this.manageFocus) return
    window.setTimeout(() => this.closeButtonRef?.nativeElement.focus(), 0)
  }

  @HostListener('document:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if (!this.manageFocus) return
    if (event.key === 'Escape') {
      event.preventDefault()
      this.closed.emit()
      return
    }

    if (event.key !== 'Tab') {
      return
    }

    const focusableElements = this.modalRef?.nativeElement.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
    )
    const firstElement = focusableElements?.[0]
    const lastElement = focusableElements?.[focusableElements.length - 1]

    if (!firstElement || !lastElement) {
      event.preventDefault()
      return
    }

    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }

  handleBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit()
    }
  }

  ngOnDestroy(): void {
    if (!this.manageFocus) return
    document.body.style.overflow = this.originalOverflow
    if (this.previousActiveElement?.isConnected) {
      this.previousActiveElement.focus()
    }
  }
}
