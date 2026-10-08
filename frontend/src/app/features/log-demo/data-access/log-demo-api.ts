import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import type { Observable } from 'rxjs'
import { appConfig } from '../../../core/config/app-config'

@Injectable({ providedIn: 'root' })
export class LogDemoApi {
  private readonly http = inject(HttpClient)

  triggerDataStoreFailure(): Observable<void> {
    return this.http.post<void>(
      appConfig.apiBaseUrl + '/api/dev/log-demo/data-store-failure',
      null,
    )
  }
}
