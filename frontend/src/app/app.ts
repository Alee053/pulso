import { CommonModule } from '@angular/common'
import { Component, effect, HostListener, inject, OnInit, signal } from '@angular/core'
import { AuthPageComponent } from './core/auth/auth-page'
import { SupabaseAuthGateway } from './core/auth/supabase-auth.gateway'
import { DiscoveryViewComponent } from './features/discovery/ui/discovery-view'
import { NavigationBarComponent } from './features/discovery/ui/navigation-bar'
import { OnboardingComponent } from './features/discovery/ui/onboarding'
import { ProfileViewComponent } from './features/discovery/ui/profile-view'
import { SavedViewComponent } from './features/discovery/ui/saved-view'
import { PublicProjectDetailComponent } from './features/discovery/ui/public-project-detail'
import { PulsoSession } from './features/discovery/application/pulso-session'
import { LanguageService } from './core/i18n/language.service'
import { IconComponent } from './shared/icon/icon'
import { LanguageSwitcherComponent } from './shared/language-switcher/language-switcher'
import { commonStrings } from './shared/strings/common.strings'
import type { AppView, Startup } from './features/discovery/domain/startup'
import { InvitationLink } from './features/discovery/application/invitation-link'
import { InvitationAcceptanceComponent } from './features/discovery/ui/invitation-acceptance'
import { ProjectTeamsComponent } from './features/discovery/ui/project-teams'
import { ProjectCreateFormComponent } from './features/discovery/ui/project-create-form'
import { FeatureFlagsService } from './core/feature-flags/feature-flags.service'
import { RequestsViewComponent } from './features/discovery/ui/requests-view'

@Component({
    selector: 'pulso-root',
    standalone: true,
    imports: [
        CommonModule,
        AuthPageComponent,
        InvitationAcceptanceComponent,
        ProjectTeamsComponent,
        DiscoveryViewComponent,
        NavigationBarComponent,
        OnboardingComponent,
        ProfileViewComponent,
        ProjectCreateFormComponent,
        SavedViewComponent,
        RequestsViewComponent,
        PublicProjectDetailComponent,
        IconComponent,
        LanguageSwitcherComponent,
    ],
    templateUrl: './app.html',
    styleUrl: './app.css',
})
export class AppComponent implements OnInit {
    readonly invitation = inject(InvitationLink)
    readonly common = commonStrings
    readonly auth = inject(SupabaseAuthGateway)
    private readonly language = inject(LanguageService)
    readonly session = inject(PulsoSession)
    readonly featureFlags = inject(FeatureFlagsService)
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
        void this.session.loadProjectCollections()
    })

    private readonly documentTitleEffect = effect(() => {
        this.language.language()
        document.title = commonStrings.documentTitle
    })

    private readonly disabledProjectCreationEffect = effect(() => {
        if (this.session.activeView() === 'create-project' && !this.featureFlags.isEnabled('project_creation')) {
            this.session.goToView('discover')
        }
    })

    ngOnInit(): void {
        void this.auth.initialize().catch(() => undefined)
        void this.featureFlags.load()
    }

    @HostListener('window:focus')
    refreshFeatureFlags(): void {
        if (!this.featureFlags.isLoading()) {
            void this.featureFlags.load()
        }
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
