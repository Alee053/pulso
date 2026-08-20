import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'

@Component({
  selector: 'pulso-brand-mark',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center gap-3">
      <span
        class="relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-[0.9rem] border border-white/20 bg-[#baf4ce] text-sm font-semibold text-[#0a1710] shadow-[0_0_2rem_rgba(173,245,200,0.2)]"
        aria-hidden="true"
      >
        <span class="absolute -right-2 -top-3 size-7 rounded-full bg-white/60 blur-md"></span>
        <span class="relative">P</span>
      </span>
      <span
        *ngIf="!compact"
        class="text-[0.72rem] font-semibold tracking-[0.2em] text-white/90"
      >PULSO</span>
    </div>
  `,
})
export class BrandMarkComponent {
  @Input() compact = false
}
