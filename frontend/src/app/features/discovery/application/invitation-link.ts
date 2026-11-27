import { DOCUMENT } from '@angular/common'
import { DestroyRef, inject, Injectable, signal } from '@angular/core'
import { throwError, type Observable } from 'rxjs'
import { MemberInvitationsApi } from '../data-access/member-invitations-api'
import type { MembershipResult } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'

@Injectable({ providedIn: 'root' })
export class InvitationLink {
  private readonly api = inject(MemberInvitationsApi)
  private token: string | null = null
  readonly requested = signal(false)
  readonly arrival = signal(0)

  constructor() {
    const browser = inject(DOCUMENT).defaultView
    if (!browser) return
    const readFragment = () => this.readFragment(browser)
    readFragment()
    browser.addEventListener('hashchange', readFragment)
    inject(DestroyRef).onDestroy(() => browser.removeEventListener('hashchange', readFragment))
  }

  private readFragment(browser: Window): void {
    const url = new URL(browser.location.href)
    const fragment = new URLSearchParams(url.hash.slice(1))
    const tokens = [...fragment.getAll('invitation'), ...fragment.getAll('invite')]
    if (tokens.length === 0) return
    this.token = tokens.length === 1 ? tokens[0] : null
    fragment.delete('invitation')
    fragment.delete('invite')
    url.hash = fragment.toString()
    browser.history.replaceState(browser.history.state, '', url.pathname + url.search + url.hash)
    this.requested.set(true)
    this.arrival.update(value => value + 1)
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
