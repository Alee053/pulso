interface BrandMarkProps {
  compact?: boolean
}

export function BrandMark({ compact = false }: BrandMarkProps) {
  return (
    <div className="flex items-center gap-3">
      <span
        className="relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-[0.9rem] border border-white/20 bg-[#baf4ce] text-sm font-semibold text-[#0a1710] shadow-[0_0_2rem_rgba(173,245,200,0.2)]"
        aria-hidden="true"
      >
        <span className="absolute -right-2 -top-3 size-7 rounded-full bg-white/60 blur-md" />
        <span className="relative">P</span>
      </span>
      {!compact && (
        <span className="text-[0.72rem] font-semibold tracking-[0.2em] text-white/90">
          PULSO
        </span>
      )}
    </div>
  )
}
