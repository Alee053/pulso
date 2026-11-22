import { DatePipe } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, computed, DestroyRef, Input, OnChanges, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import type { Subscription } from 'rxjs'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ConversationMessagesApi } from '../data-access/conversation-messages-api'
import type { ConversationMessage } from '../domain/communication'
import { conversationStrings } from '../strings/conversation.strings'
import { reportStrings } from '../strings/report.strings'
import { ProfileBlockActionComponent } from './profile-block-action'
import { ReportFormComponent } from './report-form'

type SendError = 'restricted' | 'inactive' | 'invalid' | 'sendFailed'

@Component({
  selector: 'pulso-conversation-view',
  imports: [DatePipe, ProfileBlockActionComponent, ReportFormComponent],
  templateUrl: './conversation-view.html',
})
export class ConversationViewComponent implements OnChanges {
  @Input({ required: true }) conversationId!: string
  @Input({ required: true }) profileId!: string

  protected readonly strings = conversationStrings
  protected readonly reportStrings = reportStrings
  protected readonly common = commonStrings
  protected readonly messages = signal<ConversationMessage[]>([])
  protected readonly otherProfileId = computed(() =>
    this.messages().find(message => message.senderProfileId !== this.profileId)?.senderProfileId ?? null,
  )
  protected readonly loading = signal(false)
  protected readonly loaded = signal(false)
  protected readonly loadError = signal<'loadFailed' | 'unavailable' | null>(null)
  protected readonly hasMore = signal(false)
  protected readonly draft = signal('')
  protected readonly sending = signal(false)
  protected readonly sent = signal(false)
  protected readonly sendError = signal<SendError | null>(null)
  private readonly api = inject(ConversationMessagesApi)
  private readonly destroyRef = inject(DestroyRef)
  private pageRequest?: Subscription
  private sendRequest?: Subscription

  ngOnChanges(): void {
    this.pageRequest?.unsubscribe()
    this.sendRequest?.unsubscribe()
    this.messages.set([])
    this.draft.set('')
    this.hasMore.set(false)
    this.loaded.set(false)
    this.sending.set(false)
    this.sent.set(false)
    this.sendError.set(null)
    this.loadPage(0)
  }

  protected loadMore(): void {
    if (!this.loading()) this.loadPage(this.messages().length)
  }

  protected send(event: Event, field: HTMLTextAreaElement): void {
    event.preventDefault()
    const body = this.draft().trim()
    if (!body || !this.loaded() || this.loading() || this.sending() || this.sendUnavailable()) return

    field.focus({ preventScroll: true })
    this.sending.set(true)
    this.sent.set(false)
    this.sendError.set(null)
    this.sendRequest = this.api.send(this.conversationId, body)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sending.set(false)
          this.draft.set('')
          this.sent.set(true)
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

  protected sendUnavailable(): boolean {
    return this.sendError() === 'restricted' || this.sendError() === 'inactive'
  }

  private loadPage(offset: number): void {
    this.loading.set(true)
    this.loadError.set(null)
    this.pageRequest = this.api.list(this.conversationId, offset)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ data, meta }) => {
          this.messages.update(messages => [...messages, ...data])
          this.hasMore.set(meta.hasMore)
          this.loaded.set(true)
          this.loading.set(false)
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false)
          if (error.status === 404) this.onUnavailable()
          else this.loadError.set('loadFailed')
        },
      })
  }

  private onUnavailable(): void {
    this.messages.set([])
    this.hasMore.set(false)
    this.loaded.set(false)
    this.loadError.set('unavailable')
  }
}
