import { motion, useDragControls } from 'framer-motion'
import {
  ArrowUpRight,
  AtSign,
  Bookmark,
  CalendarDays,
  ChevronRight,
  CircleUserRound,
  ExternalLink,
  MapPin,
  MessageCircle,
  UsersRound,
  X,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { Startup } from '../types/startup'

interface StartupDetailModalProps {
  startup: Startup
  recommendation: string
  onClose: () => void
}

export function StartupDetailModal({
  startup,
  recommendation,
  onClose,
}: StartupDetailModalProps) {
  const [hasImageError, setHasImageError] = useState(false)
  const [contactStarted, setContactStarted] = useState(false)
  const modalRef = useRef<HTMLElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const dragControls = useDragControls()

  useEffect(() => {
    const originalOverflow = document.body.style.overflow
    const previousActiveElement =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    document.body.style.overflow = 'hidden'
    const focusTimeout = window.setTimeout(() => closeButtonRef.current?.focus(), 0)

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }

      if (event.key !== 'Tab') {
        return
      }

      const focusableElements = modalRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      )
      const firstElement = focusableElements?.[0]
      const lastElement = focusableElements?.[focusableElements.length - 1]

      if (!firstElement || !lastElement) {
        event.preventDefault()
        return
      }

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.clearTimeout(focusTimeout)
      document.body.style.overflow = originalOverflow
      window.removeEventListener('keydown', handleKeyDown)
      if (previousActiveElement?.isConnected) {
        previousActiveElement.focus()
      }
    }
  }, [onClose])

  return (
    <motion.div
        className="modal-backdrop"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.2 }}
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            onClose()
          }
        }}
      >
        <motion.section
          ref={modalRef}
          className="detail-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="startup-detail-title"
          initial={{ opacity: 0, y: 24, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 18, scale: 0.98 }}
          transition={{ type: 'spring', stiffness: 300, damping: 28 }}
          drag="y"
          dragControls={dragControls}
          dragListener={false}
          dragConstraints={{ top: 0, bottom: 180 }}
          dragElastic={0.35}
          dragMomentum={false}
          dragSnapToOrigin
          onDragEnd={(_, info) => {
            if (info.offset.y > 100 || info.velocity.y > 600) {
              onClose()
            }
          }}
        >
          <div
            className="detail-modal__hero"
            style={{ background: `linear-gradient(145deg, ${startup.accent}65, #17201c)` }}
            onPointerDown={(event) => dragControls.start(event)}
          >
            {!hasImageError && (
              <img
                src={startup.image}
                alt={startup.imageAlt}
                className="absolute inset-0 size-full object-cover"
                onError={() => setHasImageError(true)}
              />
            )}
            <div className="absolute inset-0 bg-gradient-to-t from-[#101714] via-black/10 to-black/10" />
            <div
              className="absolute -right-16 top-12 size-52 rounded-full blur-3xl"
              style={{ backgroundColor: `${startup.accent}35` }}
            />
            <div className="relative flex items-start justify-between gap-4 p-5 sm:p-7">
              <span className="flex size-12 items-center justify-center rounded-2xl border border-white/25 bg-black/25 text-sm font-semibold text-white backdrop-blur-md">
                {startup.logoMark}
              </span>
              <button
                type="button"
                className="icon-button border border-white/20 bg-black/25 text-white/80 backdrop-blur-md hover:bg-black/45 hover:text-white"
                onPointerDown={(event) => event.stopPropagation()}
                onClick={onClose}
                aria-label="Cerrar detalle"
              >
                <X size={18} strokeWidth={1.8} aria-hidden="true" />
              </button>
            </div>
            <div className="relative mt-auto p-5 sm:p-7">
              <span className="rounded-full border border-white/20 bg-black/20 px-3 py-1.5 text-[0.64rem] font-semibold uppercase tracking-[0.13em] text-white/70 backdrop-blur-md">
                {startup.stage}
              </span>
              <h2
                id="startup-detail-title"
                className="mt-4 text-4xl font-medium tracking-[-0.07em] text-white sm:text-5xl"
              >
                {startup.name}
              </h2>
              <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-white/65">
                <span className="inline-flex items-center gap-1.5">
                  <MapPin size={14} aria-hidden="true" />
                  {startup.city}
                </span>
                <span className="size-1 rounded-full bg-white/40" aria-hidden="true" />
                <span>{startup.sector}</span>
              </div>
            </div>
          </div>

          <div className="detail-modal__body">
            <div className="flex flex-col gap-8 p-5 sm:p-7 lg:flex-row lg:gap-10">
              <div className="min-w-0 flex-1">
                <p className="text-lg font-medium leading-7 tracking-[-0.025em] text-white/85 sm:text-xl">
                  {startup.pitch}
                </p>
                <p className="mt-4 text-sm leading-7 text-white/50">
                  {startup.description}
                </p>

                <div className="mt-6 flex flex-wrap gap-1.5">
                  {startup.badges.map((badge) => (
                    <span key={badge} className="mini-badge">
                      {badge}
                    </span>
                  ))}
                </div>

                <div className="mt-8 grid grid-cols-3 gap-2">
                  {startup.metrics.map((metric) => (
                    <div key={metric.label} className="metric-card">
                      <span className="text-[0.62rem] font-semibold uppercase tracking-[0.12em] text-white/30">
                        {metric.label}
                      </span>
                      <span className="mt-2 block text-sm font-medium text-white/75">
                        {metric.value}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="mt-8 border-t border-white/10 pt-7">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-[0.13em] text-white/30">
                        Fundadores
                      </p>
                      <p className="mt-2 text-sm text-white/55">Las personas detrás de la idea</p>
                    </div>
                    <CircleUserRound size={18} className="text-white/35" aria-hidden="true" />
                  </div>
                  <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    {startup.founders.map((founder) => (
                      <div key={founder.name} className="flex items-center gap-3">
                        <span className="flex size-10 items-center justify-center rounded-full border border-white/12 bg-white/[0.07] text-xs font-semibold text-white/70">
                          {founder.initials}
                        </span>
                        <span>
                          <span className="block text-sm font-medium text-white/75">
                            {founder.name}
                          </span>
                          <span className="mt-0.5 block text-xs text-white/35">
                            {founder.role}
                          </span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <aside className="w-full shrink-0 lg:max-w-[15rem]">
                <div className="rounded-2xl border border-[#baf4ce]/18 bg-[#baf4ce]/[0.07] p-5">
                  <span className="flex size-9 items-center justify-center rounded-xl border border-[#baf4ce]/20 bg-[#baf4ce]/10 text-[#baf4ce]">
                    <SparkleIcon />
                  </span>
                  <p className="mt-5 text-[0.64rem] font-semibold uppercase tracking-[0.13em] text-[#baf4ce]/65">
                    ¿Por qué te lo recomendamos?
                  </p>
                  <p className="mt-3 text-sm leading-6 text-white/65">{recommendation}</p>
                </div>

                <div className="mt-5 rounded-2xl border border-white/10 bg-white/[0.035] p-5">
                  <p className="text-xs font-semibold uppercase tracking-[0.13em] text-white/30">
                    Buscan
                  </p>
                  <div className="mt-4 space-y-3">
                    {startup.lookingFor.map((item) => (
                      <div key={item} className="flex items-start gap-2.5 text-sm leading-5 text-white/55">
                        <ChevronRight size={14} className="mt-0.5 shrink-0 text-[#baf4ce]" aria-hidden="true" />
                        <span>{item}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </aside>
            </div>

            <div className="detail-modal__footer">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-white/35">
                <span className="inline-flex items-center gap-1.5">
                  <CalendarDays size={14} aria-hidden="true" />
                  Desde {startup.founded}
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <UsersRound size={14} aria-hidden="true" />
                  {startup.teamSize}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <a
                  href={`https://${startup.website}`}
                  className="modal-link"
                  target="_blank"
                  rel="noreferrer"
                  onClick={(event) => event.preventDefault()}
                >
                  <ExternalLink size={14} aria-hidden="true" />
                  {startup.website}
                </a>
                <button
                  type="button"
                  className="contact-button"
                  onClick={() => setContactStarted(true)}
                >
                  {contactStarted ? (
                    <>
                      <Bookmark size={14} fill="currentColor" aria-hidden="true" />
                      Listo para conectar
                    </>
                  ) : (
                    <>
                      <MessageCircle size={14} aria-hidden="true" />
                      Contactar founders
                    </>
                  )}
                </button>
              </div>
            </div>
            <div className="px-5 pb-5 sm:px-7 sm:pb-7">
              <span className="inline-flex items-center gap-1.5 text-[0.64rem] text-white/25">
                <AtSign size={12} aria-hidden="true" />
                Proyecto ficticio para el prototipo de Pulso
                <ArrowUpRight size={12} aria-hidden="true" />
              </span>
            </div>
          </div>
        </motion.section>
    </motion.div>
  )
}

function SparkleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
      <path
        d="m12 2 1.4 5.6L19 9l-5.6 1.4L12 16l-1.4-5.6L5 9l5.6-1.4L12 2Zm7 12 .7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7L19 14Z"
        fill="currentColor"
      />
    </svg>
  )
}
