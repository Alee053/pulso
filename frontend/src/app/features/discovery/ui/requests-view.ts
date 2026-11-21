import { CommonModule } from '@angular/common'
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import type { Subscription } from 'rxjs'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import { connectionInboxContract } from '../data-access/discovery.catalog.contract'
import type { ConnectionIntent } from '../domain/startup'
import { profileBlockStrings } from '../strings/profile-block.strings'
import { requestsStrings } from '../strings/requests.strings'
import { ProfileBlockActionComponent } from './profile-block-action'

@Component({
  selector: 'pulso-requests-view',
  imports: [CommonModule, IconComponent, ProfileBlockActionComponent],
  template: `
    <main class="discovery-view">
      <header class="discovery-view__heading">
        <div>
          <span class="eyebrow">{{ strings.eyebrow }}</span>
          <h1 #heading class="page-title" tabindex="-1">{{ strings.title }}</h1>
          <p class="page-lead">{{ strings.lead }}</p>
        </div>
      </header>

      @if (blocked()) {
        <p role="status" class="mb-4">{{ blockStrings.blockSuccess }}</p>
      }
      @if (responseFailed()) {
        <div class="state-panel">
          <p role="alert">{{ strings.responseFailed }}</p>
          <button type="button" class="contact-button" (click)="loadRequests()">{{ strings.refresh }}</button>
        </div>
      }
      @if (requests().length > 0) {
        <ul class="grid gap-4">
          @for (request of requests(); track request.id) {
            <li class="panel flex flex-wrap items-start justify-between gap-3 p-4">
              <div class="min-w-0">
                <p class="mb-1 text-sm text-[var(--color-muted)]">{{ strings.reason }}: {{ request.reason }}</p>
                @if (request.message) {
                  <p class="break-words">{{ request.message }}</p>
                }
                <p class="mt-2 text-xs text-[var(--color-muted)]">{{ request.createdAt | date:'short' }}</p>
              </div>
              <div class="grid gap-2">
                <div class="flex flex-wrap gap-2">
                  <button type="button" class="solid-button" [disabled]="responding()"
                          (click)="respond(request.id, 'accept')">{{ strings.accept }}</button>
                  <button type="button" class="contact-button" [disabled]="responding()"
                          (click)="respond(request.id, 'decline')">{{ strings.decline }}</button>
                </div>
                <pulso-profile-block-action [profileId]="request.senderProfileId"
                                            (blocked)="onSenderBlocked(heading)" />
              </div>
            </li>
          }
        </ul>
      }
      @if (loading()) {
        <div class="state-panel" role="status">
          <span class="state-icon"><span class="spinner" aria-hidden="true"></span></span>
          <h2>{{ strings.loading }}</h2>
        </div>
      } @else if (loadFailed()) {
        <div class="state-panel">
          <p role="alert">{{ strings.loadFailed }}</p>
          <button type="button" class="contact-button" (click)="loadMore()">{{ commonStrings.retry }}</button>
        </div>
      } @else if (hasMore()) {
        <button type="button" class="contact-button mt-4" (click)="loadMore()">{{ strings.more }}</button>
      } @else if (requests().length === 0) {
        <div class="state-panel" role="status">
          <span class="state-icon"><pulso-icon name="heart" [size]="24" /></span>
          <h2>{{ strings.emptyTitle }}</h2>
          <p>{{ strings.emptyLead }}</p>
        </div>
      }
    </main>
  `,
})
export class RequestsViewComponent implements OnInit {
  protected readonly strings = requestsStrings
  protected readonly blockStrings = profileBlockStrings
  protected readonly commonStrings = commonStrings
  protected readonly requests = signal<ConnectionIntent[]>([])
  protected readonly loading = signal(false)
  protected readonly loadFailed = signal(false)
  protected readonly hasMore = signal(false)
  protected readonly blocked = signal(false)
  protected readonly responding = signal(false)
  protected readonly responseFailed = signal(false)

  private readonly api = inject(ConnectionIntentsApi)
  private readonly destroyRef = inject(DestroyRef)
  private pageRequest?: Subscription

  ngOnInit(): void {
    this.loadRequests()
  }

  protected loadRequests(): void {
    this.pageRequest?.unsubscribe()
    this.requests.set([])
    this.hasMore.set(false)
    this.responseFailed.set(false)
    this.loadPage(0)
  }

  protected loadMore(): void {
    if (!this.loading()) this.loadPage(this.requests().length)
  }

  protected onSenderBlocked(heading: HTMLElement): void {
    heading.focus()
    this.blocked.set(true)
    this.loadRequests()
  }

  protected respond(id: string, action: 'accept' | 'decline'): void {
    if (this.responding()) return

    this.responding.set(true)
    this.responseFailed.set(false)
    const response = action === 'accept' ? this.api.acceptIntent(id) : this.api.declineIntent(id)
    response.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.responding.set(false)
        this.loadRequests()
      },
      error: () => {
        this.responding.set(false)
        this.responseFailed.set(true)
      },
    })
  }

  private loadPage(offset: number): void {
    this.loading.set(true)
    this.loadFailed.set(false)
    this.pageRequest = this.api.getPendingIntents(offset)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          const pageSize = connectionInboxContract.pageSize
          this.requests.update((requests) => [...requests, ...data.slice(0, pageSize)])
          this.hasMore.set(data.length > pageSize)
          this.loading.set(false)
        },
        error: () => {
          this.loading.set(false)
          this.loadFailed.set(true)
        },
      })
  }
}
