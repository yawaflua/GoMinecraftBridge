<script lang="ts">
  import { onMount } from 'svelte'
  import { session } from '../lib/session'
  import { api, ApiError } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import Status from '../lib/Status.svelte'
  import StateView from '../lib/StateView.svelte'
  import Dialog from '../lib/Dialog.svelte'
  import { environmentLabel, formatBytes, formatDate, formatDateTime, projectStatus, tagLabel } from '../utils'
  import { link, navigate } from '../lib/router'
  import type { Notification, Project, ProjectVersion } from '../types'
  import { t } from '../lib/i18n'

  export let identifier: string
  export let ownerRoute = false

  let project: Project | null = null
  let versions: ProjectVersion[] = []
  let messages: Notification[] = []
  let loading = true
  let error = ''
  let actionError = ''
  let actionNotice = ''
  let submitting = false
  let showSubmit = false
  let submitComment = ''
  let saving = false
  let editName = ''
  let editSlug = ''
  let editDescription = ''

  $: owner = Boolean(project && $session?.user.id === project.owner_id)
  let currentTab = new URLSearchParams(window.location.search).get('tab') ?? 'overview'
  $: hasVersions = versions.length > 0
  $: canSubmit = owner && hasVersions && (project?.status === 'PROJECT_STATUS_DRAFT' || project?.status === 'PROJECT_STATUS_REJECTED')

  onMount(load)

  async function load() {
    loading = true
    error = ''
    try {
      project = ownerRoute ? await api.projectById(identifier) : await api.projectBySlug(identifier)
      editName = project.name
      editSlug = project.slug
      editDescription = project.description
      const versionResult = await api.versions(project.id)
      versions = versionResult.versions ?? []
      if ($session) {
        try {
          const notificationResult = await api.notifications()
          messages = (notificationResult.notifications ?? []).filter((item) => item.project_id === project?.id)
        } catch {
          messages = []
        }
      }
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Проект не открылся')
    } finally {
      loading = false
    }
  }

  function setTab(tab: string) {
    const base = ownerRoute ? `/projects/${identifier}` : `/project/${identifier}`
    navigate(tab === 'overview' ? base : `${base}?tab=${tab}`)
    currentTab = tab
    if (tab === 'moderation') markMessagesRead()
  }

  function handleTabKey(event: KeyboardEvent) {
    const tabs = owner ? ['overview', 'versions', 'moderation', 'settings'] : ['overview', 'versions']
    const index = tabs.indexOf(currentTab)
    let next = index
    if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
    else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = tabs.length - 1
    else return
    event.preventDefault()
    setTab(tabs[next])
    requestAnimationFrame(() => document.querySelector<HTMLButtonElement>(`.tabs button[data-tab="${tabs[next]}"]`)?.focus())
  }

  async function markMessagesRead() {
    const unread = messages.filter((item) => !item.read).map((item) => item.id)
    if (!unread.length) return
    try {
      await api.markNotificationsRead(unread)
      messages = messages.map((item) => (unread.includes(item.id) ? { ...item, read: true } : item))
    } catch {
      // Reading state is supporting functionality; preserve the thread on failure.
    }
  }

  async function submitForReview() {
    if (!project) return
    submitting = true
    actionError = ''
    try {
      await api.submitProject(project.id, submitComment.trim())
      project = { ...project, status: 'PROJECT_STATUS_PENDING_REVIEW', status_reason: '' }
      showSubmit = false
      actionNotice = $t('Проект отправлен на ревью. Решение появится в обсуждении.')
      setTab('moderation')
    } catch (reason) {
      actionError = reason instanceof ApiError ? reason.message : $t('Не удалось отправить проект')
    } finally {
      submitting = false
    }
  }

  async function saveProject() {
    if (!project) return
    saving = true
    actionError = ''
    try {
      project = await api.updateProject(project.id, {
        name: editName.trim(),
        slug: editSlug.trim(),
        description: editDescription.trim(),
      }, ['name', 'slug', 'description'])
      actionNotice = $t('Изменения проекта сохранены.')
    } catch (reason) {
      actionError = reason instanceof ApiError ? reason.message : $t('Не удалось сохранить проект')
    } finally {
      saving = false
    }
  }

  function releasePath() {
    return project ? `/projects/${project.id}/release` : '#'
  }
