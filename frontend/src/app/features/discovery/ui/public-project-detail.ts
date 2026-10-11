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
import { PublicProjectApi } from '../data-access/public-project-api'
import type {
    PublicProjectDetail,
    PublicProjectState,
    Startup,
} from '../domain/startup'
import { IconComponent } from '../../../shared/icon/icon'
import { detailStrings } from '../strings/detail.strings'
import { ShareButtonComponent } from './share-button'

@Component({
    selector: 'pulso-public-project-detail',
    standalone: true,
    imports: [CommonModule, IconComponent, ShareButtonComponent],
    templateUrl: './public-project-detail.html',
})
export class PublicProjectDetailComponent implements OnChanges, OnInit, AfterViewChecked, OnDestroy {
    readonly strings = detailStrings
    @Input() projectId!: string
    @Input() recommendation = ''
    @Output() closed = new EventEmitter<void>()

    private readonly api = inject(PublicProjectApi)
    private request?: Subscription
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef)
    private previousActiveElement: HTMLElement | null = null
    private originalOverflow = ''

    readonly state = signal<PublicProjectState>({ status: 'loading' })

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
                    let message = 'Ocurrió un error al cargar el proyecto'
                    let retryable = true

                    if (err.status === 404) {
                        this.state.set({ status: 'empty' })
                        return
                    } else if (err.status === 400) {
                        message = 'Referencia de proyecto inválida'
                        retryable = false
                    } else if (err.status === 403) {
                        message = 'No tienes permiso para ver este proyecto'
                        retryable = false
                    } else if (err.status === 0) {
                        message = 'No se pudo conectar con el servidor'
                        retryable = true
                    } else if (err.status >= 500) {
                        message = 'Error del servidor. Intenta de nuevo más tarde'
                        retryable = true
                    }

                    this.state.set({ status: 'error', message, retryable })
                },
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
            image: '',
            imageAlt: project.name,
            accent: '#a9e1ff',
            founders: project.members.map((m) => ({
                name: m.displayName,
                role: m.displayTitle ?? 'Miembro',
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
        document.body.style.overflow = this.originalOverflow
        if (this.previousActiveElement?.isConnected) this.previousActiveElement.focus()
    }
}