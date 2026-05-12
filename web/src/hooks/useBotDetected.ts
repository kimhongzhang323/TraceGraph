import { useEffect, useState } from 'react'

export function useBotDetected(): boolean {
  const [bot, setBot] = useState(
    () => document.documentElement.getAttribute('data-bot') === '1',
  )

  useEffect(() => {
    if (bot) return
    const observer = new MutationObserver(() => {
      if (document.documentElement.getAttribute('data-bot') === '1') {
        setBot(true)
      }
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-bot'] })
    return () => observer.disconnect()
  }, [bot])

  return bot
}
