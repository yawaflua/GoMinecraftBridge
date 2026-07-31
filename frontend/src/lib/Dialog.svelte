<script lang="ts">
  import { onMount } from 'svelte'
  import Icon from './Icon.svelte'
  import { t } from './i18n'

  export let title: string
  export let description = ''
  export let close: () => void
  let dialog: HTMLDialogElement

  onMount(() => {
    dialog.showModal()
  })

  function handleClose() {
    close()
  }
</script>

<dialog bind:this={dialog} class="dialog" aria-labelledby="dialog-title" on:close={handleClose} on:cancel={handleClose}>
  <div class="dialog-head">
    <div><h2 id="dialog-title">{title}</h2>{#if description}<p>{description}</p>{/if}</div>
    <button class="icon-button" type="button" on:click={() => dialog.close()} aria-label={$t('Закрыть')}><Icon name="close" size={22}/></button>
  </div>
  <slot></slot>
</dialog>
