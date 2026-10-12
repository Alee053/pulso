import { HttpClient, HttpErrorResponse } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { catchError, map, Observable, throwError } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'
import { projectImageUrl } from './project-image'
import type {
    PublicExternalLink,
    PublicMember,
    PublicProjectDetail,
    PublicSector,
    PublicTag,
} from '../domain/startup'

interface PublicProjectResponseDto {
    data: PublicProjectDetail
}

export interface PublicProjectError {
    status: number
    message: string
    retryable: boolean
}

@Injectable({ providedIn: 'root' })
export class PublicProjectApi {
    private readonly http = inject(HttpClient)
    private readonly baseUrl = `${appConfig.apiBaseUrl}/api/v1/projects`

    getPublicDetail(reference: string): Observable<PublicProjectDetail> {
        if (!reference || reference.trim() === '') {
            return throwError(() => ({ status: 400, message: 'Referencia de proyecto inválida', retryable: false }))
        }

        return this.http
            .get<PublicProjectResponseDto>(
                `${this.baseUrl}/${encodeURIComponent(reference.trim())}`
            )
            .pipe(
                map((response) => this.mapToDomain(response.data)),
                catchError((error) => this.handleError(error))
            )
    }

    private mapToDomain(raw: PublicProjectDetail): PublicProjectDetail {
        if (!raw) throw new Error('Respuesta vacía del servidor')

        return {
            id: raw.id ?? '',
            slug: this.sanitizeText(raw.slug) ?? '',
            name: this.sanitizeText(raw.name) ?? '',
            tagline: this.sanitizeText(raw.tagline) ?? '',
            description: this.sanitizeText(raw.description),
            valueProposition: this.sanitizeText(raw.valueProposition),
            sector: this.mapSector(raw.sector),
            stage: this.sanitizeText(raw.stage) ?? '',
            city: this.sanitizeText(raw.city),
            department: this.sanitizeText(raw.department),
            tags: this.mapTags(raw.tags),
            externalLinks: this.mapExternalLinks(raw.externalLinks),
            members: this.mapMembers(raw.members),
            imageUrl: projectImageUrl(raw.imageUrl),
            imageAlt: this.sanitizeText(raw.imageAlt),
        }
    }

    private mapSector(raw: PublicSector): PublicSector {
        return {
            id: raw?.id ?? '',
            slug: this.sanitizeText(raw?.slug) ?? '',
            name: this.sanitizeText(raw?.name) ?? '',
        }
    }

    private sanitizeText(value: unknown): string | null {
        if (value === null || value === undefined) return null
        const str = String(value).trim()
        return str === '' ? null : str
    }

    private sanitizeUrl(url: unknown): string | null {
        if (!url) return null
        const urlString = String(url).trim()
        try {
            const parsed = new URL(urlString)
            if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
                return urlString
            }
        } catch { }
        return null
    }

    private mapTags(raw: PublicTag[]): PublicTag[] {
        if (!Array.isArray(raw)) return []
        return raw
            .map((tag) => ({
                id: tag?.id ?? '',
                slug: this.sanitizeText(tag?.slug) ?? '',
                name: this.sanitizeText(tag?.name) ?? '',
            }))
            .filter((tag) => tag.id !== '' && tag.name !== '')
    }

    private mapExternalLinks(raw: PublicExternalLink[]): PublicExternalLink[] {
        if (!Array.isArray(raw)) return []
        return raw
            .map((link) => ({
                type: this.sanitizeText(link?.type) ?? 'OTHER',
                url: this.sanitizeUrl(link?.url) ?? '',
                label: this.sanitizeText(link?.label) ?? '',
            }))
            .filter((link) => link.url !== '')
    }

    private mapMembers(raw: PublicMember[]): PublicMember[] {
        if (!Array.isArray(raw)) return []
        return raw.map((member) => ({
            profileId: member?.profileId ?? '',
            displayName: this.sanitizeText(member?.displayName) ?? '',
            avatarStoragePath: this.sanitizeText(member?.avatarStoragePath),
            displayTitle: this.sanitizeText(member?.displayTitle),
            isFeatured: Boolean(member?.isFeatured),
        }))
    }

    private handleError(error: HttpErrorResponse): Observable<never> {
        let message = 'Ocurrió un error al cargar el proyecto'
        let retryable = true

        if (error.status === 404) {
            message = 'El proyecto no está disponible'
            retryable = false
        } else if (error.status === 400) {
            message = 'Referencia de proyecto inválida'
            retryable = false
        } else if (error.status === 403) {
            message = 'No tienes permiso para ver este proyecto'
            retryable = false
        } else if (error.status === 0) {
            message = 'No se pudo conectar con el servidor'
            retryable = true
        } else if (error.status >= 500) {
            message = 'Error del servidor. Intenta de nuevo más tarde'
            retryable = true
        }

        return throwError(() => ({ message, retryable, status: error.status }))
    }
}
