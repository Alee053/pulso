import { DatePipe } from '@angular/common'
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { commonStrings } from '../../../shared/strings/common.strings'
import { UnreadNotifications } from '../application/unread-notifications'
import { NotificationsApi } from '../data-access/notifications-api'
import type { InboxNotification } from '../domain/communication'
import { conversationHref, requestInboxHref } from '../domain/connection'
import { notificationsStrings } from '../strings/notifications.strings'

@Component({
  selector: 'pulso-notifications-view',
  imports: [DatePipe],
  templateUrl: './notifications-view.html',
})
export class NotificationsViewComponent implements OnInit {
  protected readonly strings = notificationsStrings
  protected readonly common = commonStrings
  private readonly unread = inject(UnreadNotifications)
  private readonly api = inject(NotificationsApi)
  private readonly destroyRef = inject(DestroyRef)

  protected readonly unreadCount = this.unread.unreadCount
  protected readonly entries = signal<InboxNotification[]>([])
  protected readonly loading = signal(false)
  protected readonly loadFailed = signal(false)
  protected readonly hasMore = signal(false)
  protected readonly marking = signal<string | null>(null)
  protected readonly markFailed = signal<string | null>(null)

  ngOnInit(): void {
    this.refresh()
  }

  protected refresh(): void {
    if (this.loading() || this.marking()) return
    this.entries.set([])
    this.hasMore.set(false)
    this.loadMore()
  }

  protected loadMore(): void {
    if (this.loading() || this.marking()) return

    this.loading.set(true)
    this.loadFailed.set(false)
    this.api.list(this.entries().length).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ data, meta }) => {
        this.entries.update((entries) => [...entries, ...data])
        this.hasMore.set(meta.hasMore)
        this.unread.update(meta.unreadCount)
        this.loading.set(false)
      },
      error: () => {
        this.loadFailed.set(true)
        this.loading.set(false)
      },
    })
  }

  protected markRead(item: InboxNotification, row: HTMLElement): void {
    if (this.marking() || this.loading() || item.read) return

    this.marking.set(item.id)
    this.markFailed.set(null)
    this.api.markRead(item.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        row.focus()
        this.entries.update((entries) => entries.map(entry => entry.id === updated.id ? updated : entry))
        this.marking.set(null)
        this.unread.refresh()
      },
      error: () => {
        this.marking.set(null)
        this.markFailed.set(item.id)
      },
    })
  }

  protected label(item: InboxNotification): string {
    switch (item.type) {
      case 'NEW_MESSAGE': return this.strings.newMessage
      case 'CONNECTION_REQUEST': return this.strings.newRequest
      case 'CONNECTION_RESPONSE':
        if (item.payload['response'] === 'ACCEPTED') return this.strings.accepted
        if (item.payload['response'] === 'DECLINED') return this.strings.declined
        return this.strings.response
      default: return this.strings.general
    }
  }

  protected link(item: InboxNotification): { href: string; label: string } | null {
    if (item.conversationId) return { href: conversationHref(item.conversationId), label: this.strings.conversation }
    if (item.type === 'CONNECTION_REQUEST' && item.connectionIntentId) {
      return { href: requestInboxHref('received'), label: this.strings.received }
    }
    if (item.type === 'CONNECTION_RESPONSE' && item.connectionIntentId) {
      return { href: requestInboxHref('sent'), label: this.strings.sent }
    }
    return null
  }
}
