import { Component, EventEmitter, Input, Output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { projectStageOptions } from '../domain/project';
import type { SectorCatalogItem } from '../domain/catalog';
import type { SearchFilters } from '../data-access/project-search-api';
import { IconComponent } from '../../../shared/icon/icon'; 
import { LanguageService } from '../../../core/i18n/language.service';
import { filtersStrings } from '../strings/filters.strings';

@Component({
  selector: 'pulso-search-filters',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="filters-container">
      <!-- Botón para mostrar/ocultar -->
      <button type="button" class="filters-toggle-btn" (click)="toggleFilters()">
        <pulso-icon name="search" [size]="16" />
        <span>{{ showFilters() ? strings.toggleHide : strings.toggleShow }}</span>
      </button>

      <!-- Panel de filtros (solo visible si showFilters es true) -->
      <div class="search-filters" *ngIf="showFilters()">
        <div class="filter-group">
          <label for="filter-sector" class="field-label">{{ strings.sector }}</label>
          <select id="filter-sector" class="field-input"
                  [value]="filters.sector || ''"
                  (change)="updateFilter('sector', $any($event.target).value)">
            <option value="">{{ strings.allSectors }}</option>
            <option *ngFor="let sector of sectors" [value]="sector.slug">{{ sector.name }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label for="filter-stage" class="field-label">{{ strings.stage }}</label>
          <select id="filter-stage" class="field-input"
                  [value]="filters.stage || ''"
                  (change)="updateFilter('stage', $any($event.target).value)">
            <option value="">{{ strings.allStages }}</option>
            <option *ngFor="let stage of stages" [value]="stage.value">{{ stage.label }}</option>
          </select>
        </div>

        <div class="filter-group">
          <label for="filter-location" class="field-label">{{ strings.location }}</label>
          <input id="filter-location" class="field-input" type="text" [placeholder]="strings.locationPlaceholder"
                 [value]="filters.location || ''"
                 (input)="updateFilter('location', $any($event.target).value)">
        </div>

        <div class="filter-group filter-group--checkbox">
          <label>
            <input type="checkbox" 
                   [checked]="filters.hasOpenOpportunities || false"
                   (change)="updateFilter('hasOpenOpportunities', $any($event.target).checked)">
            {{ strings.openOpportunities }}
          </label>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .filters-container { margin-bottom: 1.5rem; }
    .filters-toggle-btn { 
      display: flex; align-items: center; gap: 0.5rem; 
      background: transparent; border: 1px solid rgba(255,255,255,0.2); 
      padding: 0.5rem 1rem; border-radius: 6px; cursor: pointer; 
      color: inherit; font-size: 0.9rem;
    }
    .filters-toggle-btn:hover { background: rgba(255,255,255,0.05); }
    
    .search-filters {
      display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 1rem;
      padding: 1rem; background: rgba(255, 255, 255, 0.05); border-radius: 8px;
    }
    .filter-group { display: flex; flex-direction: column; min-width: 150px; }
    .filter-group--checkbox { flex-direction: row; align-items: center; margin-top: 1.5rem; }
    .filter-group--checkbox label { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; }
    .field-label { font-size: 0.85rem; margin-bottom: 0.25rem; color: var(--color-text-muted); }
    .field-input { padding: 0.5rem; border-radius: 4px; border: 1px solid #ccc; background: white; color: black; }
    .field-input option {  color: black;  background: white;}
  `]
})
export class SearchFiltersComponent {
  @Input() filters: SearchFilters = {};
  @Input() sectors: SectorCatalogItem[] = [];
  @Input() stages = projectStageOptions;
  
  @Output() filtersChanged = new EventEmitter<SearchFilters>();

  private readonly languageService = inject(LanguageService);
  readonly showFilters = signal(false);

  get strings() {
    const lang = this.languageService.language();
    return filtersStrings[lang] || filtersStrings['es'];
  }

  toggleFilters() {
    this.showFilters.update(v => !v);
  }

  updateFilter(key: keyof SearchFilters, value: string | boolean) {
    const newFilters = { ...this.filters, [key]: value === '' ? undefined : value };
    this.filtersChanged.emit(newFilters);
  }
}