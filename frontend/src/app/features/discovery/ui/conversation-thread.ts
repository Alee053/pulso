import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, inject, Input, OnChanges, signal, SimpleChanges } from '@angular/core'
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ConversationMessagesApi } from '../data-access/conversation-messages-api'
import type { ConversationMessage } from '../domain/conversation'
import { conversationStrings as strings } from '../strings/conversation.strings'
import { reportStrings } from '../strings/report.strings'
import { ReportFormComponent } from './report-form'
import { ProfileBlockControlComponent } from './profile-block-control'

@Component({
  selector: 'pulso-conversation-thread',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ReportFormComponent, ProfileBlockControlComponent],
  templateUrl: './conversation-thread.html',
})
export class ConversationThreadComponent implements OnChanges {
  @Input({ required: true }) conversationId = ''
  @Input({ required: true }) otherProfileId = ''
  @Input({ required: true }) otherDisplayName = ''

  readonly strings = strings
  readonly reportStrings = reportStrings
  readonly messages = signal<ConversationMessage[]>([])
  readonly loading = signal(false)
  readonly sending = signal(false)
  readonly hasMore = signal(false)
  readonly loadError = signal<string | null>(null)
  readonly sendError = signal<string | null>(null)
  readonly sendSuccess = signal(false)
  readonly form = new FormGroup({ body: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2000)] }) })
  private readonly api = inject(ConversationMessagesApi)
  private readonly auth = inject(SupabaseAuthGateway)
  private nextOffset = 0

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['conversationId'] && this.conversationId) this.refresh()
  }

  refresh(): void {
    this.messages.set([])
    this.nextOffset = 0
    this.hasMore.set(false)
    this.loadMore()
  }

  loadMore(): void {
    if (this.loading() || !this.conversationId) return
    this.loading.set(true)
    this.loadError.set(null)
    this.api.list(this.conversationId, this.nextOffset).subscribe({
      next: (page) => {
        this.messages.update((items) => [...items, ...page.data])
        this.nextOffset = page.meta.offset + page.meta.limit
        this.hasMore.set(page.meta.hasMore && this.nextOffset <= 100000)
        this.loading.set(false)
      },
      error: (error: unknown) => {
        this.loadError.set(error instanceof HttpErrorResponse && error.status === 404
          ? strings.unavailable : strings.loadFailed)
        this.loading.set(false)
      },
    })
  }

  async send(): Promise<void> {
    if (this.sending() || this.form.invalid) {
      this.form.markAllAsTouched()
      return
    }
    this.sending.set(true)
    this.sendError.set(null)
    this.sendSuccess.set(false)
    try {
      const message = await firstValueFrom(this.api.send(this.conversationId, this.form.controls.body.value))
      this.messages.update((items) => [...items, message])
      this.nextOffset += 1
      this.form.reset({ body: '' })
      this.sendSuccess.set(true)
    } catch (error) {
      this.sendError.set(error instanceof HttpErrorResponse && error.status === 403
        ? strings.blocked : error instanceof HttpErrorResponse && error.status === 404
          ? strings.unavailable : strings.sendFailed)
    } finally {
      this.sending.set(false)
    }
  }

  senderLabel(message: ConversationMessage): string {
    return message.senderProfileId === this.auth.session()?.id ? strings.you : this.otherDisplayName
  }

  isOwnMessage(message: ConversationMessage): boolean {
    return message.senderProfileId === this.auth.session()?.id
  }
}
