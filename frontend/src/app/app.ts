import { CommonModule, DOCUMENT } from '@angular/common'
import { Component, computed, effect, HostListener, inject, OnInit, signal } from '@angular/core'
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
import { commonStrings } from './shared/strings/common.strings'
import type { AppView, Startup } from './features/discovery/domain/startup'
import { InvitationLink } from './features/discovery/application/invitation-link'
import { InvitationAcceptanceComponent } from './features/discovery/ui/invitation-acceptance'
import { ProjectTeamsComponent } from './features/discovery/ui/project-teams'
import { ProjectCreateFormComponent } from './features/discovery/ui/project-create-form'
import { FeatureFlagsService } from './core/feature-flags/feature-flags.service'
import { RequestsViewComponent } from './features/discovery/ui/requests-view'
import { MessagesViewComponent } from './features/discovery/ui/messages-view'
import { conversationHref, requestInboxHref, type RequestInbox } from './features/discovery/domain/connection'
import { NotificationsViewComponent } from './features/discovery/ui/notifications-view'
import { UnreadNotifications } from './features/discovery/application/unread-notifications'
import { readWorkspaceLink } from './features/discovery/application/workspace-link'

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
        MessagesViewComponent,
        NotificationsViewComponent,
        PublicProjectDetailComponent,
        IconComponent,
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
    private readonly unread = inject(UnreadNotifications)
    private readonly browser = inject(DOCUMENT).defaultView
    readonly selectedStartup = signal<Startup | null>(null)
    private readonly workspaceLink = signal(readWorkspaceLink(this.browser?.location.pathname ?? ''))
    // Opening a project sheet keeps its originating inbox or team view mounted.
    private readonly backgroundLink = computed(() => {
        const link = this.workspaceLink()
        if (link?.kind !== 'project') return link
        const path = this.browser?.history.state?.pulsoBackgroundPath
        return typeof path === 'string' ? readWorkspaceLink(path) : null
    })
    readonly linkedProjectId = computed(() => {
        const link = this.workspaceLink()
        return link?.kind === 'project' ? link.reference : null
    })
    readonly conversationId = computed(() => {
        const link = this.backgroundLink()
        return link?.kind === 'conversation' ? link.id : null
    })
    readonly initialInbox = computed(() => {
        const link = this.backgroundLink()
        return link?.kind === 'requests' ? link.inbox : 'received'
    })
    readonly activeView = computed<AppView>(() => {
        const link = this.backgroundLink()
        if (link?.kind === 'messages' || link?.kind === 'conversation' || link?.kind === 'requests') return link.kind
        return this.session.activeView()
    })
    readonly unreadCount = this.unread.unreadCount
    private activeUserId: string | null = null

    private readonly authSessionEffect = effect(() => {
        const authenticatedUser = this.auth.session()

        if (!authenticatedUser) {
            if (this.activeUserId !== null) {
                this.activeUserId = null
                this.session.resetForUserChange()
                this.selectedStartup.set(null)
                this.unread.reset()
            }
            return
        }

        if (this.activeUserId === authenticatedUser.id) {
            return
        }

        if (this.activeUserId !== null) {
            this.session.resetForUserChange()
            this.selectedStartup.set(null)
            this.unread.reset()
        }

        this.activeUserId = authenticatedUser.id
        void this.session.loadReferenceCatalogs()
        void this.session.loadOnboardingPreferences()
        void this.session.loadProjectCollections()
        this.unread.refresh()
    })

    private readonly documentTitleEffect = effect(() => {
        this.language.language()
        document.title = commonStrings.documentTitle
    })

    private readonly disabledProjectCreationEffect = effect(() => {
        if (this.session.activeView() === 'create-project' && !this.featureFlags.isLoading() && !this.featureFlags.isEnabled('project_creation')) {
            this.session.goToView('discover')
        }
    })

    ngOnInit(): void {
        const initialView = this.browser?.history.state?.pulsoView
        if (isWorkspaceView(initialView)) this.session.goToView(initialView)
        void this.auth.initialize().catch(() => undefined)
        void this.featureFlags.load()
    }

    @HostListener('window:focus')
    refreshOnFocus(): void {
        if (!this.featureFlags.isLoading()) {
            void this.featureFlags.load()
        }
        if (this.auth.session()) this.unread.refresh()
    }

    @HostListener('window:keydown', ['$event'])
    openMessagesShortcut(event: KeyboardEvent): void {
        if (event.defaultPrevented || event.repeat || !event.altKey || !event.shiftKey || event.ctrlKey || event.metaKey
            || event.key.toLowerCase() !== 'm' || !this.auth.session() || !this.session.hasStarted()
            || this.session.isEditingPreferences() || this.invitation.requested() || this.selectedStartup() || this.linkedProjectId()
            || this.activeView() === 'detail' || this.browser?.document.querySelector('dialog[open], [role="dialog"]')
            || (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest('textarea, input, select')))) return
        event.preventDefault()
        this.navigateTo('messages')
    }

    @HostListener('click', ['$event'])
    openProjectLink(event: MouseEvent): void {
        if (!this.browser || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey
            || event.shiftKey || !(event.target instanceof Element)) return
        const anchor = event.target.closest('a[href]')
        if (!(anchor instanceof HTMLAnchorElement) || anchor.target || anchor.hasAttribute('download')
            || anchor.origin !== this.browser.location.origin || anchor.hash || anchor.search) return
        const link = readWorkspaceLink(anchor.pathname)
        if (link?.kind !== 'project') return

        event.preventDefault()
        const { history, location } = this.browser
        const view = this.session.activeView()
        history.replaceState({ ...history.state, pulsoView: view }, '')
        history.pushState({ pulsoView: view, pulsoBackgroundPath: location.pathname }, '', anchor.pathname)
        this.workspaceLink.set(link)
    }

    @HostListener('window:popstate')
    readLink(): void {
        this.selectedStartup.set(null)
        const view = this.browser?.history.state?.pulsoView
        if (isWorkspaceView(view)) this.session.goToView(view)
        this.workspaceLink.set(readWorkspaceLink(this.browser?.location.pathname ?? ''))
    }

    reloadPage(): void {
        this.browser?.location.reload()
    }

    selectStartup(startup: Startup): void {
        this.selectedStartup.set(startup)
    }

    closeStartup(): void {
        this.selectedStartup.set(null)
    }

    navigateTo(view: AppView): void {
        this.selectedStartup.set(null)
        if (view === 'messages') {
            this.openConversation(null)
            return
        }
        const path = view === 'requests' ? requestInboxHref('received') : '/'
        this.setDestination(path, view)
        this.session.goToView(view)
    }

    openRequests(inbox: RequestInbox): void {
        this.setDestination(requestInboxHref(inbox), 'requests')
    }

    openConversation(id: string | null): void {
        this.setDestination(id ? conversationHref(id) : '/conversaciones', 'messages')
    }

    private setDestination(path: string, view: AppView): void {
        if (!this.browser) return
        const { history, location } = this.browser
        if (location.pathname !== path || (path === '/' && this.activeView() !== view)) {
            history.replaceState({ ...history.state, pulsoView: this.session.activeView() }, '')
            history.pushState({ pulsoView: view }, '', path)
        }
        this.workspaceLink.set(readWorkspaceLink(path))
        const container = this.browser.document.getElementById('workspace-content')
        container?.focus({ preventScroll: true })
        this.browser.document.documentElement.scrollTop = 0
    }

    closeWorkspaceLink(): void {
        if (!this.browser || !this.workspaceLink()) return
        const { location, history } = this.browser
        if (typeof history.state?.pulsoBackgroundPath === 'string') {
            history.back()
            return
        }
        history.replaceState(history.state, '', '/' + location.search + location.hash)
        this.workspaceLink.set(null)
    }
}

function isWorkspaceView(value: unknown): value is AppView {
    return ['discover', 'saved', 'teams', 'requests', 'profile', 'create-project', 'notifications', 'messages'].includes(String(value))
}
