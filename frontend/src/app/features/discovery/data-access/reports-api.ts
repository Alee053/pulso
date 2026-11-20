import { HttpClient } from '@angular/common/http'
import { inject, Injectable } from '@angular/core'
import { appConfig } from '../../../core/config/app-config'

export type ReportTarget =
  | { targetProfileId: string; targetProjectId?: never; targetMessageId?: never }
  | { targetProjectId: string; targetProfileId?: never; targetMessageId?: never }
  | { targetMessageId: string; targetProfileId?: never; targetProjectId?: never }

export interface ReportReceipt {
  id: string
  status: 'OPEN'
  createdAt: string
}

@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient)

  create(target: ReportTarget, reason: string, description: string) {
    return this.http.post<ReportReceipt>(`${appConfig.apiBaseUrl}/api/v1/reports`, {
      ...target,
      reason,
      description: description.trim() || undefined,
    })
  }
}
