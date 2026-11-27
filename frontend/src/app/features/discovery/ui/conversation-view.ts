import { HttpErrorResponse } from '@angular/common/http'
import {
  afterNextRender, Component, computed, DestroyRef, ElementRef, inject, Injector, input, output,
  signal, viewChild, type OnChanges, type SimpleChanges,
} from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import type { Subscription } from 'rxjs'
import { currentLanguage } from '../../../core/i18n/language'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ConversationMessagesApi } from '../data-access/conversation-messages-api'
import type { ConversationMessage } from '../domain/communication'
import { conversationStrings } from '../strings/conversation.strings'
import { reportStrings } from '../strings/report.strings'
import { ActivityDatePipe } from './activity-date'
import { ProfileBlockActionComponent } from './profile-block-action'
import { ReportFormComponent } from './report-form'

type SendError = 'restricted' | 'inactive' | 'invalid' | 'sendFailed'

@Component({
  selector: 'pulso-conversation-view',
  imports: [ActivityDatePipe, IconComponent, ProfileBlockActionComponent, ReportFormComponent],
  templateUrl: './conversation-view.html',
  styleUrl: './conversation-view.css',
})
export class ConversationViewComponent implements OnChanges {
  readonly conversationId = input.required<string>()
  readonly profileId = input.required<string>()
  readonly projectName = input<string | null>(null)
  readonly context = input<string | null>(null)
  readonly closed = output<void>()

  protected readonly strings = conversationStrings
  protected readonly reportStrings = reportStrings
  protected readonly common = commonStrings
  protected readonly language = currentLanguage

  private readonly history = signal<ConversationMessage[]>([])
  // A confirmed send can be newer than the page being read. Keep the API offset
  // tied to contiguous history until pagination reaches the delivered message.
  private readonly delivered = signal<ConversationMessage[]>([])
  protected readonly messages = computed(() => [...this.history(), ...this.delivered()])
  protected readonly historyLength = computed(() => this.history().length)
  protected readonly otherProfileId = computed(() =>
    this.messages().find(message => message.senderProfileId !== this.profileId())?.senderProfileId ?? null,
  )
  protected readonly loading = signal(false)
  protected readonly loaded = signal(false)
  protected readonly loadError = signal<'loadFailed' | 'unavailable' | null>(null)
  protected readonly hasMore = signal(false)
  protected readonly draft = signal('')
  protected readonly sending = signal(false)
  protected readonly sent = signal(false)
  protected readonly sendError = signal<SendError | null>(null)
  protected readonly sendUnavailable = computed(() => this.sendError() === 'restricted' || this.sendError() === 'inactive')

  private readonly api = inject(ConversationMessagesApi)
  private readonly destroyRef = inject(DestroyRef)
  private readonly injector = inject(Injector)
  private readonly timeline = viewChild<ElementRef<HTMLElement>>('timeline')
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading')
  private pageRequest?: Subscription
  private sendRequest?: Subscription

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['conversationId'] && !changes['profileId']) return
    this.pageRequest?.unsubscribe()
    this.sendRequest?.unsubscribe()
    this.history.set([])
    this.delivered.set([])
    this.draft.set('')
    this.hasMore.set(false)
    this.loaded.set(false)
    this.sending.set(false)
    this.sent.set(false)
    this.sendError.set(null)
    this.loadPage(0, true)
  }

  focusHeading(): void {
    this.heading()?.nativeElement.focus({ preventScroll: true })
  }

  protected isNewDay(index: number): boolean {
    if (index === 0) return true
    const messages = this.messages()
    return new Date(messages[index].createdAt).toDateString() !== new Date(messages[index - 1].createdAt).toDateString()
  }

  protected loadMore(): void {
    if (!this.loading() && !this.sending()) this.loadPage(this.history().length, false)
  }

  protected send(event: Event, field: HTMLTextAreaElement): void {
    event.preventDefault()
    const body = this.draft().trim()
    if (!body || !this.loaded() || this.loading() || this.sending() || this.sendUnavailable()) return

    field.focus({ preventScroll: true })
    this.sending.set(true)
    this.sent.set(false)
    this.sendError.set(null)
    this.sendRequest = this.api.send(this.conversationId(), body)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: message => {
          this.delivered.update(messages => [...messages, message])
          this.sending.set(false)
          this.draft.set('')
          this.sent.set(true)
          this.scrollToLatest()
          if (!this.hasMore()) this.loadMore()
        },
        error: (error: HttpErrorResponse) => {
          this.sending.set(false)
          switch (error.error?.code) {
            case 'messaging_blocked': this.sendError.set('restricted'); return
            case 'conversation_not_active': this.sendError.set('inactive'); return
            case 'conversation_not_found': this.onUnavailable(); return
          }
          this.sendError.set(error.status === 400 ? 'invalid' : 'sendFailed')
        },
      })
  }

  private loadPage(offset: number, opening: boolean): void {
    this.loading.set(true)
    this.loadError.set(null)
    this.pageRequest = this.api.list(this.conversationId(), offset)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data, meta }) => {
          const ids = new Set(data.map(message => message.id))
          this.history.update(messages => [...messages, ...data])
          this.delivered.update(messages => messages.filter(message => !ids.has(message.id)))
          this.hasMore.set(meta.hasMore)
          this.loaded.set(true)
          this.loading.set(false)
          if (opening && !meta.hasMore) this.scrollToLatest()
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false)
          if (error.status === 404) this.onUnavailable()
          else this.loadError.set('loadFailed')
        },
      })
  }

  private scrollToLatest(): void {
    afterNextRender(() => {
      const container = this.timeline()?.nativeElement
      if (container) container.scrollTop = container.scrollHeight
    }, { injector: this.injector })
  }

  private onUnavailable(): void {
    this.history.set([])
    this.delivered.set([])
    this.draft.set('')
    this.sent.set(false)
    this.hasMore.set(false)
    this.loaded.set(false)
    this.loadError.set('unavailable')
  }
}
