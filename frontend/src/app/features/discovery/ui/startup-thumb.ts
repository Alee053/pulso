import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'
import type { Startup } from '../domain/startup'

@Component({
  selector: 'pulso-startup-thumb',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="collection-thumb"
      [style.background]="'linear-gradient(145deg, ' + startup.accent + '55, #172338)'"
    >
      <img
        *ngIf="!hasError"
        [src]="startup.image"
        alt=""
        loading="lazy"
        class="absolute inset-0 size-full object-cover"
        (error)="hasError = true"
      />
      <div class="absolute inset-0 bg-gradient-to-t from-slate-950/70 via-transparent to-transparent"></div>
      <span class="absolute bottom-2 left-2 flex size-8 items-center justify-center rounded-xl border border-white/25 bg-black/25 text-xs font-semibold text-white backdrop-blur-md">
        {{ startup.logoMark }}
      </span>
    </div>
  `,
})
export class StartupThumbComponent {
  @Input() startup!: Startup
  hasError = false
}
