import { DatePipe } from '@angular/common'
import { Component, DestroyRef, Input, OnChanges, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { map, type Observable, type Subscription } from 'rxjs'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import { conversationHref, type ConnectionInboxEntry, type RequestInbox } from '../domain/connection'
import { profileBlockStrings } from '../strings/profile-block.strings'
import { requestsStrings } from '../strings/requests.strings'
import { ProfileBlockActionComponent } from './profile-block-action'

type RequestAction = 'accept' | 'decline' | 'withdraw'

@Component({
  selector: 'pulso-requests-view',
  imports: [DatePipe, ProfileBlockActionComponent],
  templateUrl: './requests-view.html',
})
export class RequestsViewComponent implements OnChanges {
  @Input() initialInbox: RequestInbox = 'received'

  protected readonly strings = requestsStrings
  protected readonly blockStrings = profileBlockStrings
  protected readonly common = commonStrings
  protected readonly conversationHref = conversationHref
  protected readonly inbox = signal<RequestInbox>('received')
  protected readonly entries = signal<ConnectionInboxEntry[]>([])
  protected readonly loading = signal(false)
  protected readonly loadFailed = signal(false)
  protected readonly hasMore = signal(false)
  protected readonly blocked = signal(false)
  protected readonly responding = signal<string | null>(null)
  protected readonly responseFailed = signal(false)

  private readonly api = inject(ConnectionIntentsApi)
  private readonly destroyRef = inject(DestroyRef)
  private pageRequest?: Subscription

  ngOnChanges(): void {
    this.switchInbox(this.initialInbox)
  }

  protected switchInbox(inbox: RequestInbox): void {
    if (this.inbox() !== inbox) this.blocked.set(false)
    this.inbox.set(inbox)
    this.loadRequests()
  }

  protected loadRequests(): void {
    this.pageRequest?.unsubscribe()
    this.entries.set([])
    this.hasMore.set(false)
    this.responseFailed.set(false)
    this.loadPage(0)
  }

  protected loadMore(): void {
    if (!this.loading() && !this.responding()) this.loadPage(this.entries().length)
  }

  protected onSenderBlocked(heading: HTMLElement): void {
    heading.focus()
    this.blocked.set(true)
    this.loadRequests()
  }

  protected respond(entry: ConnectionInboxEntry, action: RequestAction, heading: HTMLElement): void {
    if (this.loading() || this.responding() || entry.request.status !== 'PENDING') return

    const id = entry.request.id
    this.responding.set(id)
    this.responseFailed.set(false)
    this.responseFor(entry, action).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        heading.focus()
        this.entries.update((entries) => entries.map((item) => item.request.id === id ? updated : item))
        this.responding.set(null)
      },
      error: () => {
        this.responding.set(null)
        this.responseFailed.set(true)
      },
    })
  }

  private responseFor(entry: ConnectionInboxEntry, action: RequestAction): Observable<ConnectionInboxEntry> {
    const id = entry.request.id
    if (action === 'accept') {
      return this.api.acceptIntent(id).pipe(map(({ conversationId }) => ({
        ...entry,
        request: { ...entry.request, status: 'ACCEPTED' },
        conversationId,
      })))
    }

    const response = action === 'decline' ? this.api.declineIntent(id) : this.api.withdrawIntent(id)
    return response.pipe(map((request) => ({ ...entry, request })))
  }

  private loadPage(offset: number): void {
    this.loading.set(true)
    this.loadFailed.set(false)
    this.pageRequest = this.api.list(this.inbox(), offset)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ entries, hasMore }) => {
          this.entries.update((current) => [...current, ...entries])
          this.hasMore.set(hasMore)
          this.loading.set(false)
        },
        error: () => {
          this.loading.set(false)
          this.loadFailed.set(true)
        },
      })
  }
}
