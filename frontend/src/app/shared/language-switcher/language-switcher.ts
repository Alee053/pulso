import { CommonModule } from '@angular/common'
import { Component, inject } from '@angular/core'
import type { Language } from '../../core/i18n/language'
import { LanguageService } from '../../core/i18n/language.service'
import { languageNames, languageStrings } from '../strings/language.strings'

@Component({
  selector: 'pulso-language-switcher',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="language-switcher" role="group" [attr.aria-label]="strings.label">
      <button *ngFor="let language of languages.supported"
              type="button"
              class="language-switcher__option"
              [lang]="language"
              [attr.aria-pressed]="languages.language() === language"
              (click)="select(language)">
        {{ names[language] }}
      </button>
    </div>
  `,
})
export class LanguageSwitcherComponent {
  protected readonly languages = inject(LanguageService)
  protected readonly strings = languageStrings
  protected readonly names = languageNames

  select(language: Language): void {
    this.languages.set(language)
  }
}
