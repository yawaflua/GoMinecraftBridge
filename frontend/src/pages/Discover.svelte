<script lang="ts">
  import { onMount } from 'svelte'
  import Icon from '../lib/Icon.svelte'
  import StateView from '../lib/StateView.svelte'
  import { api, ApiError } from '../lib/api'
  import { link } from '../lib/router'
  import type { SearchProjectResult } from '../types'
  import { locale, t } from '../lib/i18n'

  let items: SearchProjectResult[] = []
  let search = new URLSearchParams(window.location.search).get('q') ?? ''
  let appliedSearch = search
  let loading = true
  let error = ''

  onMount(load)

  async function load() {
    loading = true
    error = ''
    try {
      const response = await api.searchProjects(appliedSearch)
      items = response.projects ?? []
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Каталог недоступен')
    } finally {
      loading = false
    }
  }

  function submitSearch() {
    appliedSearch = search.trim()
    const url = appliedSearch ? `/?q=${encodeURIComponent(appliedSearch)}` : '/'
    history.replaceState({}, '', url)
    load()
  }

  function clearSearch() {
    search = ''
    appliedSearch = ''
    history.replaceState({}, '', '/')
    load()
  }
</script>

<div class="page discover-page">
  <header class="discover-hero">
    <div class="hero-copy">
      <p class="overline">{$t('Экосистема GoMinecraftBridge')}</p>
      <h1>{#if $locale === 'en'}Find a mod,<br/><span>built with Go</span>{:else}Найдите мод,<br/><span>собранный на Go</span>{/if}</h1>
      <p>{$t('Проверенные расширения для сервера и клиента. Один архив — и Bridge загрузит нужную версию.')}</p>
    </div>
    <div class="hero-art" aria-hidden="true">
      <div class="code-card"><span>package</span> main<br/><br/><i>func</i> Init() &#123;<br/>&nbsp;&nbsp;bridge.<b>Register</b>()<br/>&#125;</div>
      <div class="voxel-cluster"><i></i><i></i><i></i><i></i><i></i></div>
    </div>
  </header>

  <form class="search-bar" role="search" on:submit|preventDefault={submitSearch}>
    <Icon name="search" size={24}/>
    <label class="sr-only" for="catalog-search">{$t('Поиск модов')}</label>
    <input id="catalog-search" bind:value={search} placeholder={$t('Поиск по названию, URL или описанию')} maxlength="200"/>
    {#if search}<button class="icon-button" type="button" on:click={clearSearch} aria-label={$t('Очистить поиск')}><Icon name="close" size={20}/></button>{/if}
    <button class="button filled" type="submit">{$t('Найти')}</button>
  </form>

  <section class="catalog" aria-labelledby="catalog-heading">
    <div class="section-heading">
      <div><p class="overline">{$t('Каталог')}</p><h2 id="catalog-heading">{appliedSearch ? $t('Результаты для «{query}»', { query: appliedSearch }) : $t('Свежие проекты')}</h2></div>
      {#if !loading && !error}<span class="result-count">{items.length} {$locale === 'en' ? (items.length === 1 ? 'project' : 'projects') : (items.length === 1 ? 'проект' : 'проектов')}</span>{/if}
    </div>

    {#if loading}
      <div class="project-grid" aria-label={$t('Загрузка проектов')}>
        {#each [0, 1, 2, 3, 4, 5] as index (index)}<div class="project-card skeleton"><div></div><span></span><span></span><span></span></div>{/each}
      </div>
    {:else if error}
      <StateView kind="error" title="Каталог недоступен" message={error}><button class="button tonal" on:click={load}>{$t('Повторить')}</button></StateView>
    {:else if items.length === 0}
      <StateView title={appliedSearch ? 'Ничего не найдено' : 'Каталог пока пуст'} message={appliedSearch ? 'Попробуйте другой запрос или покажите все проекты.' : 'Опубликованные после ревью проекты появятся здесь.'}>
        {#if appliedSearch}<button class="button tonal" on:click={clearSearch}>{$t('Сбросить поиск')}</button>{/if}
      </StateView>
    {:else}
      <div class="project-grid">
        {#each items as item, index (item.project.id)}
          <article class="project-card">
            <a class="card-link" href={`/project/${item.project.slug}`} use:link aria-label={`${$locale === 'en' ? 'Open' : 'Открыть'} ${item.project.name}`}></a>
            <div class="project-visual" data-variant={index % 4} aria-hidden="true">
              <span class="mini-cube"></span><span class="project-monogram">{item.project.name.slice(0, 2).toUpperCase()}</span>
            </div>
            <div class="project-card-body">
              <div class="project-title-row"><h3>{item.project.name}</h3>{#if item.project.latest_version}<span class="version-label">v{item.project.latest_version}</span>{/if}</div>
              <p>{item.project.description || $t('Автор пока не добавил описание проекта.')}</p>
              <div class="project-meta"><span><Icon name="package" size={17}/> {item.project.slug}</span><span><Icon name="check" size={17}/> {$t('Проверен')}</span></div>
            </div>
          </article>
        {/each}
      </div>
    {/if}
  </section>
</div>
