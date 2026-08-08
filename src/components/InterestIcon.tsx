import {
  Compass,
  Handshake,
  Heart,
  Sparkles,
  TrendingUp,
} from 'lucide-react'
import type { InterestOption } from '../types/startup'

const iconMap = {
  sparkles: Sparkles,
  trending: TrendingUp,
  handshake: Handshake,
  compass: Compass,
  heart: Heart,
} as const

interface InterestIconProps {
  name: InterestOption['icon']
  size?: number
  strokeWidth?: number
}

export function InterestIcon({
  name,
  size = 18,
  strokeWidth = 1.8,
}: InterestIconProps) {
  const Icon = iconMap[name]

  return <Icon size={size} strokeWidth={strokeWidth} aria-hidden="true" />
}
