import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, inject, input, signal } from '@angular/core'
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import { ProjectsApi } from '../data-access/projects-api'

@Component({
  selector: 'pulso-member-invitation-panel',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './member-invitation-panel.html',
})
export class MemberInvitationPanelComponent {
  readonly projectId = input.required<string>()
  private readonly api = inject(ProjectsApi)
  readonly busy = signal(false)
  readonly success = signal('')
  readonly error = signal('')
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)] }),
  })
  async submit(): Promise<void> {
    if (this.busy()) return
    this.error.set(''); this.success.set('')
    if (this.form.invalid) { this.form.markAllAsTouched(); return }
    this.busy.set(true)
    try {
      await firstValueFrom(this.api.createInvitation(this.projectId(), this.form.controls.email.value))
      this.success.set('La invitación fue registrada y enviada.')
      this.form.reset()
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse
        ? ({ 400:'Correo inválido.', 401:'Inicia sesión.', 403:'No tienes permisos.',
             404:'Proyecto inexistente.', 409:'Ya existe una invitación o membresía.' } as Record<number,string>)[e.status]
            ?? 'No se pudo enviar la invitación.'
        : 'No se pudo enviar la invitación.')
    } finally { this.busy.set(false) }
  }
}