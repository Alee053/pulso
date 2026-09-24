import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import type { Startup } from '../domain/startup'
import { StartupThumbComponent } from './startup-thumb'

@Component({
  selector: 'pulso-saved-view',
  standalone: true,
  imports: [CommonModule, IconComponent, StartupThumbComponent],
  templateUrl: './saved-view.html',
})
export class SavedViewComponent {
  @Input() savedStartups: Startup[] = []
  @Input() interestedStartups: Startup[] = []
  @Output() startupSelected = new EventEmitter<Startup>()

  private selectedCategory = ''

  get categories(): string[] {
    return [...new Set(
      this.savedStartups
        .map((startup) => startup.sector.trim())
        .filter(Boolean),
    )].sort((first, second) => first.localeCompare(second, 'es'))
  }

  get activeCategory(): string {
    return this.categories.includes(this.selectedCategory)
      ? this.selectedCategory
      : ''
  }

  get filteredSavedStartups(): Startup[] {
    const category = this.activeCategory

    if (!category) {
      return this.savedStartups
    }

    return this.savedStartups.filter(
      (startup) => startup.sector.trim() === category,
    )
  }

  handleCategoryChange(event: Event): void {
    this.selectedCategory = (event.target as HTMLSelectElement).value
  }
}
