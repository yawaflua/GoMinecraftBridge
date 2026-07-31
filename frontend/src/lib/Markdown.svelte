<script lang="ts">
  import DOMPurify from 'dompurify'
  import { marked } from 'marked'

  export let content: string
  export let compact = false

  function renderMarkdown(value: string) {
    const parsed = marked.parse(value ?? '', { async: false, breaks: true, gfm: true }) as string
    const sanitized = DOMPurify.sanitize(parsed, {
      FORBID_TAGS: ['audio', 'button', 'form', 'iframe', 'input', 'style', 'video'],
      FORBID_ATTR: ['style'],
    })
    const template = document.createElement('template')
    template.innerHTML = sanitized

    for (const anchor of template.content.querySelectorAll('a[href]')) {
      try {
        const target = new URL(anchor.getAttribute('href') ?? '', window.location.href)
        if (target.origin !== window.location.origin) {
          anchor.setAttribute('target', '_blank')
          anchor.setAttribute('rel', 'noopener noreferrer')
        }
      } catch {
        anchor.removeAttribute('href')
      }
    }
    for (const image of template.content.querySelectorAll('img')) {
      image.setAttribute('loading', 'lazy')
      image.setAttribute('decoding', 'async')
    }
    return template.innerHTML
  }

  $: rendered = renderMarkdown(content)
</script>

<!-- eslint-disable-next-line svelte/no-at-html-tags -- sanitized by DOMPurify above -->
<div class="markdown" class:compact>{@html rendered}</div>
