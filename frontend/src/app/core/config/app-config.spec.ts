import { describe, expect, it } from 'vitest'
import { appConfig } from './app-config'

describe('appConfig', () => {
  it('uses local public defaults without server credentials', () => {
    expect(appConfig.apiBaseUrl).toBe('http://localhost:8080')
    expect(appConfig.supabaseUrl).toBe('http://127.0.0.1:54321')
    expect(appConfig.supabasePublishableKey).toBe('')
  })
})
