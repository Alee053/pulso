import { Compass, Sparkles } from 'lucide-react'
import { motion } from 'framer-motion'

export function DiscoveryPlaceholder() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 items-center justify-center px-5 pb-32 pt-4 sm:px-8 lg:px-10 lg:pb-16">
      <motion.section
        className="glass-panel flex w-full max-w-2xl flex-col items-center px-7 py-16 text-center sm:px-12 sm:py-24"
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <span className="flex size-14 items-center justify-center rounded-2xl border border-[#baf4ce]/20 bg-[#baf4ce]/10 text-[#baf4ce]">
          <Compass size={25} strokeWidth={1.5} aria-hidden="true" />
        </span>
        <p className="mt-7 text-xs font-semibold uppercase tracking-[0.16em] text-[#baf4ce]/65">
          Tu radar está listo
        </p>
        <h1 className="mt-4 text-3xl font-medium tracking-[-0.05em] text-white sm:text-4xl">
          Una idea a la vez.
        </h1>
        <p className="mt-4 max-w-md text-sm leading-6 text-white/45">
          Estamos preparando tu primera selección de proyectos bolivianos.
        </p>
        <div className="mt-9 flex items-center gap-2 text-xs text-white/30">
          <Sparkles size={14} aria-hidden="true" />
          <span>La siguiente parada llega en el próximo checkpoint.</span>
        </div>
      </motion.section>
    </main>
  )
}
