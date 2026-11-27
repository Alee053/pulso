import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, inject, input, signal } from '@angular/core'
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms'
import { firstValueFrom } from 'rxjs'
import { invitationStrings } from '../strings/invitation.strings'
import { ProjectsApi } from '../data-access/projects-api'

@Component({
  selector: 'pulso-member-invitation-panel',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './member-invitation-panel.html',
})
export class MemberInvitationPanelComponent {
  readonly strings = invitationStrings
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
      this.success.set(this.strings.invited)
      this.form.reset()
    } catch (e) {
      this.error.set(e instanceof HttpErrorResponse
        ? ({ 400: this.strings.emailRejected, 401: this.strings.sessionExpired, 403: this.strings.permission,
             404: this.strings.projectUnavailable, 409: this.strings.duplicate } as Record<number,string>)[e.status]
            ?? this.strings.inviteFailed
        : this.strings.inviteFailed)
    } finally { this.busy.set(false) }
  }
}
