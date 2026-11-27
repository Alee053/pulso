import { Pipe, type PipeTransform } from '@angular/core'
import type { Language } from '../../../core/i18n/language'

const formats = {
  date: { dateStyle: 'medium' },
  time: { timeStyle: 'short' },
  dateTime: { dateStyle: 'medium', timeStyle: 'short' },
} as const

@Pipe({ name: 'activityDate' })
export class ActivityDatePipe implements PipeTransform {
  transform(timestamp: string, language: Language, format: keyof typeof formats = 'dateTime'): string {
    const date = new Date(timestamp)
    return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat(language, formats[format]).format(date)
  }
}
