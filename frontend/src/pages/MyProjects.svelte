<script lang="ts">
  import { onMount } from 'svelte'
  import { session } from '../lib/session'
  import { api, ApiError } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import Status from '../lib/Status.svelte'
  import StateView from '../lib/StateView.svelte'
  import { formatDate } from '../utils'
  import { link } from '../lib/router'
  import type { Project } from '../types'
  import { t } from '../lib/i18n'

  let projects: Project[] = []
  let loading = true
  let error = ''

  onMount(load)

  async function load() {
    if (!$session) return
    loading = true
    error = ''
    try {
      projects = (await api.myProjects()).projects ?? []
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Проекты не загрузились')
    } finally {
      loading = false
    }
  }
</script>

<div class="page workspace-page">
  <header class="page-heading split-heading">
    <div><p class="overline">{$t('Рабочая область')}</p><h1>{$t('Мои проекты')}</h1><p>{$t('Управляйте описанием, релизами и отправкой на ревью.')}</p></div>
    <a class="button filled" href="/new-project" use:link><Icon name="plus" size={20}/> {$t('Создать проект')}</a>
  </header>

  {#if loading}
    <div class="project-list skeleton-list" aria-label={$t('Загрузка')}><div></div><div></div><div></div></div>
  {:else if error}
    <StateView kind="error" title="Проекты не загрузились" message={error}><button class="button tonal" on:click={load}>{$t('Повторить')}</button></StateView>
  {:else if projects.length === 0}
    <StateView title="Здесь появятся ваши проекты" message="Создайте первый проект, загрузите рабочую версию и отправьте её на проверку.">
      <a class="button filled" href="/new-project" use:link><Icon name="plus" size={20}/> {$t('Создать проект')}</a>
    </StateView>
  {:else}
    <div class="project-list" aria-label={$t('Список ваших проектов')}>
      {#each projects as project (project.id)}
        <a class="project-row" href={`/projects/${project.id}`} use:link>
          <span class="project-row-mark">{project.name.slice(0, 2).toUpperCase()}</span>
          <span class="project-row-main"><strong>{project.name}</strong><small>{project.description || $t('Описание не добавлено')}</small></span>
          <span class="project-row-version"><small>{$t('Последняя версия')}</small><strong>{project.latest_version || $t('Нет версий')}</strong></span>
          <Status status={project.status}/>
          <span class="project-row-date"><small>{$t('Обновлён')}</small>{formatDate(project.updated_at)}</span>
          <Icon name="arrow-right" size={20}/>
        </a>
      {/each}
    </div>
  {/if}
</div>
