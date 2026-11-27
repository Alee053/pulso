import { HttpErrorResponse } from '@angular/common/http'
import { Component, DestroyRef, Input, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { IconComponent } from '../../../shared/icon/icon'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import { connectionReasons, requestInboxHref, type ConnectionReason } from '../domain/connection'
import { requestsStrings } from '../strings/requests.strings'

type RequestError = 'duplicate' | 'unavailable' | 'restricted' | 'createFailed'

@Component({
  selector: 'pulso-connection-request-form',
  imports: [IconComponent],
  template: `
    <details>
      <summary #toggle class="contact-button max-w-full cursor-pointer text-start">
        <pulso-icon name="handshake" [size]="17" />{{ opportunityId ? strings.requestOpportunity : strings.requestProject }}
      </summary>
      @if (pending()) {
        <p class="status-message status-message--success mt-3" role="status">{{ strings.pending }}</p>
        <a [href]="sentHref" class="contact-button mt-2">{{ strings.openSent }}</a>
      } @else {
        <form class="mt-3 grid gap-3 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4" (submit)="submit($event, reason.value, message.value, toggle)">
          <label>
            <span class="field-label">{{ strings.reason }}</span>
            <select #reason class="field-input" required [disabled]="sending()">
              <option value="">{{ strings.chooseReason }}</option>
              @for (option of reasons; track option) {
                <option [value]="option">{{ strings.reasons[option] }}</option>
              }
            </select>
          </label>
          <label>
            <span class="field-label">{{ strings.optionalMessage }}</span>
            <textarea #message class="field-input" rows="3" [disabled]="sending()"></textarea>
          </label>
          @if (failure(); as error) {
            <p class="status-message status-message--error" role="alert">{{ strings[error] }}</p>
          }
          <button type="submit" class="solid-button w-fit" [disabled]="sending()">
            {{ sending() ? strings.submitting : strings.submit }}
          </button>
        </form>
      }
    </details>
  `,
})
export class ConnectionRequestFormComponent {
  @Input({ required: true }) projectId!: string
  @Input() opportunityId: string | null = null

  protected readonly strings = requestsStrings
  protected readonly reasons = connectionReasons
  protected readonly sentHref = requestInboxHref('sent')
  protected readonly pending = signal(false)
  protected readonly sending = signal(false)
  protected readonly failure = signal<RequestError | null>(null)

  private readonly api = inject(ConnectionIntentsApi)
  private readonly destroyRef = inject(DestroyRef)

  protected submit(event: Event, reason: string, message: string, toggle: HTMLElement): void {
    event.preventDefault()
    if (this.sending() || !this.reasons.includes(reason as ConnectionReason)) return

    toggle.focus({ preventScroll: true })
    this.sending.set(true)
    this.failure.set(null)
    this.api.create(this.projectId, reason as ConnectionReason, message.trim() || null, this.opportunityId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (request) => {
          this.sending.set(false)
          this.pending.set(request.status === 'PENDING')
        },
        error: (error: HttpErrorResponse) => {
          this.sending.set(false)
          switch (error.error?.code) {
            case 'connection_intent_duplicate': this.failure.set('duplicate'); break
            case 'connection_context_unavailable': this.failure.set('unavailable'); break
            case 'connection_intent_forbidden': this.failure.set('restricted'); break
            default: this.failure.set('createFailed')
          }
        },
      })
  }
}
