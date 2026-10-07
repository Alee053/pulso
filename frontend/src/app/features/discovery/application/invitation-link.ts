import { DOCUMENT } from '@angular/common'
import { inject, Injectable, signal } from '@angular/core'
import { throwError, type Observable } from 'rxjs'
import { MemberInvitationsApi } from '../data-access/member-invitations-api'
import type { MembershipResult } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'

@Injectable({ providedIn: 'root' })
export class InvitationLink {
  private readonly api = inject(MemberInvitationsApi)
  private token: string | null = null
  readonly requested = signal(false)

  constructor() {
    const browser = inject(DOCUMENT).defaultView
    if (!browser) return
    const url = new URL(browser.location.href)
    const fragment = new URLSearchParams(url.hash.slice(1))
    if (!fragment.has('invitation')) return
    this.requested.set(true)
    this.token = fragment.get('invitation')
    fragment.delete('invitation')
    url.hash = fragment.toString()
    browser.history.replaceState(browser.history.state, '', url.pathname + url.search + url.hash)
  }

  get hasToken(): boolean { return !!this.token?.trim() }

  accept(): Observable<MembershipResult> {
    const token = this.token
    if (!token?.trim()) {
      return throwError(() => ({ message: invitationStrings.invalid, retryable: false }))
    }
    return this.api.accept(token)
  }

  clearToken(): void { this.token = null }

  dismiss(): void {
    this.clearToken()
    this.requested.set(false)
  }
}
