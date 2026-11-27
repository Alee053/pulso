import { CommonModule } from '@angular/common'
import {
    Component,
    computed,
    effect,
    AfterViewChecked,
    ElementRef,
    HostListener,
    OnInit,
    Input,
    OnChanges,
    SimpleChanges,
    OnDestroy,
    inject,
    signal,
    Output,
    EventEmitter,
} from '@angular/core'
import { Subscription } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { OpportunityMatchApi } from '../data-access/opportunity-match-api'
import { PublicProjectApi } from '../data-access/public-project-api'
import { toStartupViewModel } from '../data-access/startup-view-model'
import type { OpportunityMatch } from '../domain/opportunity-match'
import type {
    PublicProjectDetail,
    PublicProjectState,
} from '../domain/startup'
import { IconComponent } from '../../../shared/icon/icon'
import { detailStrings } from '../strings/detail.strings'
import { projectStageStrings } from '../strings/project.strings'
import { matchStrings } from '../strings/match.strings'
import { OpportunityMatchComponent } from './opportunity-match'
import { ShareButtonComponent } from './share-button'
import { ReportFormComponent } from './report-form'
import { reportStrings } from '../strings/report.strings'
import { ProfileBlockActionComponent } from './profile-block-action'
import { ConnectionRequestFormComponent } from './connection-request-form'
import { ProjectDecisionComponent } from './project-decision'

@Component({
    selector: 'pulso-public-project-detail',
    standalone: true,
    imports: [
        CommonModule, IconComponent, OpportunityMatchComponent, ShareButtonComponent,
        ReportFormComponent, ProfileBlockActionComponent, ConnectionRequestFormComponent, ProjectDecisionComponent,
    ],
    templateUrl: './public-project-detail.html',
    styleUrl: './public-project-detail.css',
})
export class PublicProjectDetailComponent implements OnChanges, OnInit, AfterViewChecked, OnDestroy {
    readonly strings = detailStrings
    readonly matchStrings = matchStrings
    readonly reportStrings = reportStrings
    @Input() projectId!: string
    @Input() recommendation = ''
    @Output() closed = new EventEmitter<void>()

