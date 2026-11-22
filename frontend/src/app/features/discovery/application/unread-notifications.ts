import { inject, Injectable, signal } from '@angular/core'
import type { Subscription } from 'rxjs'
import { NotificationsApi } from '../data-access/notifications-api'

@Injectable({ providedIn: 'root' })
export class UnreadNotifications {
  private readonly api = inject(NotificationsApi)
  private readonly count = signal<number | null>(null)
  readonly unreadCount = this.count.asReadonly()
  private request?: Subscription

  refresh(): void {
    this.request?.unsubscribe()
    this.request = this.api.list(0, 1).subscribe({
      next: ({ meta }) => this.count.set(meta.unreadCount),
      error: () => this.count.set(null),
    })
  }

  update(count: number): void {
    this.request?.unsubscribe()
    this.count.set(count)
  }

  reset(): void {
    this.request?.unsubscribe()
    this.count.set(null)
  }
}
