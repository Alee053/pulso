import {
  afterNextRender, Component, computed, DestroyRef, ElementRef, inject, Injector, input, output,
  signal, viewChild, viewChildren, type OnChanges, type SimpleChanges,
} from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import type { Subscription } from 'rxjs'
import { currentLanguage } from '../../../core/i18n/language'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import { conversationHref, requestInboxHref, type ConnectionInboxEntry, type RequestInbox } from '../domain/connection'
import { conversationStrings } from '../strings/conversation.strings'
import { requestsStrings } from '../strings/requests.strings'
import { ActivityDatePipe } from './activity-date'
import { ConversationViewComponent } from './conversation-view'

interface ConversationEntry {
  id: string
  connection: ConnectionInboxEntry
}

interface InboxCursor {
  offset: number
  hasMore: boolean
  loading: boolean
  failed: boolean
}

const inboxes: readonly RequestInbox[] = ['received', 'sent']

function initialCursors(): Record<RequestInbox, InboxCursor> {
  const initial = { offset: 0, hasMore: true, loading: false, failed: false }
  return { received: { ...initial }, sent: { ...initial } }
}

@Component({
  selector: 'pulso-messages-view',
  imports: [ActivityDatePipe, ConversationViewComponent, IconComponent],
  templateUrl: './messages-view.html',
  styleUrl: './messages-view.css',
})
export class MessagesViewComponent implements OnChanges {
  readonly profileId = input.required<string>()
  readonly conversationId = input<string | null>(null)
  readonly conversationSelected = output<string | null>()

  protected readonly strings = conversationStrings
  protected readonly requests = requestsStrings
  protected readonly common = commonStrings
  protected readonly language = currentLanguage
  protected readonly conversationHref = conversationHref
  protected readonly requestsHref = requestInboxHref('received')

  private readonly api = inject(ConnectionIntentsApi)
  private readonly destroyRef = inject(DestroyRef)
  private readonly injector = inject(Injector)
  private readonly pageRequests: Partial<Record<RequestInbox, Subscription>> = {}
  private readonly cursors = signal(initialCursors())
  private readonly entries = signal(new Map<string, ConnectionInboxEntry>())
  private readonly links = viewChildren<ElementRef<HTMLAnchorElement>>('threadLink')
  private readonly inboxHeading = viewChild<ElementRef<HTMLElement>>('inboxHeading')
  private readonly thread = viewChild(ConversationViewComponent)

  protected readonly loading = computed(() => inboxes.some(inbox => this.cursors()[inbox].loading))
  protected readonly failed = computed(() => inboxes.some(inbox => this.cursors()[inbox].failed))
  protected readonly canLoadMore = computed(() => inboxes.some(inbox => {
    const cursor = this.cursors()[inbox]
    return cursor.hasMore && !cursor.loading && !cursor.failed
  }))
  protected readonly conversations = computed<ConversationEntry[]>(() =>
    [...this.entries()].map(([id, connection]) => ({ id, connection })).sort((left, right) =>
      Date.parse(right.connection.request.createdAt) - Date.parse(left.connection.request.createdAt)
      || left.id.localeCompare(right.id),
    ),
  )
  protected readonly selected = computed(() => this.entries().get(this.conversationId() ?? ''))

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['profileId']) this.refresh()
    const selection = changes['conversationId']
    if (!selection || selection.firstChange) return
    afterNextRender(() => {
      if (this.conversationId()) {
        this.thread()?.focusHeading()
      } else {
        const previousLink = this.links().find(link => link.nativeElement.dataset['conversation'] === selection.previousValue)
        const destination = previousLink ?? this.inboxHeading()
        destination?.nativeElement.focus({ preventScroll: true })
      }
    }, { injector: this.injector })
  }

  protected follow(event: MouseEvent, id: string): void {
    if (event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
    event.preventDefault()
    this.conversationSelected.emit(id)
  }

  protected refresh(): void {
    for (const inbox of inboxes) this.pageRequests[inbox]?.unsubscribe()
    this.cursors.set(initialCursors())
    this.entries.set(new Map())
    this.loadMore()
  }

  protected loadMore(retry = false): void {
    for (const inbox of inboxes) {
      const cursor = this.cursors()[inbox]
      if (!cursor.hasMore || cursor.loading || cursor.failed !== retry) continue
      this.updateCursor(inbox, { ...cursor, loading: true, failed: false })
      this.pageRequests[inbox] = this.api.list(inbox, cursor.offset)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: ({ entries, hasMore }) => {
            this.entries.update(previous => {
              const merged = new Map(previous)
              for (const entry of entries) {
                if (entry.conversationId) merged.set(entry.conversationId, entry)
              }
              return merged
            })
            this.updateCursor(inbox, { offset: cursor.offset + entries.length, hasMore, loading: false, failed: false })
          },
          error: () => this.updateCursor(inbox, { ...cursor, loading: false, failed: true }),
        })
    }
  }

  private updateCursor(inbox: RequestInbox, cursor: InboxCursor): void {
    this.cursors.update(cursors => ({ ...cursors, [inbox]: cursor }))
  }
}
