import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { ConnectionIntentsApi } from '../data-access/connection-intents-api'
import type { ConnectionIntent } from '../domain/startup'
import { LanguageService } from '../../../core/i18n/language.service'
import { requestsStrings } from '../strings/requests.strings'

@Component({
  selector: 'pulso-requests-view',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <main class="discovery-view">
      <header class="discovery-view__heading">
        <div>
          <span class="eyebrow">{{ strings.eyebrow }}</span>
          <h1 class="page-title">{{ strings.title }}</h1>
          <p class="page-lead">{{ strings.lead }}</p>
        </div>
      </header>

      <div *ngIf="isLoading()" class="state-panel" role="status">
        <span class="state-icon"><span class="spinner" aria-hidden="true"></span></span>
        <h2>Cargando solicitudes...</h2>
      </div>

      <div *ngIf="!isLoading() && requests().length === 0" class="state-panel" role="status">
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
            <button type="button" class="solid-button" (click)="accept(req.id)">{{ strings.accept }}</button>
            <button type="button" class="contact-button" (click)="decline(req.id)">{{ strings.decline }}</button>
          </div>
        </div>
      </div>
    </main>
  `,
})
export class RequestsViewComponent implements OnInit {
  private readonly api = inject(ConnectionIntentsApi)
  private readonly languageService = inject(LanguageService)
  
  readonly requests = signal<ConnectionIntent[]>([])
  readonly isLoading = signal(true)

  get strings() {
    const lang = this.languageService.language()
    return requestsStrings[lang] || requestsStrings['es']
  }

  ngOnInit(): void {
    this.loadRequests()
  }

  loadRequests(): void {
    this.isLoading.set(true)
    this.api.getPendingIntents().subscribe({
      next: (data) => {
        this.requests.set(data)
        this.isLoading.set(false)
      },
      error: () => {
        this.isLoading.set(false)
      }
    })
  }

  accept(id: string): void {
    this.api.acceptIntent(id).subscribe(() => this.loadRequests())
  }

  decline(id: string): void {
    this.api.declineIntent(id).subscribe(() => this.loadRequests())
  }
}