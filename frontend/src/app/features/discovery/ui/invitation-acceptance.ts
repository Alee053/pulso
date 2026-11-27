import { CommonModule } from '@angular/common'
import { Component, computed, effect, inject, OnDestroy, signal } from '@angular/core'
import { Subscription } from 'rxjs'
import { AuthPanelComponent } from '../../../core/auth/auth-panel'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { FeatureFlagsService } from '../../../core/feature-flags/feature-flags.service'
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark'
import { IconComponent } from '../../../shared/icon/icon'
import { LanguageSwitcherComponent } from '../../../shared/language-switcher/language-switcher'
import { ThemeSwitcherComponent } from '../../../shared/theme-switcher/theme-switcher'
import { InvitationLink } from '../application/invitation-link'
import type { InvitationFailure } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'

@Component({
  selector: 'pulso-invitation-acceptance',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent, BrandMarkComponent, IconComponent, LanguageSwitcherComponent, ThemeSwitcherComponent],
  templateUrl: './invitation-acceptance.html',
})
export class InvitationAcceptanceComponent implements OnDestroy {
  readonly flags = inject(FeatureFlagsService)
  readonly strings = invitationStrings
  readonly auth = inject(SupabaseAuthGateway)
  readonly link = inject(InvitationLink)
  readonly submitting = signal(false)
  readonly accepted = signal(false)
  readonly failure = signal<InvitationFailure | null>(null)
  readonly switchingAccount = signal(false)
  readonly signOutError = signal<string | null>(null)
  private request: Subscription | null = null
  private readonly userId = computed(() => this.auth.session()?.id ?? null)

  private readonly resetOnAccountOrLinkChange = effect(() => {
    this.userId()
    this.link.arrival()
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
