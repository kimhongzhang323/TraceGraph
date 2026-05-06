import type { AnchorHTMLAttributes, ButtonHTMLAttributes } from 'react'
import { Icon } from './Icon'

type Variant = 'primary' | 'ghost'
type Size = 'sm' | 'md' | 'lg'

interface BaseProps {
  variant?: Variant
  size?: Size
  icon?: string
  iconRight?: string
  className?: string
}

type ButtonProps = BaseProps & ButtonHTMLAttributes<HTMLButtonElement> & { as?: 'button' }
type AnchorProps = BaseProps & AnchorHTMLAttributes<HTMLAnchorElement> & { as: 'a' }

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-[12.5px] rounded-lg',
  md: 'h-10 px-4 text-[13.5px] rounded-lg',
  lg: 'h-12 px-6 text-[15px] rounded-full',
}
const variants: Record<Variant, string> = {
  primary: 'bg-ink-950 text-white hover:bg-ink-800 dark:bg-white dark:text-ink-950 dark:hover:bg-ink-100',
  ghost:   'border hairline text-ink-950 dark:text-white hover:bg-ink-50 dark:hover:bg-ink-900',
}

const base = 'inline-flex items-center justify-center gap-2 font-medium tracking-tight transition-all duration-200 select-none whitespace-nowrap'

export function Button(props: ButtonProps | AnchorProps) {
  const { variant = 'primary', size = 'md', icon, iconRight, className = '', children } = props
  const cls = `${base} ${sizes[size]} ${variants[variant]} ${className}`
  const iconSize = size === 'lg' ? 16 : 14
  const inner = (
    <>
      {icon && <Icon name={icon} size={iconSize} />}
      {children}
      {iconRight && <Icon name={iconRight} size={iconSize} />}
    </>
  )
  if (props.as === 'a') {
    const { as: _a, variant: _v, size: _s, icon: _i, iconRight: _ir, ...rest } = props
    return <a className={cls} {...rest}>{inner}</a>
  }
  const { as: _a, variant: _v, size: _s, icon: _i, iconRight: _ir, ...rest } = props as ButtonProps
  return <button className={cls} {...rest}>{inner}</button>
}
