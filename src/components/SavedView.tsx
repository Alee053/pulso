import { Bookmark, ChevronRight, Heart } from 'lucide-react'
import { motion } from 'framer-motion'
import { StartupThumb } from './StartupThumb'
import type { Startup } from '../types/startup'

interface SavedViewProps {
  savedStartups: Startup[]
  interestedStartups: Startup[]
  onSelectStartup?: (startup: Startup) => void
}

function SavedStartupCard({
  startup,
  onSelectStartup,
}: {
  startup: Startup
  onSelectStartup?: (startup: Startup) => void
}) {
  return (
    <motion.button
      type="button"
      className="group flex w-full flex-col gap-4 rounded-[1.65rem] border border-white/10 bg-white/[0.045] p-3 text-left transition hover:border-white/20 hover:bg-white/[0.075] sm:flex-row sm:items-center sm:p-4"
      onClick={() => onSelectStartup?.(startup)}
      whileTap={{ scale: 0.99 }}
    >
      <StartupThumb startup={startup} />
      <span className="min-w-0 flex-1 px-1 pb-1 sm:px-0 sm:pb-0">
        <span className="flex items-start justify-between gap-3">
          <span>
            <span className="block text-base font-medium tracking-[-0.02em] text-white/90">
              {startup.name}
            </span>
            <span className="mt-1 block text-xs text-white/40">
              {startup.city} · {startup.sector}
            </span>
          </span>
          <ChevronRight
            size={17}
            className="mt-1 shrink-0 text-white/30 transition group-hover:translate-x-0.5 group-hover:text-white/70"
            aria-hidden="true"
          />
        </span>
        <span className="mt-4 block text-sm leading-6 text-white/60">
          {startup.pitch}
        </span>
        <span className="mt-4 flex flex-wrap gap-1.5">
          {startup.badges.slice(0, 2).map((badge) => (
            <span key={badge} className="mini-badge">
              {badge}
            </span>
          ))}
        </span>
      </span>
    </motion.button>
  )
}

function EmptySavedState() {
  return (
    <div className="flex flex-col items-center justify-center rounded-[1.75rem] border border-dashed border-white/12 bg-white/[0.025] px-6 py-16 text-center">
      <span className="flex size-12 items-center justify-center rounded-2xl border border-white/10 bg-white/[0.06] text-[#baf4ce]">
        <Bookmark size={21} strokeWidth={1.6} aria-hidden="true" />
      </span>
      <h3 className="mt-5 text-base font-medium text-white/85">Tu radar está vacío</h3>
      <p className="mt-2 max-w-xs text-sm leading-6 text-white/40">
        Guarda las ideas que quieras volver a mirar y aparecerán aquí.
      </p>
    </div>
  )
}

export function SavedView({
  savedStartups,
  interestedStartups,
  onSelectStartup,
}: SavedViewProps) {
  const hasSaved = savedStartups.length > 0
  const hasInterested = interestedStartups.length > 0

  return (
    <main className="mx-auto w-full max-w-5xl px-5 pb-32 sm:px-8 lg:px-10 lg:pb-16">
      <div className="mb-9 max-w-xl">
        <span className="eyebrow">TU SELECCIÓN</span>
        <h1 className="mt-5 text-4xl font-medium tracking-[-0.06em] text-white sm:text-5xl">
          Lo que te hizo parar.
        </h1>
        <p className="mt-4 text-base leading-7 text-white/50">
          Una pequeña colección de proyectos para volver cuando tengas tiempo,
          preguntas o ganas de conectar.
        </p>
      </div>

      {!hasSaved && !hasInterested ? (
        <EmptySavedState />
      ) : (
        <div className="grid gap-10 lg:grid-cols-2 lg:gap-8">
          <section>
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Bookmark size={16} className="text-[#baf4ce]" aria-hidden="true" />
                <h2 className="text-sm font-semibold uppercase tracking-[0.13em] text-white/65">
                  Guardados
                </h2>
              </div>
              <span className="text-xs text-white/30">{savedStartups.length}</span>
            </div>
            {hasSaved ? (
              <div className="space-y-3">
                {savedStartups.map((startup) => (
                  <SavedStartupCard
                    key={startup.id}
                    startup={startup}
                    onSelectStartup={onSelectStartup}
                  />
                ))}
              </div>
            ) : (
              <p className="rounded-2xl border border-dashed border-white/10 px-5 py-8 text-sm text-white/35">
                Aún no guardaste ningún proyecto.
              </p>
            )}
          </section>

          <section>
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Heart size={16} className="text-[#efb4c6]" aria-hidden="true" />
                <h2 className="text-sm font-semibold uppercase tracking-[0.13em] text-white/65">
                  Te interesaron
                </h2>
              </div>
              <span className="text-xs text-white/30">{interestedStartups.length}</span>
            </div>
            {hasInterested ? (
              <div className="space-y-3">
                {interestedStartups.map((startup) => (
                  <SavedStartupCard
                    key={startup.id}
                    startup={startup}
                    onSelectStartup={onSelectStartup}
                  />
                ))}
              </div>
            ) : (
              <p className="rounded-2xl border border-dashed border-white/10 px-5 py-8 text-sm text-white/35">
                Marca un proyecto como interesante para verlo aquí.
              </p>
            )}
          </section>
        </div>
      )}
    </main>
  )
}
