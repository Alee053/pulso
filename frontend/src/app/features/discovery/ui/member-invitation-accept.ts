import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, inject, input, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { ProjectsApi } from '../data-access/projects-api'

@Component({
  selector: 'pulso-member-invitation-accept',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './member-invitation-accept.html',
})
export class MemberInvitationAcceptComponent {
  readonly token = input.required<string>()
  readonly busy = signal(false)
  readonly error = signal('')
  readonly projectId = signal<string | null>(null)
  private readonly api = inject(ProjectsApi)
  async accept(): Promise<void> {
    if (this.busy() || !this.token()) return
    this.busy.set(true); this.error.set('')
    try {
      const response = await firstValueFrom(this.api.acceptInvitation(this.token()))
      this.projectId.set(response.projectId)
      // Remove the plaintext token from browser history immediately after success.
      history.replaceState(null, '', location.pathname + location.search)
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse
        ? ({ 400:'Invitación inválida.', 401:'Inicia sesión.', 403:'Usa la cuenta invitada.',
             404:'Invitación inexistente o utilizada.', 409:'Ya perteneces al proyecto.' } as Record<number,string>)[e.status]
            ?? 'No se pudo aceptar la invitación.'
        : 'No se pudo aceptar la invitación.')
    } finally { this.busy.set(false) }
  }
}
