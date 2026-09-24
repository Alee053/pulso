import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'
import { commonStrings } from '../strings/common.strings'

@Component({
  selector: 'pulso-brand-mark',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center gap-3">
      <span
        class="relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-[0.9rem] shadow-[0_0_2rem_rgba(105,190,242,0.24)]"
        aria-hidden="true"
      >
        <img src="assets/pulso-logo.svg" alt="" class="size-full object-cover" />
      </span>
      <span
        *ngIf="!compact"
        class="text-[0.72rem] font-semibold tracking-[0.2em] text-white/90"
      >{{ brandName }}</span>
    </div>
  `,
})
export class BrandMarkComponent {
  @Input() compact = false
  readonly brandName = commonStrings.brandName
}
