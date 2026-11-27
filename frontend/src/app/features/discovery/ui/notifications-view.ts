import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { currentLanguage } from '../../../core/i18n/language'
import { IconComponent, type IconName } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import { UnreadNotifications } from '../application/unread-notifications'
import { NotificationsApi, type NotificationFilter } from '../data-access/notifications-api'
import type { InboxNotification } from '../domain/communication'
import { conversationHref, requestInboxHref } from '../domain/connection'
import { notificationsStrings } from '../strings/notifications.strings'
import { ActivityDatePipe } from './activity-date'

@Component({
  selector: 'pulso-notifications-view',
  imports: [ActivityDatePipe, IconComponent],
  templateUrl: './notifications-view.html',
  styleUrl: './activity-list.css',
})
export class NotificationsViewComponent implements OnInit {
  protected readonly strings = notificationsStrings
  protected readonly common = commonStrings
  protected readonly language = currentLanguage
  protected readonly filter = signal<NotificationFilter>('ALL')
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

  protected switchFilter(filter: NotificationFilter): void {
    if (this.filter() === filter || this.loading() || this.marking()) return
    this.filter.set(filter)
    this.refresh()
  }

  protected refresh(): void {
    if (this.loading() || this.marking()) return
    this.entries.set([])
    this.markFailed.set(null)
    this.hasMore.set(false)
    this.loadMore()
  }

  protected loadMore(): void {
    if (this.loading() || this.marking()) return

    this.loading.set(true)
    this.loadFailed.set(false)
    this.api.list(this.entries().length, undefined, this.filter()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

  protected markRead(item: InboxNotification, row: HTMLElement, heading: HTMLElement): void {
    if (this.marking() || this.loading() || item.read) return

    this.marking.set(item.id)
    this.markFailed.set(null)
    this.api.markRead(item.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.marking.set(null)
        if (this.filter() === 'UNREAD') {
          heading.focus({ preventScroll: true })
          this.entries.update(entries => entries.filter(entry => entry.id !== updated.id))
        } else {
          row.focus({ preventScroll: true })
          this.entries.update(entries => entries.map(entry => entry.id === updated.id ? updated : entry))
        }
        this.unread.refresh()
        if (!this.entries().length && this.hasMore()) this.loadMore()
      },
      error: () => {
        this.marking.set(null)
        this.markFailed.set(item.id)
      },
    })
  }

  protected icon(item: InboxNotification): IconName {
    if (item.type === 'NEW_MESSAGE') return 'message-circle'
    if (item.type === 'CONNECTION_REQUEST' || item.type === 'CONNECTION_RESPONSE') return 'handshake'
    return 'bell'
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
