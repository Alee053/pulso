import { Component, DestroyRef, inject, input, output, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { ProfileBlockApi } from '../data-access/profile-block-api'
import { profileBlockStrings } from '../strings/profile-block.strings'

@Component({
  selector: 'pulso-profile-block-action',
  template: `
    <details #confirmation class="min-w-0 text-sm">
      <summary #trigger class="w-fit cursor-pointer rounded py-1.5 font-medium text-[var(--color-error)] hover:underline">
        @if (state() === 'blocked') {
          {{ strings.blocked }}
        } @else if (displayName(); as name) {
          {{ strings.blockNamedProfile(name) }}
        } @else {
          {{ strings.block }}
        }
      </summary>
      @if (state() !== 'blocked') {
        <p class="field-hint">{{ strings.confirm }}</p>
        <div class="mt-2 flex flex-wrap gap-2">
          <button type="button" class="contact-button" [disabled]="state() === 'blocking'"
                  (click)="confirmation.open = false; trigger.focus()">{{ strings.cancel }}</button>
          <button type="button" class="contact-button text-[var(--color-error)]" [disabled]="state() === 'blocking'"
                  (click)="block(trigger)">{{ strings.block }}</button>
        </div>
      }
    </details>
    @if (state() === 'blocking') {
      <p class="mt-2 text-sm" role="status">{{ strings.blocking }}</p>
    } @else if (state() === 'blocked') {
      <p class="status-message status-message--success mt-2" role="status">{{ strings.blockSuccess }}</p>
    } @else if (state() === 'error') {
      <p class="field-error" role="alert">{{ strings.blockFailed }}</p>
    }
  `,
})
export class ProfileBlockActionComponent {
  readonly profileId = input.required<string>()
  readonly displayName = input<string>()
  readonly blocked = output<void>()

  protected readonly strings = profileBlockStrings
  protected readonly state = signal<'ready' | 'blocking' | 'blocked' | 'error'>('ready')

  private readonly api = inject(ProfileBlockApi)
  private readonly destroyRef = inject(DestroyRef)

  protected block(trigger: HTMLElement): void {
    if (this.state() === 'blocking' || this.state() === 'blocked') return

    trigger.focus()
    this.state.set('blocking')
    this.api.blockProfile(this.profileId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.state.set('blocked')
          this.blocked.emit()
        },
        error: () => this.state.set('error'),
      })
  }
}
