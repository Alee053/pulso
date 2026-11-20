import { Component, DestroyRef, inject, input, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { ReportsApi, type ReportTarget } from '../data-access/reports-api'
import { reportStrings } from '../strings/report.strings'

@Component({
  selector: 'pulso-report-form',
  templateUrl: './report-form.html',
})
export class ReportFormComponent {
  readonly target = input.required<ReportTarget>()
  readonly label = input.required<string>()

  protected readonly strings = reportStrings
  protected readonly state = signal<'idle' | 'sending' | 'success' | 'error'>('idle')
  private readonly api = inject(ReportsApi)
  private readonly destroyRef = inject(DestroyRef)

  protected submit(
    event: Event, form: HTMLFormElement, trigger: HTMLElement, reason: string, description: string,
  ): void {
    event.preventDefault()
    if (!reason || this.state() === 'sending' || this.state() === 'success') return

    if (form.contains(form.ownerDocument.activeElement)) trigger.focus()
    this.state.set('sending')
    this.api.create(this.target(), reason, description)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.state.set('success'),
        error: () => this.state.set('error'),
      })
  }
}
