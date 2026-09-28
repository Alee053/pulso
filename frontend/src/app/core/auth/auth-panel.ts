import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { commonStrings } from '../../shared/strings/common.strings'
import { SupabaseAuthGateway } from './supabase-auth.gateway'
import { authStrings } from './strings/auth.strings'

type AuthMode = 'sign-in' | 'sign-up'
type FlipDirection = 'forward' | 'backward' | null

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
  readonly flipDirection = signal<FlipDirection>(null)
  readonly isBusy = signal(false)
  readonly message = signal<string | null>(null)

  email = ''
  password = ''

  ngOnInit(): void {
    void this.auth.initialize().catch(() => {
      this.message.set(this.strings.initFailed)
    })
  }

  async submit(event?: SubmitEvent): Promise<void> {
    event?.preventDefault()
    const email = this.email.trim()
    if (this.isBusy()) {
      return
    }
    if (!email || !this.password) {
      this.message.set(this.strings.missingCredentials)
      return
    }

    this.isBusy.set(true)
    this.message.set(null)
    try {
      const result = this.mode() === 'sign-up'
        ? await this.auth.signUp(email, this.password)
        : await this.auth.signIn(email, this.password)
      this.password = ''
      this.message.set(
        result.requiresEmailConfirmation
          ? this.strings.confirmEmail
          : this.mode() === 'sign-up'
            ? this.strings.signUpSuccess
            : this.strings.signInSuccess,
      )
    } catch {
      this.message.set(
        this.mode() === 'sign-up'
          ? this.strings.signUpFailed
          : this.strings.signInFailed,
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
    this.message.set(null)
    try {
      await this.auth.signOut()
      this.message.set(this.strings.signedOut)
    } catch {
      this.message.set(this.common.sessionCloseFailed)
    } finally {
      this.isBusy.set(false)
    }
  }

  toggleMode(): void {
    this.mode.update((current) => {
      const nextMode = current === 'sign-in' ? 'sign-up' : 'sign-in'
      this.flipDirection.set(nextMode === 'sign-up' ? 'forward' : 'backward')
      return nextMode
    })
    this.message.set(null)
  }
}
