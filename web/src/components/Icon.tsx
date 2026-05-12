import {
  Activity,
  AlertTriangle,
  Apple,
  ArrowLeft,
  ArrowRight,
  Box,
  Building2,
  Check,
  Chrome,
  CreditCard,
  Database,
  FileCode2,
  Fingerprint,
  GitBranch,
  Github,
  History,
  Key,
  LayoutDashboard,
  Lock,
  LogOut,
  Mail,
  MailCheck,
  Monitor,
  Moon,
  Package,
  Play,
  Plus,
  Redo2,
  RefreshCw,
  ScanSearch,
  Shield,
  ShieldCheck,
  SlidersHorizontal,
  Smartphone,
  Square,
  Sun,
  Trash2,
  User,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface IconProps {
  name: string
  size?: number
  className?: string
  strokeWidth?: number
}

const ICON_MAP: Record<string, LucideIcon> = {
  activity:             Activity,
  'alert-triangle':     AlertTriangle,
  apple:                Apple,
  'arrow-left':         ArrowLeft,
  'arrow-right':        ArrowRight,
  box:                  Box,
  'building-2':         Building2,
  check:                Check,
  chrome:               Chrome,
  'credit-card':        CreditCard,
  database:             Database,
  'file-code':          FileCode2,
  fingerprint:          Fingerprint,
  'git-branch':         GitBranch,
  github:               Github,
  history:              History,
  key:                  Key,
  'layout-dashboard':   LayoutDashboard,
  lock:                 Lock,
  'log-out':            LogOut,
  mail:                 Mail,
  'mail-check':         MailCheck,
  monitor:              Monitor,
  moon:                 Moon,
  package:              Package,
  play:                 Play,
  plus:                 Plus,
  'redo-2':             Redo2,
  'refresh-cw':         RefreshCw,
  search:               ScanSearch,
  shield:               Shield,
  'shield-check':       ShieldCheck,
  'sliders-horizontal': SlidersHorizontal,
  smartphone:           Smartphone,
  square:               Square,
  sun:                  Sun,
  'trash-2':            Trash2,
  user:                 User,
  x:                    X,
}

export function Icon({ name, size = 16, className = '', strokeWidth = 1.75 }: IconProps) {
  const LucideIcon = ICON_MAP[name]

  if (!LucideIcon) {
    return (
      <span
        className={`inline-flex items-center justify-center ${className}`}
        style={{ width: size, height: size }}
      />
    )
  }

  return (
    <span
      className={`inline-flex items-center justify-center ${className}`}
      style={{ width: size, height: size }}
    >
      <LucideIcon size={size} strokeWidth={strokeWidth} aria-hidden="true" focusable="false" />
    </span>
  )
}
