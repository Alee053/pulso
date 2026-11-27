import { Component, EventEmitter, Input, Output, signal } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { projectStageOptions } from '../domain/project'
import type { SectorCatalogItem, TagCatalogItem } from '../domain/catalog'
import { searchMaxTags, type SearchFilters } from '../data-access/project-search-api'
import { filtersStrings } from '../strings/filters.strings'

@Component({
  selector: 'pulso-search-filters',
  imports: [IconComponent],
  template: `
    <div class="filter-cluster">
      <div class="filter-actions">
        <button #toggleButton type="button" class="contact-button" aria-controls="project-search-filters"
                [attr.aria-label]="expanded() ? strings.toggleHide : strings.toggleShow"
                [attr.aria-expanded]="expanded()" (click)="expanded.update(toggle)">
          <pulso-icon name="sliders" [size]="16" />
          <span class="filter-label-full">{{ expanded() ? strings.toggleHide : strings.toggleShow }}</span>
          <span class="filter-label-short" aria-hidden="true">{{ strings.compact }}</span>
        </button>
        @if (searchActive) {
          <button type="button" class="contact-button" (click)="clear(toggleButton)">{{ strings.clear }}</button>
        }
      </div>

      <div id="project-search-filters" [hidden]="!expanded()">
        <div class="panel mt-3 grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-3">
          <div class="min-w-0">
            <label for="filter-sector" class="field-label">{{ strings.sector }}</label>
            <select #sector id="filter-sector" class="field-input" [value]="filters.sector ?? ''"
                    (change)="filtersChanged.emit({ ...filters, sector: sector.value })">
              <option value="">{{ strings.allSectors }}</option>
              @for (item of sectors; track item.id) {
                <option [value]="item.slug">{{ item.name }}</option>
              }
            </select>
          </div>
          <div class="min-w-0">
            <label for="filter-stage" class="field-label">{{ strings.stage }}</label>
            <select #stage id="filter-stage" class="field-input" [value]="filters.stage ?? ''"
                    (change)="filtersChanged.emit({ ...filters, stage: stage.value })">
              <option value="">{{ strings.allStages }}</option>
              @for (item of stages; track item.value) {
                <option [value]="item.value">{{ item.label }}</option>
              }
            </select>
          </div>
          <div class="min-w-0">
            <label for="filter-location" class="field-label">{{ strings.location }}</label>
            <input #location id="filter-location" class="field-input" type="text" maxlength="100"
                   [value]="filters.location ?? ''" [placeholder]="strings.locationPlaceholder"
                   (input)="filtersChanged.emit({ ...filters, location: location.value })">
          </div>
          @if (tags.length) {
            <fieldset class="min-w-0 sm:col-span-full">
              <legend class="field-label">{{ strings.tags }}</legend>
              <div class="flex flex-wrap gap-x-4 gap-y-1">
                @for (tag of tags; track tag.id) {
                  <label class="inline-flex min-h-11 items-center gap-2">
                    <input #selected type="checkbox" class="accent-[var(--color-accent)]"
                           [checked]="filters.tags?.includes(tag.slug) ?? false"
                           [disabled]="(filters.tags?.length ?? 0) >= maxTags && !filters.tags?.includes(tag.slug)"
                           (change)="toggleTag(tag.slug, selected.checked)">
                    <span class="[overflow-wrap:anywhere]">{{ tag.name }}</span>
                  </label>
                }
              </div>
              @if ((filters.tags?.length ?? 0) >= maxTags) {
                <p class="field-hint" role="status">{{ strings.tagLimit(maxTags) }}</p>
              }
            </fieldset>
          }
          <label class="inline-flex min-h-11 items-center gap-2 sm:col-span-full">
            <input #open type="checkbox" class="accent-[var(--color-accent)]"
                   [checked]="filters.hasOpenOpportunities ?? false"
                   (change)="filtersChanged.emit({ ...filters, hasOpenOpportunities: open.checked })">
            {{ strings.openOpportunities }}
          </label>
        </div>
      </div>
    </div>
  `,
})
export class SearchFiltersComponent {
  @Input() filters: SearchFilters = {}
  @Input() searchActive = false
  @Input() sectors: SectorCatalogItem[] = []
  @Input() tags: TagCatalogItem[] = []
  @Output() filtersChanged = new EventEmitter<SearchFilters>()
  @Output() cleared = new EventEmitter<void>()

  protected readonly stages = projectStageOptions
  protected readonly strings = filtersStrings
  protected readonly expanded = signal(false)
  protected readonly toggle = (expanded: boolean): boolean => !expanded
  protected readonly maxTags = searchMaxTags

  protected clear(toggle: HTMLButtonElement): void {
    toggle.focus()
    this.cleared.emit()
  }

  protected toggleTag(slug: string, checked: boolean): void {
    const tags = this.filters.tags ?? []
    if (checked && tags.length >= this.maxTags) return
    this.filtersChanged.emit({
      ...this.filters,
      tags: checked ? [...tags, slug] : tags.filter(tag => tag !== slug),
    })
  }
}
