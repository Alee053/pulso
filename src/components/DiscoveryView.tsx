import { AnimatePresence, motion } from 'framer-motion'
import {
  Bookmark,
  Check,
  ArrowDown,
  ArrowUp,
  ChevronLeft,
  ChevronRight,
  Heart,
  Keyboard,
  RotateCcw,
  Sparkles,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { StartupCard } from './StartupCard'
import type { Decision, Startup } from '../types/startup'

interface DiscoveryViewProps {
  activeSignals: string[]
  currentStartup: Startup | undefined
  feedRemaining: number
  isModalOpen: boolean
  totalStartups: number
  savedIds: string[]
  onDecision: (decision: Decision) => void
  onRestart: () => void
  onSave: (startupId: string) => void
  onSelectStartup: (startup: Startup) => void
}

function ActionButton({
  label,
  shortcut,
  icon,
  onClick,
  tone = 'default',
  disabled = false,
}: {
  label: string
  shortcut: string
  icon: React.ReactNode
  onClick: () => void
  tone?: 'default' | 'save' | 'interested'
  disabled?: boolean
}) {
  return (
    <motion.button
      type="button"
      className={`discovery-action discovery-action--${tone}`}
      onClick={onClick}
      disabled={disabled}
      whileHover={{ y: -3 }}
      whileTap={{ scale: 0.92 }}
      aria-label={`${label} (${shortcut})`}
    >
      <span className="discovery-action__icon">{icon}</span>
      <span className="discovery-action__label">{label}</span>
      <span className="discovery-action__shortcut">{shortcut}</span>
    </motion.button>
  )
}

function EmptyFeed({ onRestart }: { onRestart: () => void }) {
  return (
    <motion.div
      className="glass-panel flex w-full max-w-[35rem] flex-col items-center px-7 py-16 text-center sm:px-12 sm:py-24"
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
    >
      <span className="flex size-14 items-center justify-center rounded-2xl border border-[#baf4ce]/20 bg-[#baf4ce]/10 text-[#baf4ce]">
        <Check size={25} strokeWidth={1.6} aria-hidden="true" />
      </span>
      <span className="eyebrow mt-7">PRIMERA VUELTA COMPLETA</span>
      <h2 className="mt-5 text-3xl font-medium tracking-[-0.055em] text-white sm:text-4xl">
        Ya viste todo el pulso.
      </h2>
      <p className="mt-4 max-w-sm text-sm leading-6 text-white/45">
        Tu selección quedó guardada en la memoria de esta sesión. ¿Quieres
        explorar con una mirada nueva?
      </p>
      <button type="button" className="solid-button mt-8" onClick={onRestart}>
        <RotateCcw size={15} aria-hidden="true" />
        Volver a explorar
      </button>
    </motion.div>
  )
}

export function DiscoveryView({
  activeSignals,
  currentStartup,
  feedRemaining,
  isModalOpen,
  totalStartups,
  savedIds,
  onDecision,
  onRestart,
  onSave,
  onSelectStartup,
}: DiscoveryViewProps) {
  const [direction, setDirection] = useState(1)
  const [isTransitioning, setIsTransitioning] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })
  const isSaved = currentStartup ? savedIds.includes(currentStartup.id) : false
  const activeNumber = Math.min(totalStartups - feedRemaining + 1, totalStartups)
  const progress = totalStartups > 0 ? (activeNumber / totalStartups) * 100 : 0
  const horizontalIntensity = Math.min(Math.abs(dragOffset.x) / 90, 1)
  const verticalIntensity = Math.min(Math.abs(dragOffset.y) / 90, 1)
  const isDraggingLeft = dragOffset.x < -12
  const isDraggingRight = dragOffset.x > 12
  const isDraggingUp = dragOffset.y < -12
  const isDraggingDown = dragOffset.y > 12

  useEffect(() => {
    if (!toast) {
      return
    }

    const timeoutId = window.setTimeout(() => setToast(null), 2600)
    return () => window.clearTimeout(timeoutId)
  }, [toast])

  function handleDecision(decision: Decision) {
    if (!currentStartup || isTransitioning) {
      return
    }

    setDirection(decision === 'interested' ? 1 : -1)
    setIsTransitioning(true)
    onDecision(decision)
  }

  function handleSave() {
    if (!currentStartup || isTransitioning) {
      return
    }

    onSave(currentStartup.id)
    setToast(isSaved ? 'Quitado de tus guardados' : 'Guardado en tu selección')
  }

  useEffect(() => {
    if (isModalOpen) {
      return
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.metaKey || event.ctrlKey || event.altKey || event.repeat) {
        return
      }

      if (event.key === 'ArrowLeft') {
        event.preventDefault()
        handleDecision('skip')
      }

      if (event.key === 'ArrowRight') {
        event.preventDefault()
        handleDecision('interested')
      }

      if (event.key.toLowerCase() === 's') {
        event.preventDefault()
        handleSave()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  })

  return (
    <main className="discovery-view mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 pb-32 sm:px-8 lg:px-10 lg:pb-16">
      <div className="discovery-view__heading mb-7 flex flex-col justify-between gap-5 sm:mb-9 sm:flex-row sm:items-end">
        <div>
          <span className="eyebrow">PARA TI / SELECCIÓN LOCAL</span>
          <h1 className="mt-4 text-3xl font-medium tracking-[-0.06em] text-white sm:text-5xl">
            Ideas con pulso.
          </h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-white/45 sm:text-base">
            Una buena idea no siempre hace ruido. A veces solo necesita que
            alguien la vea.
          </p>
        </div>
        <div className="discovery-view__meta flex items-center gap-3 self-start sm:self-auto">
          <span className="flex size-9 items-center justify-center rounded-xl border border-white/10 bg-white/[0.05] text-[#baf4ce]">
            <Sparkles size={16} strokeWidth={1.7} aria-hidden="true" />
          </span>
          <div>
            <p className="text-[0.64rem] font-semibold uppercase tracking-[0.15em] text-white/30">
              Hecho para descubrir
            </p>
            <p className="mt-1 text-xs text-white/55">Curado en Bolivia</p>
          </div>
        </div>
      </div>

      <div className="discovery-view__grid grid flex-1 items-start gap-8 lg:grid-cols-[minmax(12rem,1fr)_minmax(20rem,34rem)_minmax(12rem,1fr)] lg:items-center lg:gap-10">
        <aside className="discovery-side discovery-side--left discovery-view__side order-2 lg:order-1">
          <div className="hidden lg:block">
            <span className="side-label">SEÑALES ACTIVAS</span>
            <div className="mt-4 flex flex-wrap gap-2">
              {(activeSignals.length > 0 ? activeSignals : ['Exploración'])
                .slice(0, 3)
                .map((signal) => (
                  <span key={signal} className="side-chip">
                    {signal}
                  </span>
                ))}
            </div>
            <p className="mt-7 max-w-[12rem] text-sm leading-6 text-white/35">
              Tu selección se ajusta un poco cada vez que decides.
            </p>
          </div>
        </aside>

        <section className="discovery-view__stage order-1 flex min-w-0 flex-col items-center lg:order-2">
          <div className="discovery-view__progress-meta mb-4 flex w-full max-w-[35rem] items-center justify-between text-[0.68rem] font-semibold uppercase tracking-[0.14em] text-white/35">
            <span>
              {String(activeNumber).padStart(2, '0')} / {String(totalStartups).padStart(2, '0')}
            </span>
            <span>{feedRemaining} por descubrir</span>
          </div>
          <div className="discovery-view__progress mb-6 h-px w-full max-w-[35rem] overflow-hidden bg-white/10">
            <motion.div
              className="h-full bg-[#baf4ce] shadow-[0_0_1rem_rgba(186,244,206,0.65)]"
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
            />
          </div>

          <AnimatePresence
            initial={false}
            mode="wait"
            custom={direction}
            onExitComplete={() => setIsTransitioning(false)}
          >
            {currentStartup ? (
              <StartupCard
                key={currentStartup.id}
                startup={currentStartup}
                direction={direction}
                disabled={isTransitioning}
                isSaved={isSaved}
                onSave={handleSave}
                onDragChange={(x, y) => setDragOffset({ x, y })}
                onSelect={onSelectStartup}
                onDragDecision={handleDecision}
              />
            ) : (
              <EmptyFeed key="empty" onRestart={onRestart} />
            )}
          </AnimatePresence>

          <motion.div
            className="drag-intent drag-intent--skip"
            animate={{
              opacity: isDraggingLeft ? horizontalIntensity : 0,
              scale: isDraggingLeft ? 0.9 + horizontalIntensity * 0.1 : 0.82,
              x: isDraggingLeft ? 0 : 8,
            }}
            transition={{ duration: 0.12 }}
            aria-hidden="true"
          >
            <span className="drag-intent__icon">
              <X size={21} strokeWidth={2.2} />
            </span>
            <span>Pasar</span>
          </motion.div>
          <motion.div
            className="drag-intent drag-intent--interested"
            animate={{
              opacity: isDraggingRight ? horizontalIntensity : 0,
              scale: isDraggingRight ? 0.9 + horizontalIntensity * 0.1 : 0.82,
              x: isDraggingRight ? 0 : -8,
            }}
            transition={{ duration: 0.12 }}
            aria-hidden="true"
          >
            <span className="drag-intent__icon">
              <Heart size={21} strokeWidth={2.2} />
            </span>
            <span>Me interesa</span>
          </motion.div>
          <motion.div
            className="drag-intent drag-intent--detail"
            animate={{
              opacity: isDraggingUp ? verticalIntensity : 0,
              scale: isDraggingUp ? 0.9 + verticalIntensity * 0.1 : 0.82,
              y: isDraggingUp ? 0 : 8,
            }}
            transition={{ duration: 0.12 }}
            aria-hidden="true"
          >
            <span className="drag-intent__icon">
              <ArrowUp size={21} strokeWidth={2.2} />
            </span>
            <span>Ver detalle</span>
          </motion.div>
          <motion.div
            className="drag-intent drag-intent--save"
            animate={{
              opacity: isDraggingDown ? verticalIntensity : 0,
              scale: isDraggingDown ? 0.9 + verticalIntensity * 0.1 : 0.82,
              y: isDraggingDown ? 0 : -8,
            }}
            transition={{ duration: 0.12 }}
            aria-hidden="true"
          >
            <span className="drag-intent__icon">
              <Bookmark size={20} strokeWidth={2.2} />
            </span>
            <span>Guardar</span>
            <ArrowDown size={15} strokeWidth={2.2} />
          </motion.div>

          <div className="discovery-view__actions mt-6 flex w-full max-w-[35rem] items-start justify-center gap-2 sm:gap-4">
            <ActionButton
              label="Pasar"
              shortcut="←"
              icon={<X size={20} strokeWidth={1.7} aria-hidden="true" />}
              onClick={() => handleDecision('skip')}
              disabled={!currentStartup || isTransitioning}
            />
            <ActionButton
              label={isSaved ? 'Guardado' : 'Guardar'}
              shortcut="S"
              icon={
                <Bookmark
                  size={19}
                  strokeWidth={isSaved ? 2.4 : 1.7}
                  fill={isSaved ? 'currentColor' : 'none'}
                  aria-hidden="true"
                />
              }
              onClick={handleSave}
              tone="save"
              disabled={!currentStartup || isTransitioning}
            />
            <ActionButton
              label="Me interesa"
              shortcut="→"
              icon={<Heart size={20} strokeWidth={1.7} aria-hidden="true" />}
              onClick={() => handleDecision('interested')}
              tone="interested"
              disabled={!currentStartup || isTransitioning}
            />
          </div>
          <div className="mt-4 hidden items-center gap-2 text-[0.65rem] text-white/25 sm:flex">
            <Keyboard size={13} aria-hidden="true" />
            <span>Usa las flechas o arrastra la tarjeta</span>
          </div>
        </section>

        <aside className="discovery-side discovery-side--right discovery-view__side order-3 hidden lg:block">
          <span className="side-label">CÓMO FUNCIONA</span>
          <div className="mt-5 space-y-5">
            <div className="flex gap-3">
              <ChevronLeft size={15} className="mt-0.5 text-white/40" aria-hidden="true" />
              <p className="text-sm leading-5 text-white/40">
                Pasa si no es el momento.
              </p>
            </div>
            <div className="flex gap-3">
              <ChevronRight size={15} className="mt-0.5 text-[#baf4ce]" aria-hidden="true" />
              <p className="text-sm leading-5 text-white/50">
                Marca lo que quieras seguir.
              </p>
            </div>
            <div className="flex gap-3">
              <Bookmark size={15} className="mt-0.5 text-white/40" aria-hidden="true" />
              <p className="text-sm leading-5 text-white/40">
                Guarda una idea para después.
              </p>
            </div>
          </div>
        </aside>
      </div>

      <AnimatePresence>
        {toast && (
          <motion.div
            className="toast"
            initial={{ opacity: 0, y: 12, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.96 }}
            transition={{ duration: 0.22 }}
          >
            <span className="flex size-6 items-center justify-center rounded-full bg-[#baf4ce] text-[#0a1710]">
              <Bookmark size={13} fill="currentColor" aria-hidden="true" />
            </span>
            {toast}
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  )
}
