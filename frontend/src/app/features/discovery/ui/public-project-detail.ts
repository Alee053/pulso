import { CommonModule } from '@angular/common'
import {
    Component,
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
import type { OpportunityMatch } from '../domain/opportunity-match'
import type {
    PublicProjectDetail,
    PublicProjectState,
    Startup,
} from '../domain/startup'
import { IconComponent } from '../../../shared/icon/icon'
import { detailStrings } from '../strings/detail.strings'
import { projectStageStrings } from '../strings/project.strings'
import { matchStrings } from '../strings/match.strings'
import { OpportunityMatchComponent } from './opportunity-match'
import { ShareButtonComponent } from './share-button'

@Component({
    selector: 'pulso-public-project-detail',
    standalone: true,
    imports: [CommonModule, IconComponent, OpportunityMatchComponent, ShareButtonComponent],
    templateUrl: './public-project-detail.html',
})
export class PublicProjectDetailComponent implements OnChanges, OnInit, AfterViewChecked, OnDestroy {
    readonly strings = detailStrings
    readonly matchStrings = matchStrings
    @Input() projectId!: string
    @Input() recommendation = ''
    @Output() closed = new EventEmitter<void>()

    private readonly api = inject(PublicProjectApi)
    private readonly matchApi = inject(OpportunityMatchApi)
    private readonly auth = inject(SupabaseAuthGateway)
    private request?: Subscription
    private matchRequest?: Subscription
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef)
    private previousActiveElement: HTMLElement | null = null
    private originalOverflow = ''

    readonly state = signal<PublicProjectState>({ status: 'loading' })
    readonly matches = signal<OpportunityMatch[]>([])
    hasImageError = false

    get isSignedIn(): boolean {
        return this.auth.session() !== null
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

    private focusableElements(): NodeListOf<HTMLElement> {
        return this.element.nativeElement.querySelectorAll<HTMLElement>(
            'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
        )
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['projectId']) this.loadProject()
    }

    loadProject(): void {
        this.hasImageError = false
        this.request?.unsubscribe()
        this.matchRequest?.unsubscribe()
        this.matches.set([])
        this.state.set({ status: 'loading' })

        this.request = this.api
            .getPublicDetail(this.projectId)
            .subscribe({
                next: (project) => {
                    if (!project || !project.id) {
                        this.state.set({ status: 'empty' })
                    } else {
                        this.state.set({ status: 'loaded', project })
                        this.loadMatches(project)
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

    /**
     * The public detail and the match list share the same order (newest first), so
     * an opportunity is paired by position and only when the titles agree.
     */
    matchFor(index: number, opportunity: { title: string }): OpportunityMatch | null {
        const match = this.matches()[index]
        return match && match.title === opportunity.title ? match : null
    }

    private loadMatches(project: PublicProjectDetail): void {
        if (!this.isSignedIn || project.opportunities.length === 0) return

        this.matchRequest?.unsubscribe()
        this.matchRequest = this.matchApi.list(project.id).subscribe({
            next: (matches) => this.matches.set(matches),
            error: () => this.matches.set([]),
        })
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

    mapToStartup(project: PublicProjectDetail): Startup {
        return {
            id: project.id,
            name: project.name,
            logoMark: project.name.charAt(0).toUpperCase(),
            city: project.city ?? '',
            department: project.department,
            sector: project.sector.name,
            pitch: project.tagline,
            description: project.description ?? '',
            valueProposition: project.valueProposition,
            stage: project.stage,
            tags: project.tags.map((t) => t.name),
            badges: project.tags.map((t) => t.name),
            image: project.imageUrl ?? '',
            imageAlt: project.imageAlt || project.name,
            accent: '#a9e1ff',
            founders: project.members.map((m) => ({
                name: m.displayName,
                role: m.displayTitle ?? this.strings.memberFallbackRole,
                initials: m.displayName
                    .split(' ')
                    .map((n) => n.charAt(0))
                    .join('')
                    .toUpperCase()
                    .slice(0, 2),
                isFeatured: m.isFeatured,
            })),
            lookingFor: [],
            founded: '',
            teamSize: `${project.members.length} miembros`,
            traction: '',
            website: project.externalLinks.find((l) => l.type === 'WEBSITE')?.url ?? '',
            socials: project.externalLinks
                .filter((l) => l.type !== 'WEBSITE')
                .map((l) => l.url),
            publicLinks: project.externalLinks,
            recommendation: '',
            metrics: [],
            popularity: 0,
            exploration: 0,
        }
    }

    ngOnDestroy(): void {
        this.request?.unsubscribe()
        this.matchRequest?.unsubscribe()
        document.body.style.overflow = this.originalOverflow
        if (this.previousActiveElement?.isConnected) this.previousActiveElement.focus()
    }
}
