import { CommonModule } from '@angular/common'
import { Component, effect, inject, OnDestroy, signal } from '@angular/core'
import { Subscription } from 'rxjs'
import { AuthPanelComponent } from '../../../core/auth/auth-panel'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { InvitationLink } from '../application/invitation-link'
import type { InvitationFailure } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'

@Component({
  selector: 'pulso-invitation-acceptance',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent],
  templateUrl: './invitation-acceptance.html',
})
export class InvitationAcceptanceComponent implements OnDestroy {
  readonly strings = invitationStrings
  readonly auth = inject(SupabaseAuthGateway)
  readonly link = inject(InvitationLink)
  readonly submitting = signal(false)
  readonly accepted = signal(false)
  readonly failure = signal<InvitationFailure | null>(null)
  readonly switchingAccount = signal(false)
  readonly signOutError = signal<string | null>(null)
  private request: Subscription | null = null
  private userId: string | null | undefined

  private readonly sessionEffect = effect(() => {
    const userId = this.auth.session()?.id ?? null
    if (this.userId === userId) return
    this.userId = userId
    this.request?.unsubscribe()
    this.submitting.set(false)
    this.accepted.set(false)
    this.failure.set(null)
    this.signOutError.set(null)
  })

  ngOnDestroy(): void { this.request?.unsubscribe() }

  accept(): void {
    if (this.submitting() || this.accepted() || this.switchingAccount()) return
    if (!this.auth.session()) {
      this.failure.set({ message: this.strings.authRequired, retryable: true })
      return
    }
    this.failure.set(null)
    this.submitting.set(true)
    this.request = this.link.accept().subscribe({
      next: () => {
        this.link.clearToken()
        this.submitting.set(false)
        this.accepted.set(true)
      },
      error: (failure: InvitationFailure) => {
        this.submitting.set(false)
        this.failure.set(failure)
      },
    })
  }

  async changeAccount(): Promise<void> {
    if (this.submitting() || this.switchingAccount()) return
    this.switchingAccount.set(true)
    this.signOutError.set(null)
    try {
      await this.auth.signOut()
    } catch {
      this.signOutError.set(this.strings.signOutFailed)
    } finally {
      this.switchingAccount.set(false)
    }
  }
}
