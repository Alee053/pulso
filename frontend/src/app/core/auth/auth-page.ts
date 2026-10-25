import { CommonModule } from '@angular/common'
import { Component, inject } from '@angular/core'
import { FeatureFlagsService } from '../feature-flags/feature-flags.service'
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark'
import { LanguageSwitcherComponent } from '../../shared/language-switcher/language-switcher'
import { commonStrings } from '../../shared/strings/common.strings'
import { AuthPanelComponent } from './auth-panel'
import { authStrings } from './strings/auth.strings'

@Component({
  selector: 'pulso-auth-page',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent, BrandMarkComponent, LanguageSwitcherComponent],
  templateUrl: './auth-page.html',
})
export class AuthPageComponent {
  readonly featureFlags = inject(FeatureFlagsService)
  readonly strings = authStrings
  readonly common = commonStrings
}
