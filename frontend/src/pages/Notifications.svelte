<script lang="ts">
  import { onMount } from 'svelte'
  import { api, ApiError } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import StateView from '../lib/StateView.svelte'
  import { formatDateTime } from '../utils'
  import { link } from '../lib/router'
  import type { Notification } from '../types'
  import { t } from '../lib/i18n'

  let notifications: Notification[] = []
  let loading = true
  let error = ''
  let unreadOnly = false
  let marking = false

  onMount(load)

  async function load() {
    loading = true
    error = ''
    try {
      notifications = (await api.notifications(unreadOnly)).notifications ?? []
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Уведомления недоступны')
    } finally {
      loading = false
    }
  }

  async function markAll() {
    marking = true
    try {
      await api.markNotificationsRead([])
      notifications = notifications.map((item) => ({ ...item, read: true }))
      if (unreadOnly) notifications = []
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Уведомления недоступны')
    } finally {
      marking = false
    }
  }

  function toggleFilter() {
    unreadOnly = !unreadOnly
    load()
  }
</script>

<div class="page workspace-page notifications-page">
  <header class="page-heading split-heading">
    <div><p class="overline">{$t('Центр событий')}</p><h1>{$t('Уведомления')}</h1><p>{$t('Решения по ревью, сообщения модерации и системные объявления.')}</p></div>
    {#if notifications.some((item) => !item.read)}<button class="button text" on:click={markAll} disabled={marking}>{$t(marking ? 'Отмечаем…' : 'Прочитать все')}</button>{/if}
  </header>

  <div class="filter-row" aria-label={$t('Фильтры уведомлений')}>
    <button class="filter-chip" class:active={!unreadOnly} on:click={() => unreadOnly && toggleFilter()}>{$t('Все')}</button>
    <button class="filter-chip" class:active={unreadOnly} on:click={() => !unreadOnly && toggleFilter()}>{$t('Непрочитанные')}</button>
  </div>

  {#if loading}
    <div class="notification-list skeleton-list" aria-label={$t('Загрузка')}><div></div><div></div><div></div></div>
  {:else if error}
    <StateView kind="error" title="Уведомления недоступны" message={error}><button class="button tonal" on:click={load}>{$t('Повторить')}</button></StateView>
  {:else if notifications.length === 0}
    <StateView title={unreadOnly ? 'Всё прочитано' : 'Пока тихо'} message={unreadOnly ? 'Новых уведомлений нет. Можно вернуться ко всем событиям.' : 'Здесь появятся решения по ревью и сообщения модерации.'}>
      {#if unreadOnly}<button class="button tonal" on:click={toggleFilter}>{$t('Показать все')}</button>{/if}
    </StateView>
  {:else}
    <div class="notification-list">
      {#each notifications as notification (notification.id)}
        <article class="notification-row" class:unread={!notification.read}>
          <span class="notification-icon"><Icon name={notification.system ? 'shield' : 'chat'} size={21}/></span>
          <div><div class="notification-title"><strong>{notification.system ? 'BridgeMods' : $t('Модерация')}</strong>{#if !notification.read}<span class="unread-marker">{$t('Новое')}</span>{/if}</div><p>{notification.text}</p><time>{formatDateTime(notification.created_at)}</time></div>
          {#if notification.project_id}<a class="button text" href={`/projects/${notification.project_id}?tab=moderation`} use:link>{$t('К проекту')} <Icon name="arrow-right" size={18}/></a>{/if}
        </article>
      {/each}
    </div>
  {/if}
</div>
