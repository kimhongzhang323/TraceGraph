import { icons } from 'lucide-react'
import { useEffect, useRef } from 'react'

interface IconProps {
  name: string
  size?: number
  className?: string
  strokeWidth?: number
}

export function Icon({ name, size = 16, className = '', strokeWidth = 1.75 }: IconProps) {
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!ref.current) return
    const key = name
      .split('-')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join('') as keyof typeof icons
    const icon = icons[key]
    if (!icon) { ref.current.innerHTML = ''; return }
    const [, attrs, children] = icon as unknown as [string, Record<string, string>, [string, Record<string, string>][]]
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    Object.entries({ ...attrs, width: size, height: size, 'stroke-width': strokeWidth }).forEach(
      ([k, v]) => svg.setAttribute(k, String(v)),
    )
    children.forEach(([tag, a]) => {
      const el = document.createElementNS('http://www.w3.org/2000/svg', tag)
      Object.entries(a).forEach(([k, v]) => el.setAttribute(k, String(v)))
      svg.appendChild(el)
    })
    ref.current.innerHTML = ''
    ref.current.appendChild(svg)
  }, [name, size, strokeWidth])

  return (
    <span
      ref={ref}
      className={`inline-flex items-center justify-center ${className}`}
      style={{ width: size, height: size }}
    />
  )
}
