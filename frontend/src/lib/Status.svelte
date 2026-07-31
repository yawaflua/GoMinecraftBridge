<script lang="ts">
  import Icon from './Icon.svelte'
  import { t } from './i18n'
  import { projectStatus } from '../utils'
  import type { ProjectStatus } from '../types'

  export let status: ProjectStatus
  export let detail = false

  $: config = projectStatus[status] ?? projectStatus.PROJECT_STATUS_UNSPECIFIED
</script>

<span class="status" class:with-detail={detail} data-tone={config.tone}>
  <span class="status-icon">
    <Icon name={config.tone === 'success' ? 'check' : config.tone === 'warning' ? 'clock' : config.tone === 'error' ? 'warning' : 'file'} size={16}/>
  </span>
  <span>
    <span class="status-label">{$t(config.label)}</span>
    {#if detail}<span class="status-detail">{$t(config.detail)}</span>{/if}
  </span>
</span>
