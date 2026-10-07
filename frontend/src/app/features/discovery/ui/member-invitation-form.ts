import { CommonModule } from '@angular/common'
import { Component, effect, EventEmitter, inject, Input, OnChanges, OnDestroy, Output, signal } from '@angular/core'
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms'
import { Subscription } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { MemberInvitationsApi } from '../data-access/member-invitations-api'
import type { CurrentProjectMembership, InvitationFailure } from '../domain/member-invitation'
import { canAdministerMemberships } from '../domain/membership-administration'
import { invitationStrings } from '../strings/invitation.strings'

@Component({
  selector: 'pulso-member-invitation-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './member-invitation-form.html',
})
export class MemberInvitationFormComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) project!: CurrentProjectMembership
  @Output() invited = new EventEmitter<void>()
  readonly strings = invitationStrings
  readonly auth = inject(SupabaseAuthGateway)
  private readonly api = inject(MemberInvitationsApi)
  private request: Subscription | null = null
  private userId: string | null | undefined
  readonly email = new FormControl('', { nonNullable: true, validators: [
    Validators.required, Validators.email, Validators.maxLength(254),
  ] })
  readonly submitting = signal(false)
  readonly registered = signal(false)
  readonly failure = signal<InvitationFailure | null>(null)

  private readonly sessionEffect = effect(() => {
    const userId = this.auth.session()?.id ?? null
    if (this.userId === userId) return
    this.userId = userId
    this.reset()
  })

  get canInvite(): boolean {
    return !!this.auth.session() && canAdministerMemberships(this.project)
  }

  ngOnChanges(): void { this.reset() }
  ngOnDestroy(): void { this.request?.unsubscribe() }

  submit(): void {
    if (this.submitting() || !this.canInvite) return
    this.email.setValue(this.email.value.trim())
    this.email.markAsTouched()
    this.registered.set(false)
    this.failure.set(null)
    if (this.email.invalid) return
    this.submitting.set(true)
    this.request = this.api.invite(this.project.projectId, this.email.value).subscribe({
      next: () => {
        this.submitting.set(false)
        this.registered.set(true)
        this.email.reset()
        this.invited.emit()
      },
      error: (failure: InvitationFailure) => {
        this.submitting.set(false)
        this.failure.set(failure)
      },
    })
  }

  private reset(): void {
    this.request?.unsubscribe()
    this.email.reset()
    this.submitting.set(false)
    this.registered.set(false)
    this.failure.set(null)
  }
}
