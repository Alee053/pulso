import { Bookmark, Compass, UserRound } from 'lucide-react'
import { BrandMark } from './BrandMark'
import type { AppView } from '../types/startup'

interface NavigationBarProps {
  activeView: AppView
  savedCount: number
  interestedCount: number
  onNavigate: (view: AppView) => void
}

const navigationItems: Array<{
  id: AppView
  label: string
  icon: typeof Compass
}> = [
  { id: 'discover', label: 'Descubrir', icon: Compass },
  { id: 'saved', label: 'Guardados', icon: Bookmark },
  { id: 'profile', label: 'Perfil', icon: UserRound },
]

export function NavigationBar({
  activeView,
  savedCount,
  interestedCount,
  onNavigate,
}: NavigationBarProps) {
  const totalSaved = savedCount + interestedCount

  return (
    <>
      <header className="relative z-30 mx-auto flex w-full max-w-7xl items-center justify-between px-5 py-5 sm:px-8 sm:py-7 lg:px-10">
        <BrandMark />
        <nav className="hidden items-center gap-1 rounded-full border border-white/10 bg-white/[0.035] p-1 backdrop-blur-xl md:flex">
          {navigationItems.map((item) => {
            const Icon = item.icon
            const isActive = item.id === activeView
            const count = item.id === 'saved' ? totalSaved : 0

            return (
              <button
                key={item.id}
                type="button"
                className={`nav-pill ${isActive ? 'nav-pill--active' : ''}`}
                onClick={() => onNavigate(item.id)}
              >
                <Icon size={15} strokeWidth={isActive ? 2 : 1.7} aria-hidden="true" />
                {item.label}
                {count > 0 && <span className="nav-count">{count}</span>}
              </button>
            )
          })}
        </nav>
        <div className="flex items-center gap-3 text-right">
          <div className="hidden sm:block">
            <p className="text-[0.65rem] font-semibold uppercase tracking-[0.18em] text-white/30">
              Tu pulso
            </p>
            <p className="mt-1 text-xs text-white/60">Curiosidad local</p>
          </div>
          <span className="flex size-9 items-center justify-center rounded-full border border-white/10 bg-white/[0.06] text-xs font-medium text-white/75">
            {Math.max(totalSaved, 1)}
          </span>
        </div>
      </header>

      <nav className="mobile-nav fixed inset-x-4 bottom-4 z-40 flex items-center justify-around rounded-[1.35rem] border border-white/12 bg-[#131917]/85 p-1.5 shadow-2xl shadow-black/30 backdrop-blur-2xl md:hidden">
        {navigationItems.map((item) => {
          const Icon = item.icon
          const isActive = item.id === activeView
          const count = item.id === 'saved' ? totalSaved : 0

          return (
            <button
              key={item.id}
              type="button"
              className={`mobile-nav__item ${isActive ? 'mobile-nav__item--active' : ''}`}
              onClick={() => onNavigate(item.id)}
            >
              <span className="relative">
                <Icon size={18} strokeWidth={isActive ? 2 : 1.7} aria-hidden="true" />
                {count > 0 && <span className="mobile-nav__count">{count}</span>}
              </span>
              <span>{item.label}</span>
            </button>
          )
        })}
      </nav>
    </>
  )
}
