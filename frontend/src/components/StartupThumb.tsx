import { useState } from 'react'
import type { Startup } from '../types/startup'

interface StartupThumbProps {
  startup: Startup
}

export function StartupThumb({ startup }: StartupThumbProps) {
  const [hasError, setHasError] = useState(false)

  return (
    <div
      className="relative aspect-[1.15] shrink-0 overflow-hidden rounded-2xl border border-white/10 sm:w-36"
      style={{ background: `linear-gradient(145deg, ${startup.accent}55, #18201d)` }}
    >
      {!hasError && (
        <img
          src={startup.image}
          alt=""
          className="absolute inset-0 size-full object-cover transition duration-500 group-hover:scale-105"
          onError={() => setHasError(true)}
        />
      )}
      <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/5 to-white/15" />
      <span className="absolute bottom-3 left-3 flex size-9 items-center justify-center rounded-xl border border-white/25 bg-black/25 text-xs font-semibold text-white backdrop-blur-md">
        {startup.logoMark}
      </span>
    </div>
  )
}
