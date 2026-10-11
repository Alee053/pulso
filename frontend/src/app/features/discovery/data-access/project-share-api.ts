import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'

export type ShareOrigin = 'FEED' | 'PROJECT_DETAIL'

export interface ProjectShareResponse {
  projectId: string
  slug: string
  url: string
}

@Injectable({ providedIn: 'root' })
export class ProjectShareApi {
  private readonly http = inject(HttpClient)

  generate(projectRef: string): Observable<ProjectShareResponse> {
    return this.http.get<ProjectShareResponse>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${projectRef}/share`,
    )
  }

  register(projectRef: string, origin: ShareOrigin): Observable<ProjectShareResponse> {
    return this.http.post<ProjectShareResponse>(
      `${appConfig.apiBaseUrl}/api/v1/projects/${projectRef}/share`,
      { origin },
    )
  }

  async copyToClipboard(url: string): Promise<boolean> {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(url)
        return true
      }
      return this.fallbackCopy(url)
    } catch {
      return false
    }
  }

  async shareNative(url: string, title: string): Promise<boolean> {
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title, url })
        return true
      } catch {
        return false
      }
    }
    return false
  }

  private fallbackCopy(text: string): boolean {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      return true
    } catch {
      return false
    } finally {
      document.body.removeChild(textarea)
    }
  }
}