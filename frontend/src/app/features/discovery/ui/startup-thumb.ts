import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'
import type { Startup } from '../domain/startup'

@Component({
  selector: 'pulso-startup-thumb',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="relative aspect-[1.15] shrink-0 overflow-hidden rounded-2xl border border-white/10 sm:w-36"
      [style.background]="'linear-gradient(145deg, ' + startup.accent + '55, #18201d)'"
    >
      <img
        *ngIf="!hasError"
        [src]="startup.image"
        alt=""
        class="absolute inset-0 size-full object-cover transition duration-500 group-hover:scale-105"
        (error)="hasError = true"
      />
      <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-black/5 to-white/15"></div>
      <span class="absolute bottom-3 left-3 flex size-9 items-center justify-center rounded-xl border border-white/25 bg-black/25 text-xs font-semibold text-white backdrop-blur-md">
        {{ startup.logoMark }}
      </span>
    </div>
  `,
})
export class StartupThumbComponent {
  @Input() startup!: Startup
  hasError = false
}
