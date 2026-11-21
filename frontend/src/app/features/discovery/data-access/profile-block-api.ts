import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { type Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'

@Injectable({ providedIn: 'root' })
export class ProfileBlockApi {
    private readonly http = inject(HttpClient)

    blockProfile(targetId: string): Observable<void> {
        return this.http.post<void>(
            `${appConfig.apiBaseUrl}/api/v1/profiles/${targetId}/block`,
            {},
        )
    }

    unblockProfile(targetId: string): Observable<void> {
        return this.http.delete<void>(
            `${appConfig.apiBaseUrl}/api/v1/profiles/${targetId}/block`,
        )
    }
}