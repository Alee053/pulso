import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
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
export class AppComponent implements OnInit {
  readonly session = inject(PulsoSession)
  readonly selectedStartup = signal<Startup | null>(null)

  ngOnInit(): void {
    void this.session.loadReferenceCatalogs()
  }

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