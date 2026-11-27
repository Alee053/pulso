import { DOCUMENT } from '@angular/common'
import { Component, Injectable, inject, signal } from '@angular/core'
import { IconComponent } from '../icon/icon'
import { themeStrings } from '../strings/theme.strings'

type Theme = 'system' | 'light' | 'dark'
const storageKey = 'pulso.theme'

function themeOf(value: string | null): Theme {
  return value === 'light' || value === 'dark' ? value : 'system'
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT)
  private readonly current = signal<Theme>('system')
  readonly preference = this.current.asReadonly()

  constructor() {
    let stored: string | null = null
    try {
      stored = this.document.defaultView?.localStorage.getItem(storageKey) ?? null
    } catch {
      // A browser can disable storage while still supporting color schemes.
    }
    this.apply(themeOf(stored))
  }

  select(value: string): void {
    const theme = themeOf(value)
    this.apply(theme)
    try {
      this.document.defaultView?.localStorage.setItem(storageKey, theme)
    } catch {
      // Keep the current choice for this session when persistence is unavailable.
    }
  }

  private apply(theme: Theme): void {
    this.current.set(theme)
    this.document.documentElement.dataset['theme'] = theme
  }
}

@Component({
  selector: 'pulso-theme-switcher',
  imports: [IconComponent],
  template: `
    <label class="theme-switcher" [title]="strings.label">
      <pulso-icon [name]="theme.preference() === 'system' ? 'monitor' : theme.preference() === 'light' ? 'sun' : 'moon'" [size]="17" />
      <span class="theme-switcher__value" aria-hidden="true">{{ strings[theme.preference()] }}</span>
      <select #choice [attr.aria-label]="strings.label" [value]="theme.preference()" (change)="theme.select(choice.value)">
        <option value="system">{{ strings.system }}</option>
        <option value="light">{{ strings.light }}</option>
        <option value="dark">{{ strings.dark }}</option>
      </select>
    </label>
  `,
})
export class ThemeSwitcherComponent {
  protected readonly theme = inject(ThemeService)
  protected readonly strings = themeStrings
}
