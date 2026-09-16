import { CommonModule } from '@angular/common'
import { Component } from '@angular/core'
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark'
import { AuthPanelComponent } from './auth-panel'

@Component({
  selector: 'pulso-auth-page',
  standalone: true,
  imports: [CommonModule, AuthPanelComponent, BrandMarkComponent],
  templateUrl: './auth-page.html',
})
export class AuthPageComponent {}
