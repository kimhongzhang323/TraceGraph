import {
  Activity,
  ArrowRight,
  Box,
  FileCode2,
  Database,
  Github,
  History,
  Moon,
  Package,
  Play,
  ScanSearch,
  RefreshCw,
  Sun,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface IconProps {
  name: string
  size?: number
  className?: string
  strokeWidth?: number
}

export function Icon({ name, size = 16, className = '', strokeWidth = 1.75 }: IconProps) {
  const iconMap: Record<string, LucideIcon> = {
    activity: Activity,
    'arrow-right': ArrowRight,
    box: Box,
    database: Database,
    'file-code': FileCode2,
    github: Github,
    history: History,
    moon: Moon,
    package: Package,
    play: Play,
    'refresh-cw': RefreshCw,
    search: ScanSearch,
    sun: Sun,
  }

  const LucideIcon = iconMap[name]

  if (!LucideIcon) {
    return <span className={`inline-flex items-center justify-center ${className}`} style={{ width: size, height: size }} />
  }

  return (
    <span className={`inline-flex items-center justify-center ${className}`} style={{ width: size, height: size }}>
      <LucideIcon size={size} strokeWidth={strokeWidth} aria-hidden="true" focusable="false" />
    </span>
  )
}
