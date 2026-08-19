export interface AppConfig {
  apiBaseUrl: string
  supabaseUrl: string
  supabasePublishableKey: string
}

// Keep public runtime configuration in one place. The publishable key is safe
// for browser use; service-role keys must never be placed in this file.
export const appConfig: AppConfig = {
  apiBaseUrl: 'http://localhost:8080',
  supabaseUrl: '',
  supabasePublishableKey: '',
}
