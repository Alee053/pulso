export interface AppConfig {
  apiBaseUrl: string
  supabaseUrl: string
  supabasePublishableKey: string
}

declare const __PULSO_API_BASE_URL__: string
declare const __PULSO_SUPABASE_URL__: string
declare const __PULSO_SUPABASE_PUBLISHABLE_KEY__: string

const apiBaseUrl = typeof __PULSO_API_BASE_URL__ === 'undefined'
  ? 'http://localhost:8080'
  : __PULSO_API_BASE_URL__
const supabaseUrl = typeof __PULSO_SUPABASE_URL__ === 'undefined'
  ? 'http://127.0.0.1:54321'
  : __PULSO_SUPABASE_URL__
const supabasePublishableKey = typeof __PULSO_SUPABASE_PUBLISHABLE_KEY__ === 'undefined'
  ? ''
  : __PULSO_SUPABASE_PUBLISHABLE_KEY__

// Keep public build configuration in one place. The publishable key is safe for
// browser use; service-role keys must never be placed in this file.
export const appConfig: AppConfig = {
  apiBaseUrl,
  supabaseUrl,
  supabasePublishableKey,
}
