import { Component, Input } from '@angular/core'
import type { Startup } from '../domain/startup'

@Component({
  selector: 'pulso-startup-thumb',
  template: `
    <span class="collection-thumb" aria-hidden="true">
      @if (startup.image && startup.image !== failedImage) {
        <img [src]="startup.image" alt="" loading="lazy" (error)="failedImage = startup.image" />
      } @else {
        {{ startup.logoMark }}
      }
    </span>
  `,
})
export class StartupThumbComponent {
  @Input({ required: true }) startup!: Startup
  protected failedImage = ''
}
