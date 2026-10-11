import { CommonModule } from '@angular/common'
import { Component, Input } from '@angular/core'

export type IconName =
    | 'arrow-down'
    | 'arrow-left'
    | 'arrow-right'
    | 'arrow-up'
    | 'arrow-up-right'
    | 'at-sign'
    | 'bookmark'
    | 'calendar-days'
    | 'check'
    | 'chevron-left'
    | 'chevron-right'
    | 'circle-user'
    | 'compass'
    | 'external-link'
    | 'handshake'
    | 'heart'
    | 'keyboard'
    | 'map-pin'
    | 'message-circle'
    | 'rotate-ccw'
    | 'settings'
    | 'share'
    | 'sparkles'
    | 'trending'
    | 'trending-up'
    | 'user-round'
    | 'users-round'
    | 'x'

@Component({
    selector: 'pulso-icon',
    standalone: true,
    imports: [CommonModule],
    template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path
        *ngFor="let path of pathData"
        [attr.d]="path"
        [attr.fill]="filled ? 'currentColor' : 'none'"
      />
    </svg>
  `,
})
export class IconComponent {
    @Input() name: IconName = 'sparkles'
    @Input() size = 18
    @Input() strokeWidth = 1.8
    @Input() filled = false

    readonly paths: Record<IconName, string[]> = {
        'arrow-down': ['M12 5v14', 'm19 12-7 7-7-7'],
        'arrow-left': ['M19 12H5', 'm12 19-7-7 7-7'],
        'arrow-right': ['M5 12h14', 'm12 5 7 7-7 7'],
        'arrow-up': ['M12 19V5', 'm5 12 7-7 7 7'],
        'arrow-up-right': ['M7 17 17 7', 'M7 7h10v10'],
        'at-sign': [
            'M16 8a6 6 0 1 0 2.4 4.8',
            'M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3 7.2',
        ],
        bookmark: ['m6 4 6-2 6 2v16l-6-3-6 3Z'],
        'calendar-days': [
            'M8 2v4',
            'M16 2v4',
            'M3 10h18',
            'M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z',
        ],
        check: ['m5 12 4 4L19 6'],
        'chevron-left': ['m15 18-6-6 6-6'],
        'chevron-right': ['m9 18 6-6-6-6'],
        'circle-user': [
            'M18 20a6 6 0 0 0-12 0',
            'M12 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
            'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
        ],
        compass: [
            'm16.24 7.76-1.2 4.83a2 2 0 0 1-1.45 1.45l-4.83 1.2 1.2-4.83a2 2 0 0 1 1.45-1.45l4.83-1.2Z',
            'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
        ],
        'external-link': ['M14 3h7v7', 'M10 14 21 3', 'M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5'],
        handshake: [
            'm11 17 2 2a2 2 0 0 0 3 0l5-5',
            'm4 14-1.5-1.5a2 2 0 0 1 0-3L7 5h5l2 2h3l4 4',
            'm7 17-2 2a2 2 0 1 1-3-3l5-5',
            'm14 7 1.5-1.5a2 2 0 0 1 3 0l2 2',
        ],
        heart: ['M20.8 8.6a5.5 5.5 0 0 0-9-3.5L12 5l.2.1a5.5 5.5 0 0 0-9 3.5c0 3.4 3.1 6 8.8 10.4 5.7-4.4 8.8-7 8.8-10.4Z'],
        keyboard: [
            'M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z',
            'M6 9h.01',
            'M10 9h.01',
            'M14 9h.01',
            'M18 9h.01',
            'M6 13h.01',
            'M10 13h8',
        ],
        'map-pin': [
            'M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z',
            'M12 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z',
        ],
        'message-circle': [
            'M21 11.5a8.4 8.4 0 0 1-9 8.5 9.5 9.5 0 0 1-4-.9L3 21l1.9-4.8A8.5 8.5 0 1 1 21 11.5Z',
        ],
        'rotate-ccw': ['M3 12a9 9 0 1 0 3-6.7L3 8', 'M3 3v5h5'],
        settings: [
            'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z',
            'M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-1.7 1.7-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5v.2h-2.4v-.2a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1L8 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H6.7v-2.4h.2a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.9L8 8.6l1.7-1.7.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.5v-.2h2.4v.2a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1 1.7 1.7-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.5 1h.2v2.4h-.2a1.7 1.7 0 0 0-1.5 1Z',
        ],
        share: [
            'M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8',
            'm16 6-4-4-4 4',
            'M12 2v13',
        ],
        sparkles: [
            'm12 2 1.4 5.6L19 9l-5.6 1.4L12 16l-1.4-5.6L5 9l5.6-1.4L12 2Z',
            'm19 14 .7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7L19 14Z',
        ],
        trending: ['m3 17 6-6 4 4 8-8', 'M15 7h6v6'],
        'trending-up': ['m3 17 6-6 4 4 8-8', 'M15 7h6v6'],
        'user-round': [
            'M20 21a8 8 0 0 0-16 0',
            'M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
        ],
        'users-round': [
            'M18 21a6 6 0 0 0-12 0',
            'M12 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
            'M22 21a5 5 0 0 0-3.5-4.8',
            'M18 5.1a3 3 0 0 1 0 5.8',
        ],
        x: ['M18 6 6 18', 'm6 6 12 12'],
    }

    get pathData(): string[] {
        return this.paths[this.name]
    }
}