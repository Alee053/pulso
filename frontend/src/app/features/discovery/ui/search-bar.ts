import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { FormsModule } from '@angular/forms'
import { IconComponent } from '../../../shared/icon/icon'

@Component({
    selector: 'pulso-search-bar',
    standalone: true,
    imports: [CommonModule, FormsModule, IconComponent],
    template: `
    <div class="relative w-full max-w-md">
      <div class="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
        <pulso-icon name="search" [size]="18" class="text-gray-400" />
      </div>
      <input
        type="text"
        name="search"
        [value]="query"
        (input)="searched.emit($any($event.target).value)"
        class="w-full pl-10 pr-4 py-2 bg-white/5 border border-white/10 rounded-xl text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[#a9e1ff] focus:border-transparent transition"
        placeholder="Buscar proyectos, sectores o ciudades..."
      />
    </div>
  `,
})
export class SearchBarComponent {
    @Input() query = ''
    @Output() searched = new EventEmitter<string>()
}