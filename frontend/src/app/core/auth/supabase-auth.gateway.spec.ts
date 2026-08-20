import { describe, expect, it } from 'vitest'
import { SupabaseAuthGateway } from './supabase-auth.gateway'

describe('SupabaseAuthGateway', () => {
  it('stays inactive until public Supabase configuration is provided', async () => {
    const gateway = new SupabaseAuthGateway()

    expect(gateway.isConfigured).toBe(false)
    await expect(gateway.getAccessToken()).resolves.toBeNull()
  })
})
