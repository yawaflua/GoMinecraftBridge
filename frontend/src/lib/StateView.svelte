<script lang="ts">
  import Icon from './Icon.svelte'
  import { t } from './i18n'
  export let kind: 'loading' | 'empty' | 'error' = 'empty'
  export let title: string
  export let message: string
  export let compact = false
</script>

<div class="state-view" class:compact aria-live={kind === 'loading' ? 'polite' : undefined}>
  {#if kind === 'loading'}
    <div class="progress-ring" aria-hidden="true"></div>
  {:else}
    <div class="state-symbol" data-kind={kind}><Icon name={kind === 'error' ? 'warning' : 'package'} size={28}/></div>
  {/if}
  <div>
    <h2>{$t(title)}</h2>
    <p>{$t(message)}</p>
    <slot></slot>
  </div>
</div>
