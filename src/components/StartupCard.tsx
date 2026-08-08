import { motion } from 'framer-motion'
import { ArrowUpRight, Bookmark, Heart, MapPin, X } from 'lucide-react'
import { useRef, useState } from 'react'
import type { Decision, Startup } from '../types/startup'

interface StartupCardProps {
  startup: Startup
  direction: number
  disabled: boolean
  isSaved: boolean
  onSave: () => void
  onSelect: (startup: Startup) => void
  onDragDecision: (decision: Decision) => void
}

const cardVariants = {
  enter: (direction: number) => ({
    opacity: 0,
    x: direction * 80,
    rotate: direction * 3,
    scale: 0.96,
  }),
  center: {
    opacity: 1,
    x: 0,
    rotate: 0,
    scale: 1,
  },
  exit: (direction: number) => ({
    opacity: 0,
    x: direction * 460,
    rotate: direction * 13,
    scale: 0.92,
  }),
}

export function StartupCard({
  startup,
  direction,
  disabled,
  isSaved,
  onSave,
  onSelect,
  onDragDecision,
}: StartupCardProps) {
  const [hasImageError, setHasImageError] = useState(false)
  const [dragX, setDragX] = useState(0)
  const didDrag = useRef(false)
  const dragIntensity = Math.min(Math.abs(dragX) / 90, 1)
  const isDraggingLeft = dragX < -12
  const isDraggingRight = dragX > 12

  return (
    <motion.article
      className="discovery-card"
      custom={direction}
      variants={cardVariants}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{
        x: { type: 'spring', stiffness: 280, damping: 28 },
        rotate: { type: 'spring', stiffness: 250, damping: 26 },
        opacity: { duration: 0.22 },
        scale: { duration: 0.3, ease: [0.22, 1, 0.36, 1] },
      }}
      drag={disabled ? false : 'x'}
      dragConstraints={{ left: -135, right: 135 }}
      dragElastic={0.72}
      dragMomentum={false}
      dragSnapToOrigin
      whileDrag={{ scale: 1.02, cursor: 'grabbing' }}
      onDragStart={() => {
        didDrag.current = true
      }}
      onDrag={(_, info) => {
        setDragX(info.offset.x)
      }}
      onDragEnd={(_, info) => {
        setDragX(0)
        if (info.offset.x > 92 || info.velocity.x > 650) {
          onDragDecision('interested')
        } else if (info.offset.x < -92 || info.velocity.x < -650) {
          onDragDecision('skip')
        } else {
          window.setTimeout(() => {
            didDrag.current = false
          }, 0)
        }
      }}
      onClick={(event) => {
        if (didDrag.current) {
          didDrag.current = false
          return
        }

        if (!disabled) {
          event.currentTarget.focus()
          onSelect(startup)
        }
      }}
      role="group"
      tabIndex={-1}
      aria-label={`Ver perfil de ${startup.name}`}
    >
      <div
        className="absolute inset-0"
        style={{ background: `linear-gradient(145deg, ${startup.accent}55, #17201c)` }}
      >
        {!hasImageError && (
          <img
            src={startup.image}
            alt={startup.imageAlt}
            className="absolute inset-0 size-full object-cover"
            draggable={false}
            onError={() => setHasImageError(true)}
          />
        )}
        <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(6,11,9,0.05)_0%,rgba(6,11,9,0.05)_27%,rgba(6,11,9,0.32)_51%,#09100d_100%)]" />
        <div
          className="absolute -right-20 top-20 size-60 rounded-full blur-3xl"
          style={{ backgroundColor: `${startup.accent}28` }}
        />
      </div>

      <motion.div
        className="drag-intent drag-intent--skip"
        animate={{
          opacity: isDraggingLeft ? dragIntensity : 0,
          scale: isDraggingLeft ? 0.9 + dragIntensity * 0.1 : 0.82,
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
          opacity: isDraggingRight ? dragIntensity : 0,
          scale: isDraggingRight ? 0.9 + dragIntensity * 0.1 : 0.82,
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

      <div className="relative flex h-full flex-col justify-between p-5 sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2 rounded-full border border-white/15 bg-black/20 px-2 py-1.5 backdrop-blur-md">
            <span className="flex size-8 items-center justify-center rounded-full border border-white/15 bg-white/10 text-[0.65rem] font-semibold text-white/90">
              {startup.logoMark}
            </span>
            <span className="pr-1 text-xs font-medium text-white/70">{startup.name}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="discovery-card__save"
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation()
                onSave()
              }}
              disabled={disabled}
              aria-label={isSaved ? 'Quitar de guardados' : 'Guardar startup'}
            >
              <Bookmark
                size={17}
                strokeWidth={isSaved ? 2.4 : 1.8}
                fill={isSaved ? 'currentColor' : 'none'}
                aria-hidden="true"
              />
            </button>
            <span className="rounded-full border border-white/15 bg-black/20 px-3 py-2 text-[0.65rem] font-semibold uppercase tracking-[0.12em] text-white/65 backdrop-blur-md">
              {startup.stage}
            </span>
          </div>
        </div>

        <div className="max-w-[32rem]">
          <div className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-medium text-white/60">
            <span className="inline-flex items-center gap-1.5">
              <MapPin size={13} strokeWidth={1.8} aria-hidden="true" />
              {startup.city}
            </span>
            <span className="size-1 rounded-full bg-white/35" aria-hidden="true" />
            <span>{startup.sector}</span>
          </div>
          <h2 className="text-[2.75rem] font-medium leading-[0.96] tracking-[-0.07em] text-white sm:text-6xl">
            {startup.name}
          </h2>
          <p className="mt-4 max-w-md text-base font-medium leading-6 text-white/82 sm:text-lg">
            {startup.pitch}
          </p>
          <p className="discovery-card__description mt-3 max-w-lg text-sm leading-6 text-white/48">
            {startup.description}
          </p>
          <div className="mt-5 flex flex-wrap gap-1.5">
            {startup.badges.map((badge) => (
              <span key={badge} className="card-badge">
                {badge}
              </span>
            ))}
          </div>
          <button
            type="button"
            className="mt-6 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.13em] text-[#c5f8d4] transition hover:text-white"
            onClick={(event) => {
              event.stopPropagation()
              onSelect(startup)
            }}
          >
            Ver perfil completo
            <ArrowUpRight size={14} strokeWidth={1.8} aria-hidden="true" />
          </button>
        </div>
      </div>
    </motion.article>
  )
}
