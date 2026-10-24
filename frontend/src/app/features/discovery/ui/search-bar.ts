import { Component, EventEmitter, Input, Output } from '@angular/core'
import { IconComponent } from '../../../shared/icon/icon'
import { searchStrings } from '../strings/feed.strings'

@Component({
    selector: 'pulso-search-bar',
    standalone: true,
    imports: [IconComponent],
    template: `
        <div class="search-bar">
            <label class="field-label" for="project-search">{{ strings.label }}</label>
            <div class="search-bar__control">
                <pulso-icon name="search" [size]="18" class="search-bar__icon" />
                <input id="project-search"
                       class="field-input search-bar__input"
                       type="search"
                       autocomplete="off"
                       maxlength="100"
                       [value]="query"
                       [placeholder]="strings.placeholder"
                       (input)="searched.emit($any($event.target).value)" />
            </div>
        </div>
    `,
})
export class SearchBarComponent {
    @Input() query = ''
    @Output() searched = new EventEmitter<string>()

    readonly strings = searchStrings
}
