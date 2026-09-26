import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import type { Startup } from '../domain/startup'
import { savedStrings } from '../strings/saved.strings'
import { StartupThumbComponent } from './startup-thumb'

@Component({
  selector: 'pulso-saved-view',
  standalone: true,
  imports: [CommonModule, IconComponent, StartupThumbComponent],
  templateUrl: './saved-view.html',
})
export class SavedViewComponent {
  readonly strings = savedStrings
  @Input() savedStartups: Startup[] = []
  @Input() interestedStartups: Startup[] = []
  @Output() startupSelected = new EventEmitter<Startup>()
}
