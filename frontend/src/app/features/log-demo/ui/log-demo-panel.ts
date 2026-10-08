import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { logDemoStrings } from '../strings/log-demo.strings'
import { LogDemoApi } from '../data-access/log-demo-api'

type LogDemoState = 'idle' | 'sending' | 'logged' | 'failed' | 'unexpected'

@Component({
  selector: 'pulso-log-demo-panel',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './log-demo-panel.html',
  styleUrl: './log-demo-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogDemoPanelComponent {
  private readonly api = inject(LogDemoApi)

  readonly strings = logDemoStrings
  readonly isOpen = signal(false)
  readonly state = signal<LogDemoState>('idle')

  toggle(): void {
    this.isOpen.update((open) => !open)
  }

  close(): void {
    this.isOpen.set(false)
  }

  triggerFailure(): void {
    this.state.set('sending')

    this.api.triggerDataStoreFailure().subscribe({
      next: () => this.state.set('unexpected'),
      error: (error: unknown) => {
        this.state.set(
          error instanceof HttpErrorResponse && error.status === 503
            ? 'logged'
            : 'failed',
        )
      },
    })
  }
}
