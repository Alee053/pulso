import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { commonStrings } from '../../shared/strings/common.strings'
import { SupabaseAuthGateway } from './supabase-auth.gateway'
import { authStrings } from './strings/auth.strings'

type AuthMode = 'sign-in' | 'sign-up'

@Component({
  selector: 'pulso-auth-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './auth-panel.html',
})
export class AuthPanelComponent implements OnInit {
  readonly strings = authStrings
  readonly common = commonStrings
  readonly auth = inject(SupabaseAuthGateway)
  readonly mode = signal<AuthMode>('sign-in')
  readonly isBusy = signal(false)
  readonly message = signal<string | null>(null)
  readonly messageIsError = signal(false)

  email = ''
  password = ''

  ngOnInit(): void {
    void this.auth.initialize().catch(() => {
      this.setFeedback(this.strings.initFailed, 'error')
    })
  }

  async submit(event?: SubmitEvent): Promise<void> {
    event?.preventDefault()
    const email = this.email.trim()
    if (this.isBusy()) {
      return
    }
    if (!email || !this.password) {
      this.setFeedback(this.strings.missingCredentials, 'error')
      return
    }

    this.isBusy.set(true)
    this.setFeedback(null)
    try {
      const result = this.mode() === 'sign-up'
        ? await this.auth.signUp(email, this.password)
        : await this.auth.signIn(email, this.password)
      this.password = ''
      this.setFeedback(
        result.requiresEmailConfirmation
          ? this.strings.confirmEmail
          : this.mode() === 'sign-up'
            ? this.strings.signUpSuccess
            : this.strings.signInSuccess,
      )
    } catch {
      this.setFeedback(
        this.mode() === 'sign-up'
          ? this.strings.signUpFailed
          : this.strings.signInFailed,
        'error',
      )
    } finally {
      this.isBusy.set(false)
    }
  }

  async signOut(): Promise<void> {
    if (this.isBusy()) {
      return
    }

    this.isBusy.set(true)
    this.setFeedback(null)
    try {
      await this.auth.signOut()
      this.setFeedback(this.strings.signedOut)
    } catch {
      this.setFeedback(this.common.sessionCloseFailed, 'error')
    } finally {
      this.isBusy.set(false)
    }
  }

  private setFeedback(message: string | null, kind: 'status' | 'error' = 'status'): void {
    this.message.set(message)
    this.messageIsError.set(kind === 'error')
  }

  toggleMode(): void {
    this.mode.update((current) => current === 'sign-in' ? 'sign-up' : 'sign-in')
    this.setFeedback(null)
  }
}