</script>

<div class="page project-detail-page">
  {#if loading}
    <StateView kind="loading" title="Открываем проект" message="Получаем описание, версии и состояние ревью."/>
  {:else if error || !project}
    <StateView kind="error" title="Проект не открылся" message={error || 'Проект не найден'}><button class="button tonal" on:click={load}>Повторить</button></StateView>
  {:else}
    <a class="back-link" href={ownerRoute ? '/projects' : '/'} use:link><Icon name="arrow-left" size={20}/> {$t(ownerRoute ? 'Мои проекты' : 'Каталог')}</a>

    <header class="project-header">
      <div class="project-identity">
        <span class="project-large-mark">{project.name.slice(0, 2).toUpperCase()}<i></i></span>
        <div>
          <div class="project-heading-line"><h1>{project.name}</h1>{#if owner}<Status status={project.status}/>{/if}</div>
          <p class="project-slug">/mod/{project.slug}</p>
          <p>{project.description || $t('Описание не добавлено')}</p>
        </div>
      </div>
      <div class="project-actions">
        {#if owner}
          {#if canSubmit}
            <button class="button filled" on:click={() => (showSubmit = true)}><Icon name="shield" size={20}/> {$t('Отправить на ревью')}</button>
          {:else if project.status !== 'PROJECT_STATUS_PENDING_REVIEW' && project.status !== 'PROJECT_STATUS_BANNED'}
            <a class="button filled" href={releasePath()} use:link><Icon name="release" size={20}/> {$t('Новый релиз')}</a>
          {/if}
        {:else if project.latest_version}
          <a class="button filled" href={api.downloadUrl(project.slug)} download><Icon name="download" size={20}/> {$t('Скачать {version}', { version: project.latest_version })}</a>
        {/if}
      </div>
    </header>

    {#if owner}
      <div class="lifecycle-banner" data-tone={projectStatus[project.status].tone}>
        <Status status={project.status} detail/>
        {#if project.status === 'PROJECT_STATUS_DRAFT' && !hasVersions}<a href={releasePath()} use:link>{$t('Добавить первую версию')} <Icon name="arrow-right" size={18}/></a>{/if}
        {#if project.status === 'PROJECT_STATUS_REJECTED' && project.status_reason}<p><strong>{$t('Комментарий:')}</strong> {project.status_reason}</p>{/if}
      </div>
    {/if}

    {#if actionNotice}<div class="inline-message success" role="status"><Icon name="check" size={20}/><span>{actionNotice}</span></div>{/if}
    {#if actionError}<div class="inline-message error" role="alert"><Icon name="warning" size={20}/><span>{actionError}</span></div>{/if}

    <div class="tabs" role="tablist" tabindex="-1" aria-label={$t('Разделы проекта')} on:keydown={handleTabKey}>
      <button data-tab="overview" role="tab" tabindex={currentTab === 'overview' ? 0 : -1} aria-selected={currentTab === 'overview'} class:active={currentTab === 'overview'} on:click={() => setTab('overview')}>{$t('Обзор')}</button>
      <button data-tab="versions" role="tab" tabindex={currentTab === 'versions' ? 0 : -1} aria-selected={currentTab === 'versions'} class:active={currentTab === 'versions'} on:click={() => setTab('versions')}>{$t('Версии')} <span>{versions.length}</span></button>
      {#if owner}<button data-tab="moderation" role="tab" tabindex={currentTab === 'moderation' ? 0 : -1} aria-selected={currentTab === 'moderation'} class:active={currentTab === 'moderation'} on:click={() => setTab('moderation')}>{$t('Обсуждение')} {#if messages.some((message) => !message.read)}<i aria-label={$t('Есть новые сообщения')}></i>{/if}</button>{/if}
      {#if owner}<button data-tab="settings" role="tab" tabindex={currentTab === 'settings' ? 0 : -1} aria-selected={currentTab === 'settings'} class:active={currentTab === 'settings'} on:click={() => setTab('settings')}>{$t('Настройки')}</button>{/if}
    </div>

    {#if currentTab === 'overview'}
      <div class="detail-layout">
        <article class="readme-region">
          <h2>{$t('О проекте')}</h2>
          {#if versions[0]?.readme}<div class="prose">{versions[0].readme}</div>{:else}<p class="muted">{$t('README появится здесь после загрузки версии.')}</p>{/if}
        </article>
        <aside class="facts-pane">
          <h2>{$t('Совместимость')}</h2>
          <dl>
            <div><dt>{$t('Среда')}</dt><dd>{versions[0] ? $t(environmentLabel[versions[0].metadata.environment]) : '—'}</dd></div>
            <div><dt>Bridge ABI</dt><dd>{versions[0]?.metadata.abi_version || '—'}</dd></div>
            <div><dt>API</dt><dd>{versions[0]?.metadata.api_version || '—'}</dd></div>
            <div><dt>{$t('Лицензия')}</dt><dd>{versions[0]?.metadata.licenses?.join(', ') || '—'}</dd></div>
            <div><dt>{$t('Авторы')}</dt><dd>{versions[0]?.metadata.authors?.join(', ') || '—'}</dd></div>
          </dl>
          <h2>{$t('Проект')}</h2>
          <dl><div><dt>{$t('Создан')}</dt><dd>{formatDate(project.created_at)}</dd></div><div><dt>{$t('Последнее обновление')}</dt><dd>{formatDate(project.updated_at)}</dd></div></dl>
        </aside>
      </div>
    {:else if currentTab === 'versions'}
      <section class="versions-region" aria-labelledby="versions-heading">
        <div class="section-heading"><div><h2 id="versions-heading">{$t('Версии')}</h2><p>{$t('Архивы, changelog и совместимость каждого релиза.')}</p></div>{#if owner && project.status !== 'PROJECT_STATUS_BANNED'}<a class="button tonal" href={releasePath()} use:link><Icon name="plus" size={20}/> {$t('Новый релиз')}</a>{/if}</div>
        {#if versions.length === 0}
          <StateView title="Версий пока нет" message={owner ? 'Загрузите ZIP-архив и метаданные — после этого проект можно отправить на ревью.' : 'Автор ещё не опубликовал ни одной версии.'} compact>
            {#if owner}<a class="button filled" href={releasePath()} use:link>{$t('Загрузить версию')}</a>{/if}
          </StateView>
        {:else}
          <div class="version-list">
            {#each versions as version (version.id)}
              <article class="version-row">
                <span class="release-icon"><Icon name="package" size={22}/></span>
                <div class="version-main"><div><h3>{version.version}</h3><span class="version-tag">{$t(tagLabel[version.tag])}</span></div><p>{version.description || version.changelog || $t('Описание релиза не добавлено.')}</p><small>{formatDate(version.created_at)} · {formatBytes(version.size_bytes)} · {$t(environmentLabel[version.metadata.environment])}</small></div>
                <div class="version-compat"><small>ABI</small><strong>{version.metadata.abi_version || '—'}</strong></div>
                <a class="icon-button" href={api.downloadUrl(project.slug, version.version)} download aria-label={$t('Скачать версию {version}', { version: version.version })}><Icon name="download" size={21}/></a>
              </article>
            {/each}
          </div>
        {/if}
      </section>
    {:else if currentTab === 'moderation' && owner}
      <section class="moderation-thread" aria-labelledby="thread-heading">
        <div class="thread-heading"><div><p class="overline">{$t('Проект · {name}', { name: project.name })}</p><h2 id="thread-heading">{$t('Обсуждение с модерацией')}</h2><p>{$t('Здесь появляются системные события и сообщения, связанные с проверкой проекта.')}</p></div><Icon name="chat" size={28}/></div>
        <div class="thread">
          <div class="thread-event"><span><Icon name="file" size={18}/></span><div><strong>{$t('Проект создан')}</strong><p>{$t('Черновик готов к наполнению.')}</p><time>{formatDateTime(project.created_at)}</time></div></div>
          {#if project.status !== 'PROJECT_STATUS_DRAFT'}
            <div class="thread-event"><span><Icon name="shield" size={18}/></span><div><strong>{$t('Проект отправлен на проверку')}</strong><p>{$t('Модерация получила текущую карточку и все версии.')}</p><time>{formatDateTime(project.updated_at)}</time></div></div>
          {/if}
          {#each [...messages].reverse() as message (message.id)}
            <div class="thread-message" class:unread={!message.read}>
              <span class="moderator-avatar"><Icon name={message.system ? 'shield' : 'user'} size={19}/></span>
              <div><div class="message-author"><strong>{$t(message.system ? 'Система BridgeMods' : 'Модерация')}</strong><time>{formatDateTime(message.created_at)}</time></div><p>{message.text}</p></div>
            </div>
          {/each}
          {#if messages.length === 0 && project.status === 'PROJECT_STATUS_PENDING_REVIEW'}
            <div class="thread-wait"><Icon name="clock" size={22}/><div><strong>{$t('Ожидаем решение')}</strong><p>{$t('Новые сообщения появятся автоматически. Произвольные ответы владельца пока не предусмотрены API.')}</p></div></div>
          {/if}
        </div>
        {#if canSubmit}<button class="button filled" on:click={() => (showSubmit = true)}>{$t('Отправить повторно')}</button>{/if}
      </section>
    {:else if currentTab === 'settings' && owner}
      <form class="settings-form" on:submit|preventDefault={saveProject}>
        <div class="section-heading"><div><h2>{$t('Настройки проекта')}</h2><p>{$t('Изменения применятся к публичной карточке.')}</p></div><button class="button filled" type="submit" disabled={saving}>{$t(saving ? 'Сохраняем…' : 'Сохранить')}</button></div>
        <label class="field"><span>{$t('Название')}</span><input bind:value={editName} required maxlength="100"/></label>
        <label class="field"><span>Slug</span><input bind:value={editSlug} required minlength="2" maxlength="64" pattern="[a-z0-9][a-z0-9-]+"/><small>{$t('Изменит публичную ссылку на проект.')}</small></label>
        <label class="field"><span>{$t('Описание')}</span><textarea bind:value={editDescription} rows="6" maxlength="4096"></textarea><small>{editDescription.length}/4096</small></label>
      </form>
    {/if}

    {#if showSubmit}
      <Dialog title={$t('Отправить проект на ревью?')} description={$t('Модерация увидит текущие данные проекта и все загруженные версии.')} close={() => (showSubmit = false)}>
        <form class="dialog-form" on:submit|preventDefault={submitForReview}>
          <label class="field"><span>{$t('Комментарий для модератора')}</span><textarea bind:value={submitComment} rows="4" placeholder={$t('Что важно проверить в этой версии?')}></textarea></label>
          {#if actionError}<div class="inline-message error" role="alert">{actionError}</div>{/if}
          <div class="dialog-actions"><button class="button text" type="button" on:click={() => (showSubmit = false)}>{$t('Отмена')}</button><button class="button filled" type="submit" disabled={submitting}>{$t(submitting ? 'Отправляем…' : 'Отправить')}</button></div>
        </form>
      </Dialog>
    {/if}
  {/if}
</div>
