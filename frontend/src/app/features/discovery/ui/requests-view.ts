import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import type { ConnectionIntent } from '../domain/startup'
import { LanguageService } from '../../../core/i18n/language.service'
import { requestsStrings } from '../strings/requests.strings'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ConversationThreadComponent } from './conversation-thread'

@Component({
  selector: 'pulso-requests-view',
  standalone: true,
  imports: [CommonModule, IconComponent, ConversationThreadComponent],
  template: `
    <main class="discovery-view">
      <header class="discovery-view__heading">
        <div>
          <span class="eyebrow">{{ strings.eyebrow }}</span>
          <h1 class="page-title">{{ strings.title }}</h1>
          <p class="page-lead">{{ strings.lead }}</p>
        </div>
      </header>

      <div *ngIf="errorMessage() as error" class="status-message status-message--error mb-4" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="contact-button mt-3" (click)="loadRequests()" [disabled]="isLoading()">{{ strings.retry }}</button>
      </div>

      <section *ngIf="activeConversation() as active" class="panel mb-6 p-4 sm:p-6">
        <header class="flex flex-wrap items-start justify-between gap-3">
          <p class="field-label">{{ strings.openConversation }}</p>
          <button type="button" class="contact-button" (click)="activeConversation.set(null)">{{ strings.closeConversation }}</button>
        </header>
        <pulso-conversation-thread [conversationId]="active.conversationId"
                                   [otherProfileId]="active.otherProfileId"
                                   [otherDisplayName]="active.otherDisplayName" />
      </section>

      <div *ngIf="isLoading()" class="state-panel" role="status">
        <span class="state-icon"><span class="spinner" aria-hidden="true"></span></span>
        <h2>{{ strings.loading }}</h2>
      </div>

      <div *ngIf="!isLoading() && !activeConversation() && !errorMessage() && requests().length === 0" class="state-panel" role="status">
        <span class="state-icon"><pulso-icon name="heart" [size]="24" /></span>
        <h2>{{ strings.emptyTitle }}</h2>
        <p>{{ strings.emptyLead }}</p>
      </div>

      <div *ngIf="!isLoading() && requests().length > 0" class="requests-list">
        <div *ngFor="let req of requests()" class="request-card panel p-4 mb-4 rounded-xl border border-white/10 flex justify-between items-center">
          <div>
            <p class="text-sm text-[var(--color-muted)] mb-1">{{ strings.reason }}: {{ req.reason }}</p>
            <p *ngIf="req.message" class="text-white">{{ req.message }}</p>
            <p class="text-xs text-[var(--color-muted)] mt-2">{{ req.createdAt | date:'short' }}</p>
          </div>
          <div class="flex gap-2">
            <button type="button" class="solid-button" (click)="accept(req.id)" [disabled]="busyRequestId() !== null">{{ strings.accept }}</button>
            <button type="button" class="contact-button" (click)="decline(req.id)" [disabled]="busyRequestId() !== null">{{ strings.decline }}</button>
          </div>
        </div>
      </div>
    </main>
  `,
})
export class RequestsViewComponent implements OnInit {
  private readonly api = inject(ConnectionIntentsApi)
  private readonly languageService = inject(LanguageService)
  private readonly auth = inject(SupabaseAuthGateway)
  
  readonly requests = signal<ConnectionIntent[]>([])
  readonly isLoading = signal(true)
  readonly errorMessage = signal<string | null>(null)
  readonly activeConversation = signal<{ conversationId: string; otherProfileId: string; otherDisplayName: string } | null>(null)
  readonly busyRequestId = signal<string | null>(null)

  get strings() {
    const lang = this.languageService.language()
    return requestsStrings[lang] || requestsStrings['es']
  }

  ngOnInit(): void {
    this.loadRequests()
  }

  loadRequests(): void {
    this.isLoading.set(true)
    this.errorMessage.set(null)
    this.api.getPendingIntents().subscribe({
      next: (data) => {
        this.requests.set(data)
        this.isLoading.set(false)
      },
      error: () => {
        this.isLoading.set(false)
        this.errorMessage.set(this.strings.loadFailed)
      }
    })
  }

  accept(id: string): void {
    if (this.busyRequestId()) return
    this.busyRequestId.set(id)
    this.errorMessage.set(null)
    this.api.acceptIntent(id).subscribe({
      next: (conversation) => {
        const actorId = this.auth.session()?.id
        const otherProfile = conversation.participants?.find((participant) => participant.profileId !== actorId)
        if (otherProfile) {
          this.activeConversation.set({
            conversationId: conversation.conversationId,
            otherProfileId: otherProfile.profileId,
            otherDisplayName: this.strings.participant,
          })
        }
        this.busyRequestId.set(null)
        this.loadRequests()
      },
      error: () => {
        this.busyRequestId.set(null)
        this.errorMessage.set(this.strings.acceptFailed)
      },
    })
  }

  decline(id: string): void {
    if (this.busyRequestId()) return
    this.busyRequestId.set(id)
    this.errorMessage.set(null)
    this.api.declineIntent(id).subscribe({
      next: () => { this.busyRequestId.set(null); this.loadRequests() },
      error: () => { this.busyRequestId.set(null); this.errorMessage.set(this.strings.declineFailed) },
    })
  }
}