    private readonly api = inject(PublicProjectApi)
    private readonly matchApi = inject(OpportunityMatchApi)
    protected readonly auth = inject(SupabaseAuthGateway)
    private request?: Subscription
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef)
    private previousActiveElement: HTMLElement | null = null
    private originalOverflow = ''

    readonly state = signal<PublicProjectState>({ status: 'loading' })
    protected readonly matches = signal(new Map<string, OpportunityMatch>())
    protected readonly matchState = signal<'loading' | 'loaded' | 'error'>('loaded')
    private readonly matchAttempts = signal(0)
    protected readonly decisionStartup = computed(() => {
        const state = this.state()
        if (state.status !== 'loaded') return null
        const { project } = state
        const startup = toStartupViewModel({ ...project, city: project.city ?? '', sector: project.sector.name })
        return { ...startup, tags: project.tags.map((tag) => tag.slug) }
    })
    private readonly loadMatches = effect((onCleanup) => {
        const state = this.state()
        const signedIn = this.auth.session() !== null
        this.matchAttempts()
        this.matches.set(new Map())
        if (!signedIn || state.status !== 'loaded' || state.project.opportunities.length === 0) return

        this.matchState.set('loading')
        const request = this.matchApi.list(state.project.id).subscribe({
            next: (matches) => {
                this.matches.set(new Map(matches.map(match => [match.opportunityId, match])))
                this.matchState.set('loaded')
            },
            error: () => this.matchState.set('error'),
        })
        onCleanup(() => request.unsubscribe())
    })
    hasImageError = false

    get isSignedIn(): boolean {
        return this.auth.session() !== null
    }

    canBlockProfile(profileId: string): boolean {
        const actor = this.auth.session()
        return actor !== null && actor.id !== profileId
    }

    get isLoading(): boolean {
        return this.state().status === 'loading'
    }

    get isEmpty(): boolean {
        return this.state().status === 'empty'
    }

    get hasError(): boolean {
        return this.state().status === 'error'
    }

    get errorMessage(): string {
        const current = this.state()
        return current.status === 'error' ? current.message : ''
    }

    get isRetryable(): boolean {
        const current = this.state()
        return current.status === 'error' ? current.retryable : false
    }

    get loadedProject(): PublicProjectDetail | null {
        const current = this.state()
        return current.status === 'loaded' ? current.project : null
    }

    ngOnInit(): void {
        this.previousActiveElement = document.activeElement instanceof HTMLElement
            ? document.activeElement : null
        this.originalOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
    }

    ngAfterViewChecked(): void {
        if (!this.element.nativeElement.contains(document.activeElement)) {
            this.focusableElements()[0]?.focus()
        }
    }

    @HostListener('document:keydown', ['$event'])
    handleKeyDown(event: KeyboardEvent): void {
        if (event.key === 'Escape') {
            event.preventDefault()
            this.close()
            return
        }
        if (event.key !== 'Tab') return
        const elements = this.focusableElements()
        const first = elements[0]
        const last = elements[elements.length - 1]
        if (!first || !last) {
            event.preventDefault()
        } else if (event.shiftKey && document.activeElement === first) {
            event.preventDefault()
            last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault()
            first.focus()
        }
    }

    private focusableElements(): HTMLElement[] {
        const candidates = this.element.nativeElement.querySelectorAll<HTMLElement>(
            'button:not(:disabled), a[href], summary, input:not(:disabled):not([type="hidden"]), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
        )
        return Array.from(candidates).filter(candidate => {
            // A closed disclosure exposes its own summary, even inside another open disclosure.
            const container = candidate.matches('details > summary:first-of-type')
                ? candidate.parentElement?.parentElement : candidate
            return !container?.closest('details:not([open])') && !candidate.closest('[hidden], [inert]')
        })
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['projectId']) this.loadProject()
    }

    loadProject(): void {
        this.hasImageError = false
        this.request?.unsubscribe()
        this.state.set({ status: 'loading' })

        this.request = this.api
            .getPublicDetail(this.projectId)
            .subscribe({
                next: (project) => {
                    if (!project || !project.id) {
                        this.state.set({ status: 'empty' })
                    } else {
                        this.state.set({ status: 'loaded', project })
                    }
                },
                error: (err) => {
                    let message: string = this.strings.errorGeneric
                    let retryable = true

                    if (err.status === 404) {
                        this.state.set({ status: 'empty' })
                        return
                    } else if (err.status === 400) {
                        message = this.strings.errorInvalidReference
                        retryable = false
                    } else if (err.status === 403) {
                        message = this.strings.errorForbidden
                        retryable = false
                    } else if (err.status === 0) {
                        message = this.strings.errorUnreachable
                        retryable = true
                    } else if (err.status >= 500) {
                        message = this.strings.errorServer
                        retryable = true
                    }

                    this.state.set({ status: 'error', message, retryable })
                },
            })
    }

    stageLabel(stage: string): string {
        return (projectStageStrings as Record<string, string>)[stage] ?? stage
    }

    protected retryMatches(): void {
        this.matchAttempts.update(attempts => attempts + 1)
    }

    retry(): void {
        const current = this.state()
        if (current.status === 'error' && current.retryable) {
            this.loadProject()
        }
    }

    close(): void {
        this.closed.emit()
    }

    handleBackdropClick(event: MouseEvent): void {
        if (event.target === event.currentTarget) this.close()
    }

    ngOnDestroy(): void {
        this.request?.unsubscribe()
        document.body.style.overflow = this.originalOverflow
        if (this.previousActiveElement?.isConnected) this.previousActiveElement.focus()
    }
}
