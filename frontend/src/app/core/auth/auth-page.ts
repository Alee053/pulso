import { CommonModule } from '@angular/common'
import { Component, inject } from '@angular/core'
import { FeatureFlagsService } from '../feature-flags/feature-flags.service'
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark'
import { LanguageSwitcherComponent } from '../../shared/language-switcher/language-switcher'
import { IconComponent } from '../../shared/icon/icon'
import { ThemeSwitcherComponent } from '../../shared/theme-switcher/theme-switcher'
import { AuthPanelComponent } from './auth-panel'
import { authStrings } from './strings/auth.strings'

@Component({
  selector: 'pulso-auth-page',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent, BrandMarkComponent, LanguageSwitcherComponent, ThemeSwitcherComponent, IconComponent],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.css',
})
export class AuthPageComponent {
  readonly featureFlags = inject(FeatureFlagsService)
  readonly strings = authStrings
}
