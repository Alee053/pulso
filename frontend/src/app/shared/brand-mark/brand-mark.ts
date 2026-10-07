import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'
import { commonStrings } from '../strings/common.strings'

@Component({
  selector: 'pulso-brand-mark',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="brand-mark">
      <span class="brand-mark__symbol" aria-hidden="true">
        <img src="assets/pulso-logo.svg" alt="" class="size-full object-cover" />
      </span>
      <span *ngIf="!compact" class="brand-mark__name">{{ brandName }}</span>
    </div>
  `,
})
export class BrandMarkComponent {
  @Input() compact = false
  readonly brandName = commonStrings.brandName
}
