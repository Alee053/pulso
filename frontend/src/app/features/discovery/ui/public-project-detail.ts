import { CommonModule } from '@angular/common'
import {
    Component,
    Input,
    OnInit,
    OnDestroy,
    inject,
    signal,
    Output,
    EventEmitter,
} from '@angular/core'
import { Subject, takeUntil } from 'rxjs'
import { PublicProjectApi } from '../data-access/public-project-api'
import type {
    PublicProjectDetail,
    PublicProjectState,
    Startup,
} from '../domain/startup'
import { IconComponent } from '../../../shared/icon/icon'
import { detailStrings } from '../strings/detail.strings'
import { StartupDetailModalComponent } from './startup-detail-modal'

@Component({
    selector: 'pulso-public-project-detail',
    standalone: true,
    imports: [CommonModule, IconComponent, StartupDetailModalComponent],
    templateUrl: './public-project-detail.html',
})
export class PublicProjectDetailComponent implements OnInit, OnDestroy {
    readonly strings = detailStrings
    @Input() projectId!: string
    @Input() recommendation = ''
    @Output() closed = new EventEmitter<void>()

    private readonly api = inject(PublicProjectApi)
    private readonly destroy$ = new Subject<void>()

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
        this.loadProject()
    }

    loadProject(): void {
        this.state.set({ status: 'loading' })

        this.api
            .getPublicDetail(this.projectId)
            .pipe(takeUntil(this.destroy$))
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
                        message = 'El proyecto no está disponible'
                        retryable = false
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
            sector: project.sector.name,
            pitch: project.tagline,
            description: project.description ?? '',
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
            })),
            lookingFor: [],
            founded: '',
            teamSize: `${project.members.length} miembros`,
            traction: '',
            website: project.externalLinks.find((l) => l.type === 'WEBSITE')?.url ?? '',
            socials: project.externalLinks
                .filter((l) => l.type !== 'WEBSITE')
                .map((l) => l.url),
            recommendation: '',
            metrics: [],
            popularity: 0,
            exploration: 0,
        }
    }

    ngOnDestroy(): void {
        this.destroy$.next()
        this.destroy$.complete()
    }
}