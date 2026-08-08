import { ArrowUpRight, Settings2, Sparkles } from 'lucide-react'
import { motion } from 'framer-motion'
import { InterestIcon } from './InterestIcon'
import type { InterestId, InterestOption } from '../types/startup'

interface ProfileViewProps {
  interestOptions: readonly InterestOption[]
  selectedInterests: InterestId[]
}

export function ProfileView({
  interestOptions,
  selectedInterests,
}: ProfileViewProps) {
  const selectedOptions = interestOptions.filter((option) =>
    selectedInterests.includes(option.id),
  )

  return (
    <main className="mx-auto w-full max-w-5xl px-5 pb-32 sm:px-8 lg:px-10 lg:pb-16">
      <div className="mb-9 max-w-xl">
        <span className="eyebrow">TU ESPACIO</span>
        <h1 className="mt-5 text-4xl font-medium tracking-[-0.06em] text-white sm:text-5xl">
          Tu forma de mirar.
        </h1>
        <p className="mt-4 text-base leading-7 text-white/50">
          Pulso aprende de tus decisiones dentro de esta sesión para acercarte
          a historias que puedan resonar contigo.
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
        <motion.section
          className="glass-panel p-6 sm:p-8"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45 }}
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-4">
              <span className="flex size-14 items-center justify-center rounded-2xl border border-white/15 bg-[#baf4ce] text-lg font-semibold text-[#0a1710] shadow-lg shadow-emerald-950/20">
                T
              </span>
              <div>
                <p className="text-lg font-medium tracking-[-0.03em] text-white/90">
                  Explorador local
                </p>
                <p className="mt-1 text-sm text-white/40">Tu sesión de Pulso</p>
              </div>
            </div>
            <button
              type="button"
              className="icon-button icon-button--subtle"
              aria-label="Configurar perfil"
            >
              <Settings2 size={17} strokeWidth={1.7} aria-hidden="true" />
            </button>
          </div>

          <div className="mt-9 border-t border-white/10 pt-6">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.13em] text-white/35">
                  Tu selección
                </p>
                <p className="mt-2 text-sm text-white/65">
                  {selectedOptions.length} intereses activos
                </p>
              </div>
              <Sparkles size={18} className="text-[#baf4ce]" aria-hidden="true" />
            </div>
            <div className="mt-5 flex flex-wrap gap-2">
              {selectedOptions.map((interest) => (
                <span key={interest.id} className="interest-pill">
                  <InterestIcon name={interest.icon} size={14} />
                  {interest.shortLabel}
                </span>
              ))}
            </div>
          </div>
        </motion.section>

        <motion.section
          className="relative overflow-hidden rounded-[2rem] border border-[#baf4ce]/20 bg-[#baf4ce]/[0.08] p-6 sm:p-8"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.08, duration: 0.45 }}
        >
          <div className="absolute -right-12 -top-12 size-40 rounded-full bg-[#baf4ce]/15 blur-3xl" />
          <div className="relative">
            <span className="flex size-10 items-center justify-center rounded-xl border border-[#baf4ce]/20 bg-[#baf4ce]/10 text-[#baf4ce]">
              <Sparkles size={18} strokeWidth={1.7} aria-hidden="true" />
            </span>
            <p className="mt-8 text-xs font-semibold uppercase tracking-[0.13em] text-[#baf4ce]/65">
              Una nota de Pulso
            </p>
            <h2 className="mt-3 max-w-xs text-2xl font-medium leading-tight tracking-[-0.045em] text-white/90">
              Lo local también puede sentirse futurista.
            </h2>
            <p className="mt-4 text-sm leading-6 text-white/50">
              Cada proyecto que encuentras abre una puerta para alguien más.
            </p>
            <a
              href="https://pulso.local"
              className="mt-8 inline-flex items-center gap-2 text-sm font-medium text-[#baf4ce] transition hover:text-white"
              onClick={(event) => event.preventDefault()}
            >
              Conoce la idea <ArrowUpRight size={15} aria-hidden="true" />
            </a>
          </div>
        </motion.section>
      </div>
    </main>
  )
}
