import { Injectable, signal } from '@angular/core'
import type { Session, SupabaseClient } from '@supabase/supabase-js'
import { appConfig } from '../config/app-config'

export interface AuthenticatedUser {
  id: string
  email: string | null
}

export interface AuthActionResult {
  user: AuthenticatedUser | null
  requiresEmailConfirmation: boolean
}

@Injectable({ providedIn: 'root' })
export class SupabaseAuthGateway {
  private clientPromise: Promise<SupabaseClient | null> | null = null
  private initializationPromise: Promise<void> | null = null
  private authSubscription: { unsubscribe: () => void } | null = null
  private accessToken: string | null = null

  readonly session = signal<AuthenticatedUser | null>(null)
  readonly isLoading = signal(true)

  get isConfigured(): boolean {
    return Boolean(appConfig.supabaseUrl && appConfig.supabasePublishableKey)
  }

  async initialize(): Promise<void> {
    if (!this.isConfigured) {
      this.isLoading.set(false)
      return
    }

    this.initializationPromise ??= this.loadSession()
      .catch((error: unknown) => {
        this.clearSession()
        throw error
      })
      .finally(() => this.isLoading.set(false))

    return this.initializationPromise
  }

  async getAccessToken(): Promise<string | null> {
    await this.initialize()
    return this.accessToken
  }

  async signUp(email: string, password: string): Promise<AuthActionResult> {
    const client = await this.requireClient()
    const { data, error } = await client.auth.signUp({ email, password })
    if (error) {
      throw error
    }

    this.setSession(data.session)
    return {
      user: data.user ? toAuthenticatedUser(data.user) : null,
      requiresEmailConfirmation: data.session === null,
    }
  }

  async signIn(email: string, password: string): Promise<AuthActionResult> {
    const client = await this.requireClient()
    const { data, error } = await client.auth.signInWithPassword({ email, password })
    if (error) {
      throw error
    }

    this.setSession(data.session)
    return {
      user: data.user ? toAuthenticatedUser(data.user) : null,
      requiresEmailConfirmation: false,
    }
  }

  async signOut(): Promise<void> {
    const client = await this.requireClient()
    const { error } = await client.auth.signOut()
    if (error) {
      throw error
    }

    this.clearSession()
  }

  private async loadSession(): Promise<void> {
    const client = await this.requireClient()
    const { data, error } = await client.auth.getSession()
    if (error) {
      throw error
    }

    this.setSession(data.session)
    this.authSubscription?.unsubscribe()
    this.authSubscription = client.auth.onAuthStateChange(
      (_event, session) => this.setSession(session),
    ).data.subscription
  }

  private async requireClient(): Promise<SupabaseClient> {
    const client = await this.getClient()
    if (!client) {
      throw new Error('Supabase Auth is not configured.')
    }
    return client
  }

  private setSession(session: Session | null): void {
    this.accessToken = session?.access_token ?? null
    this.session.set(session?.user ? toAuthenticatedUser(session.user) : null)
  }

  private clearSession(): void {
    this.accessToken = null
    this.session.set(null)
  }

  private getClient(): Promise<SupabaseClient | null> {
    if (!this.isConfigured) {
      return Promise.resolve(null)
    }

    this.clientPromise ??= import('@supabase/supabase-js').then(
      ({ createClient }) =>
        createClient(appConfig.supabaseUrl, appConfig.supabasePublishableKey, {
          auth: {
            persistSession: true,
            autoRefreshToken: true,
            detectSessionInUrl: false,
          },
        }),
    )

    return this.clientPromise
  }
}

function toAuthenticatedUser(user: Session['user']): AuthenticatedUser {
  return {
    id: user.id,
    email: user.email ?? null,
  }
}
