<script lang="ts">
  export let name: string
  export let size = 24
  export let filled = false
</script>

<svg
  class:filled
  width={size}
  height={size}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.8"
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"
>
  {#if name === 'explore'}
    <circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2.1 4.9-4.9 2.1 2.1-4.9 4.9-2.1Z"/>
  {:else if name === 'grid'}
    <rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/>
  {:else if name === 'bell'}
    <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 8h18c0-1-3-1-3-8"/><path d="M10 21h4"/>
  {:else if name === 'shield'}
    <path d="M12 3 5 6v5c0 4.7 2.9 8 7 10 4.1-2 7-5.3 7-10V6l-7-3Z"/><path d="m9 12 2 2 4-4"/>
  {:else if name === 'user'}
    <circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>
  {:else if name === 'search'}
    <circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>
  {:else if name === 'plus'}
    <path d="M12 5v14M5 12h14"/>
  {:else if name === 'arrow-left'}
    <path d="m15 18-6-6 6-6"/>
  {:else if name === 'arrow-right'}
    <path d="m9 18 6-6-6-6"/>
  {:else if name === 'download'}
    <path d="M12 3v12m-5-5 5 5 5-5M5 20h14"/>
  {:else if name === 'package'}
    <path d="m4 7 8-4 8 4-8 4-8-4Z"/><path d="m4 7v10l8 4 8-4V7M12 11v10"/>
  {:else if name === 'code'}
    <path d="m8 9-3 3 3 3m8-6 3 3-3 3m-2-9-4 12"/>
  {:else if name === 'check'}
    <path d="m5 12 4 4L19 6"/>
  {:else if name === 'clock'}
    <circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>
  {:else if name === 'warning'}
    <path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 4h.01"/>
  {:else if name === 'chat'}
    <path d="M21 14a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v7Z"/>
  {:else if name === 'edit'}
    <path d="m14 5 5 5M4 20l3.5-.7L20 6.8 17.2 4 4.7 16.5 4 20Z"/>
  {:else if name === 'release'}
    <path d="M12 3v12m0-12-4 4m4-4 4 4M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/>
  {:else if name === 'moon'}
    <path d="M20 15.5A8.5 8.5 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5Z"/>
  {:else if name === 'sun'}
    <circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>
  {:else if name === 'logout'}
    <path d="M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5M14 8l4 4-4 4m4-4H8"/>
  {:else if name === 'close'}
    <path d="M6 6l12 12M18 6 6 18"/>
  {:else if name === 'file'}
    <path d="M6 2h8l4 4v16H6V2Z"/><path d="M14 2v5h5M9 13h6m-6 4h6"/>
  {:else if name === 'dots'}
    <circle cx="5" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none"/>
  {:else}
    <circle cx="12" cy="12" r="9"/><path d="M12 8h.01M11 12h1v5h1"/>
  {/if}
</svg>
