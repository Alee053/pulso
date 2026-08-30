import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { SupabaseAuthGateway } from './supabase-auth.gateway'

type AuthMode = 'sign-in' | 'sign-up'

@Component({
  selector: 'pulso-auth-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './auth-panel.html',
})
export class AuthPanelComponent implements OnInit {
  readonly auth = inject(SupabaseAuthGateway)
  readonly mode = signal<AuthMode>('sign-in')
  readonly isBusy = signal(false)
  readonly message = signal<string | null>(null)

  email = ''
  password = ''

  ngOnInit(): void {
    void this.auth.initialize().catch(() => {
      this.message.set('No se pudo comprobar la sesión local.')
    })
  }

  async submit(event?: SubmitEvent): Promise<void> {
    event?.preventDefault()
    const email = this.email.trim()
    if (this.isBusy()) {
      return
    }
    if (!email || !this.password) {
      this.message.set('Escribe tu correo y contraseña para continuar.')
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
          ? 'Revisa tu correo para confirmar la cuenta.'
          : 'Sesión iniciada. El token se enviará solo al backend.',
      )
    } catch {
      this.message.set(
        this.mode() === 'sign-up'
          ? 'No se pudo crear la cuenta con esos datos.'
          : 'No se pudo iniciar sesión con esos datos.',
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
      this.message.set('Sesión cerrada.')
    } catch {
      this.message.set('No se pudo cerrar la sesión local.')
    } finally {
      this.isBusy.set(false)
    }
  }

  toggleMode(): void {
    this.mode.update((current) => current === 'sign-in' ? 'sign-up' : 'sign-in')
    this.message.set(null)
  }
}
