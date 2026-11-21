import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, inject, Input, OnChanges, signal, SimpleChanges } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProfileBlocksApi } from '../data-access/profile-blocks-api'
import { profileBlockStrings as strings } from '../strings/profile-block.strings'

@Component({
  selector: 'pulso-profile-block-control',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile-block-control.html',
})
export class ProfileBlockControlComponent implements OnChanges {
  @Input({ required: true }) profileId = ''
  @Input({ required: true }) displayName = ''

  readonly strings = strings
  readonly state = signal<'loading' | 'blocked' | 'unblocked' | 'error'>('loading')
  readonly saving = signal(false)
  readonly errorMessage = signal<string | null>(null)
  readonly successMessage = signal<string | null>(null)
  private readonly auth = inject(SupabaseAuthGateway)
  private readonly api = inject(ProfileBlocksApi)

  get isSelf(): boolean { return this.auth.session()?.id === this.profileId }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['profileId']) this.load()
  }

  async load(): Promise<void> {
    if (!this.profileId || this.isSelf || !this.auth.session()) return
    this.state.set('loading')
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      const state = await firstValueFrom(this.api.status(this.profileId))
      this.state.set(state.blocked ? 'blocked' : 'unblocked')
    } catch (error) {
      this.state.set('error')
      this.errorMessage.set(error instanceof HttpErrorResponse && error.status === 403
        ? strings.actionFailed : strings.loadFailed)
    }
  }

  async toggle(): Promise<void> {
    if (this.saving() || this.state() === 'loading' || this.state() === 'error' || this.isSelf) return
    const wasBlocked = this.state() === 'blocked'
    this.saving.set(true)
    this.errorMessage.set(null)
    this.successMessage.set(null)
    try {
      const result = await firstValueFrom(wasBlocked
        ? this.api.unblock(this.profileId)
        : this.api.block(this.profileId))
      this.state.set(result.blocked ? 'blocked' : 'unblocked')
      this.successMessage.set(result.blocked ? strings.blocked : strings.unblocked)
    } catch (error) {
      this.errorMessage.set(error instanceof HttpErrorResponse && error.status === 409
        ? strings.loadFailed : strings.actionFailed)
    } finally {
      this.saving.set(false)
    }
  }
}
