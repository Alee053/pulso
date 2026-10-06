import { CommonModule } from '@angular/common'
import { Component, effect, inject, OnInit, signal } from '@angular/core'
import { AuthPageComponent } from './core/auth/auth-page'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import { DiscoveryViewComponent } from './features/discovery/ui/discovery-view'
import { NavigationBarComponent } from './features/discovery/ui/navigation-bar'
import { OnboardingComponent } from './features/discovery/ui/onboarding'
import { ProfileViewComponent } from './features/discovery/ui/profile-view'
import { SavedViewComponent } from './features/discovery/ui/saved-view'
import { StartupDetailModalComponent } from './features/discovery/ui/startup-detail-modal'
import { PublicProjectDetailComponent } from './features/discovery/ui/public-project-detail'
import { PulsoSession } from './features/discovery/application/pulso-session'
import { IconComponent } from './shared/icon/icon'
import { commonStrings } from './shared/strings/common.strings'
import type { AppView, Startup } from './features/discovery/domain/startup'
import { ProjectCreateFormComponent } from './features/discovery/ui/project-create-form'

@Component({
    selector: 'pulso-root',
    standalone: true,
    imports: [
        CommonModule,
        AuthPageComponent,
        DiscoveryViewComponent,
        NavigationBarComponent,
        OnboardingComponent,
        ProfileViewComponent,
        ProjectCreateFormComponent,
        SavedViewComponent,
        StartupDetailModalComponent,
        PublicProjectDetailComponent,
        IconComponent,
    ],
    templateUrl: './app.html',
    styleUrl: './app.css',
})
export class AppComponent implements OnInit {
    readonly common = commonStrings
    readonly auth = inject(SupabaseAuthGateway)
    readonly session = inject(PulsoSession)
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
        void this.session.loadOnboardingPreferences()
    })

    ngOnInit(): void {
        document.title = commonStrings.documentTitle
        void this.auth.initialize().catch(() => undefined)
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