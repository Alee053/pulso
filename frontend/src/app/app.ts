import { CommonModule } from '@angular/common'
import { Component, effect, inject, signal } from '@angular/core'
import { AuthPanelComponent } from './core/auth/auth-panel'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import { DiscoveryViewComponent } from './features/discovery/ui/discovery-view'
import { NavigationBarComponent } from './features/discovery/ui/navigation-bar'
import { OnboardingComponent } from './features/discovery/ui/onboarding'
import { ProfileViewComponent } from './features/discovery/ui/profile-view'
import { SavedViewComponent } from './features/discovery/ui/saved-view'
import { StartupDetailModalComponent } from './features/discovery/ui/startup-detail-modal'
import { PulsoSession } from './features/discovery/application/pulso-session'
import type { AppView, Startup } from './features/discovery/domain/startup'

@Component({
  selector: 'pulso-root',
  standalone: true,
  imports: [
    CommonModule,
    AuthPanelComponent,
    DiscoveryViewComponent,
    NavigationBarComponent,
    OnboardingComponent,
    ProfileViewComponent,
    SavedViewComponent,
    StartupDetailModalComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class AppComponent {
  readonly session = inject(PulsoSession)
  readonly auth = inject(SupabaseAuthGateway)
  readonly selectedStartup = signal<Startup | null>(null)
  private activeUserId: string | null = null

  private readonly authSessionEffect = effect(() => {
    const authenticatedUser = this.auth.session()

    if (!authenticatedUser) {
      if (this.activeUserId !== null) {
        this.activeUserId = null
        this.session.resetForUserChange()
      }
      return
    }

    if (this.activeUserId === authenticatedUser.id) {
      return
    }

    if (this.activeUserId !== null) {
      this.session.resetForUserChange()
    }

    this.activeUserId = authenticatedUser.id
    void this.session.loadReferenceCatalogs()
  })

  selectStartup(startup: Startup): void {
    this.selectedStartup.set(startup)
  }

  closeStartup(): void {
    this.selectedStartup.set(null)
  }

  navigateTo(view: AppView): void {
    this.selectedStartup.set(null)
    this.session.goToView(view)
  }
}
