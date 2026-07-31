import { writable } from 'svelte/store'

export const location = writable(window.location.pathname + window.location.search)

window.addEventListener('popstate', () => location.set(window.location.pathname + window.location.search))

export function navigate(to: string, replace = false) {
  if (replace) window.history.replaceState({}, '', to)
  else window.history.pushState({}, '', to)
  location.set(window.location.pathname + window.location.search)
  window.scrollTo({ top: 0, behavior: 'instant' })
}

export function link(node: HTMLAnchorElement) {
  function handle(event: MouseEvent) {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      node.target === '_blank' ||
      node.origin !== window.location.origin
    ) return
    event.preventDefault()
    navigate(node.pathname + node.search + node.hash)
  }
  node.addEventListener('click', handle)
  return { destroy: () => node.removeEventListener('click', handle) }
}

