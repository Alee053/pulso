import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, Check } from 'lucide-react'
import { BrandMark } from './BrandMark'
import { InterestIcon } from './InterestIcon'
import type { InterestOption, InterestId } from '../types/startup'

interface OnboardingProps {
  interestOptions: readonly InterestOption[]
  selectedInterests: InterestId[]
  onToggleInterest: (interest: InterestId) => void
  onStart: () => void
}

export function Onboarding({
  interestOptions,
  selectedInterests,
  onToggleInterest,
  onStart,
}: OnboardingProps) {
  return (
    <main className="relative min-h-screen overflow-hidden px-5 pb-8 pt-6 sm:px-10 sm:pb-12 sm:pt-8">
      <div className="ambient-orb ambient-orb--mint" aria-hidden="true" />
      <div className="ambient-orb ambient-orb--blue" aria-hidden="true" />
      <div className="relative mx-auto flex min-h-[calc(100vh-3.5rem)] max-w-6xl flex-col">
        <header className="flex items-center justify-between">
          <BrandMark />
          <span className="text-[0.68rem] font-medium uppercase tracking-[0.18em] text-white/35">
            01 / 01
          </span>
        </header>

        <div className="mx-auto flex w-full max-w-4xl flex-1 flex-col justify-center py-12 sm:py-16">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          >
            <span className="eyebrow">UN MAPA DE LO QUE VIENE</span>
            <h1 className="mt-6 max-w-3xl text-4xl font-medium tracking-[-0.065em] text-white text-balance sm:text-6xl lg:text-[5.25rem] lg:leading-[0.98]">
              Descubre lo que está creciendo en Bolivia.
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-white/55 sm:text-lg">
              Pulso reúne proyectos con intención, talento y algo nuevo que
              aportar. Tú decides qué merece tu atención.
            </p>
          </motion.div>

          <motion.section
            className="mt-12 sm:mt-16"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          >
            <div className="mb-4 flex items-end justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/35">
                  Para empezar
                </p>
                <h2 className="mt-2 text-xl font-medium tracking-[-0.03em] text-white sm:text-2xl">
                  ¿Qué estás buscando?
                </h2>
              </div>
              <p className="hidden text-right text-xs leading-5 text-white/35 sm:block">
                Elige todo lo que te mueva.
                <br />
                Puedes cambiarlo después.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              {interestOptions.map((interest, index) => {
                const isSelected = selectedInterests.includes(interest.id)

                return (
                  <motion.button
                    key={interest.id}
                    type="button"
                    className={`glass-choice group relative min-h-36 text-left sm:min-h-44 lg:min-h-52 ${
                      isSelected ? 'glass-choice--selected' : ''
                    }`}
                    onClick={() => onToggleInterest(interest.id)}
                    whileHover={{ y: -4 }}
                    whileTap={{ scale: 0.98 }}
                    initial={{ opacity: 0, y: 14 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{
                      delay: 0.2 + index * 0.06,
                      duration: 0.45,
                      ease: [0.22, 1, 0.36, 1],
                    }}
                  >
                    <span className="flex size-9 items-center justify-center rounded-xl border border-white/10 bg-white/[0.06] text-[#c1f7d1] transition-colors group-hover:bg-white/10">
                      <InterestIcon name={interest.icon} />
                    </span>
                    <span className="mt-6 block text-sm font-medium leading-5 text-white/90">
                      {interest.label}
                    </span>
                    <span className="mt-2 block max-w-[12rem] text-xs leading-5 text-white/40">
                      {interest.description}
                    </span>
                    <AnimatePresence>
                      {isSelected && (
                        <motion.span
                          className="absolute right-4 top-4 flex size-6 items-center justify-center rounded-full bg-[#baf4ce] text-[#0c1710]"
                          initial={{ scale: 0, rotate: -24 }}
                          animate={{ scale: 1, rotate: 0 }}
                          exit={{ scale: 0, rotate: 24 }}
                          transition={{ type: 'spring', stiffness: 420, damping: 22 }}
                        >
                          <Check size={14} strokeWidth={3} aria-hidden="true" />
                        </motion.span>
                      )}
                    </AnimatePresence>
                  </motion.button>
                )
              })}
            </div>
          </motion.section>

          <motion.div
            className="mt-8 flex flex-col items-start gap-4 sm:mt-10 sm:flex-row sm:items-center sm:justify-between"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.55, duration: 0.5 }}
          >
            <p className="text-xs leading-5 text-white/35">
              {selectedInterests.length === 0
                ? 'Selecciona al menos un interés para personalizar tu pulso.'
                : selectedInterests.length === 1
                  ? '1 interés seleccionado.'
                  : `${selectedInterests.length} intereses seleccionados.`}
            </p>
            <motion.button
              type="button"
              className="solid-button w-full sm:w-auto"
              onClick={onStart}
              disabled={selectedInterests.length === 0}
              whileHover={selectedInterests.length > 0 ? { scale: 1.02 } : undefined}
              whileTap={selectedInterests.length > 0 ? { scale: 0.98 } : undefined}
            >
              Entrar a Pulso
              <ArrowRight size={17} strokeWidth={1.8} aria-hidden="true" />
            </motion.button>
          </motion.div>
        </div>

        <footer className="flex items-center justify-between border-t border-white/10 pt-5 text-[0.65rem] uppercase tracking-[0.14em] text-white/25">
          <span>Hecho con curiosidad</span>
          <span>Bolivia / 2026</span>
        </footer>
      </div>
    </main>
  )
}
