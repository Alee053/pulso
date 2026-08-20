import { HttpInterceptorFn } from '@angular/common/http'
import { inject } from '@angular/core'
import { from, of } from 'rxjs'
import { catchError, switchMap } from 'rxjs/operators'
import { appConfig } from '../config/app-config'
import { SupabaseAuthGateway } from './supabase-auth.gateway'

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isBackendRequest(request.url)) {
    return next(request)
  }

  const auth = inject(SupabaseAuthGateway)

  return from(auth.getAccessToken()).pipe(
    // An unavailable session must not block public API requests. Protected
    // requests continue without credentials and receive the normal 401.
    catchError(() => of(null)),
    switchMap((token) => {
      if (!token) {
        return next(request)
      }

      return next(
        request.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        }),
      )
    }),
  )
}

function isBackendRequest(requestUrl: string): boolean {
  if (!appConfig.apiBaseUrl) {
    return false
  }

  try {
    const baseUrl = new URL(appConfig.apiBaseUrl, 'http://localhost')
    const targetUrl = new URL(requestUrl, baseUrl)
    const basePath = baseUrl.pathname.replace(/\/$/, '')

    return targetUrl.origin === baseUrl.origin
      && (basePath === ''
        || targetUrl.pathname === basePath
        || targetUrl.pathname.startsWith(`${basePath}/`))
  } catch {
    return false
  }
}
