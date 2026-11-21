import { defineStrings } from '../../../core/i18n/define-strings'

export const profileBlockStrings = defineStrings({
  block: (name: string) => `Bloquear a ${name}`,
  unblock: (name: string) => `Desbloquear a ${name}`,
  loading: 'Comprobando el estado de contacto…',
  blocking: 'Bloqueando…',
  unblocking: 'Desbloqueando…',
  blocked: 'Perfil bloqueado. No se podrán crear nuevas solicitudes ni enviar mensajes.',
  unblocked: 'Perfil desbloqueado. Se puede volver a solicitar contacto.',
  loadFailed: 'No pudimos comprobar el estado de contacto.',
  actionFailed: 'No pudimos cambiar el estado de contacto. Actualiza e inténtalo de nuevo.',
  retry: 'Reintentar',
}, {
  block: (name: string) => `Block ${name}`,
  unblock: (name: string) => `Unblock ${name}`,
  loading: 'Checking contact status…',
  blocking: 'Blocking…',
  unblocking: 'Unblocking…',
  blocked: 'Profile blocked. New requests and messages cannot be sent.',
  unblocked: 'Profile unblocked. Contact requests can be sent again.',
  loadFailed: 'We could not check the contact status.',
  actionFailed: 'We could not change the contact status. Refresh and try again.',
  retry: 'Retry',
})
