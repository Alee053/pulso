import { CommonModule } from '@angular/common'
import { Component, Input, inject, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { IconComponent } from '../../../shared/icon/icon'
import { ProjectShareApi, type ShareOrigin } from '../data-access/project-share-api'

@Component({
  selector: 'pulso-share-button',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './share-button.html',
  styleUrls: ['./share-button.css'],
})
export class ShareButtonComponent {
  @Input({ required: true }) projectRef!: string
  @Input({ required: true }) projectName!: string
  @Input() origin: ShareOrigin = 'FEED'

  private readonly shareApi = inject(ProjectShareApi)

  readonly isSharing = signal(false)
  readonly feedback = signal<string | null>(null)

  async share(event: MouseEvent): Promise<void> {
    event.stopPropagation()
    if (this.isSharing()) return

    this.isSharing.set(true)
    this.feedback.set(null)

    try {
      const link = await firstValueFrom(this.shareApi.generate(this.projectRef))

      // Intentar native share primero (móvil)
      const shared = await this.shareApi.shareNative(link.url, this.projectName)

      if (!shared) {
        // Fallback: copiar al portapapeles
        const copied = await this.shareApi.copyToClipboard(link.url)
        this.feedback.set(copied ? 'Enlace copiado' : 'No se pudo copiar')
      } else {
        this.feedback.set('Compartido')
      }

      // Registrar el evento SHARED (solo si el usuario está autenticado)
      try {
        await firstValueFrom(this.shareApi.register(this.projectRef, this.origin))
      } catch {
        // Ignorar errores de registro (no bloquear la acción)
      }

      setTimeout(() => this.feedback.set(null), 3000)
    } catch {
      this.feedback.set('No se pudo generar el enlace')
      setTimeout(() => this.feedback.set(null), 3000)
    } finally {
      this.isSharing.set(false)
    }
  }
}