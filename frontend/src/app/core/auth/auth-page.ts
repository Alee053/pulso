import { CommonModule } from '@angular/common'
import { Component } from '@angular/core'
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark'
import { commonStrings } from '../../shared/strings/common.strings'
import { AuthPanelComponent } from './auth-panel'
import { authStrings } from './strings/auth.strings'

@Component({
  selector: 'pulso-auth-page',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent, BrandMarkComponent],
  templateUrl: './auth-page.html',
})
export class AuthPageComponent {
  readonly strings = authStrings
  readonly common = commonStrings
}
