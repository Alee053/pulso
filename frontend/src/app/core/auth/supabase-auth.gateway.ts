import { Injectable } from '@angular/core'
import type { SupabaseClient } from '@supabase/supabase-js'
import { appConfig } from '../config/app-config'

@Injectable({ providedIn: 'root' })
export class SupabaseAuthGateway {
  private clientPromise: Promise<SupabaseClient | null> | null = null

  get isConfigured(): boolean {
    return Boolean(appConfig.supabaseUrl && appConfig.supabasePublishableKey)
  }

  async getAccessToken(): Promise<string | null> {
    const client = await this.getClient()
    if (!client) {
      return null
    }

    const { data, error } = await client.auth.getSession()
    if (error) {
      throw error
    }

    return data.session?.access_token ?? null
  }

  private getClient(): Promise<SupabaseClient | null> {
    if (!this.isConfigured) {
      return Promise.resolve(null)
    }

    this.clientPromise ??= import('@supabase/supabase-js').then(
      ({ createClient }) =>
        createClient(appConfig.supabaseUrl, appConfig.supabasePublishableKey),
    )

    return this.clientPromise
  }
}
