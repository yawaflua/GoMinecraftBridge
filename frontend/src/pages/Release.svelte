<script lang="ts">
  import { onMount } from 'svelte'
  import { session } from '../lib/session'
  import { api, ApiError, fileToBase64 } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import StateView from '../lib/StateView.svelte'
  import { link, navigate } from '../lib/router'
  import { formatBytes } from '../utils'
  import type { PluginEnvironment, Project, VersionTag } from '../types'
  import { t } from '../lib/i18n'

  export let projectId: string

  let project: Project | null = null
  let loading = true
  let loadError = ''
  let pending = false
  let error = ''
  let version = ''
  let description = ''
  let changelog = ''
  let readme = ''
  let tag: VersionTag = 'VERSION_TAG_RELEASE'
  let environment: PluginEnvironment = 'PLUGIN_ENVIRONMENT_BOTH'
  let modSlug = ''
  let metadataDescription = ''
  const protocolVersion = '3'
  let licenses = ''
  let authors = $session?.user.username ?? ''
  let archive: File | null = null
  $: changelogPlaceholder = $t('## Изменения\n- Добавлена синхронизация миров')
  $: readmePlaceholder = $t('# World Sync\nОписание установки и настройки…')

  onMount(async () => {
    try {
      project = await api.projectById(projectId)
      modSlug = project.slug
    } catch (reason) {
      loadError = reason instanceof ApiError ? reason.message : $t('Проект не открылся')
    } finally {
      loading = false
    }
  })

  function chooseFile(event: Event) {
    const file = (event.currentTarget as HTMLInputElement).files?.[0] ?? null
    error = ''
    if (file && file.size > 64 * 1024 * 1024) {
      error = $t('Архив больше 64 МБ. Выберите файл меньшего размера.')
      archive = null
      ;(event.currentTarget as HTMLInputElement).value = ''
      return
    }
    archive = file
  }

  async function publish() {
    if (!project || !archive) {
      error = $t('Выберите архив версии.')
      return
    }
    error = ''
    pending = true
    try {
      const data = await fileToBase64(archive)
      await api.uploadVersion(project.id, {
        version: version.trim(),
        description: description.trim(),
        changelog: changelog.trim(),
        readme,
        tag,
        metadata: {
          slug: modSlug.trim(),
          description: metadataDescription.trim() || description.trim(),
          licenses: licenses.split(',').map((value) => value.trim()).filter(Boolean),
          authors: authors.split(',').map((value) => value.trim()).filter(Boolean),
          abi_version: protocolVersion,
          api_version: protocolVersion,
          environment,
        },
        archive: { content_type: archive.type || 'application/zip', data },
      })
      navigate(`/projects/${project.id}?tab=versions&released=1`)
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось загрузить версию')
    } finally {
      pending = false
    }
  }
</script>

<div class="page editor-page release-page">
  {#if loading}
    <StateView kind="loading" title="Готовим релиз" message="Проверяем проект и доступ к публикации."/>
  {:else if loadError || !project}
    <StateView kind="error" title="Релиз недоступен" message={loadError || 'Проект не найден'}/>
  {:else}
    <a class="back-link" href={`/projects/${project.id}?tab=versions`} use:link><Icon name="arrow-left" size={20}/> {project.name}</a>
    <header class="page-heading"><p class="overline">{$t('Новая версия')}</p><h1>{$t('Подготовьте релиз')}</h1><p>{$t('Архив и метаданные образуют одну неизменяемую версию. Проверьте совместимость перед загрузкой.')}</p></header>

    <form class="release-layout" on:submit|preventDefault={publish}>
      <div class="release-form">
        {#if error}<div class="inline-message error" role="alert"><Icon name="warning" size={20}/><span>{error}</span></div>{/if}

        <fieldset class="form-section">
          <legend>{$t('Архив и версия')}</legend>
          <p>{$t('Загрузите собранный ZIP или бинарный пакет до 64 МБ.')}</p>
          <label class="drop-zone" class:has-file={archive}>
            <input type="file" accept=".zip,application/zip,application/octet-stream" required on:change={chooseFile}/>
            <span class="drop-icon"><Icon name={archive ? 'check' : 'release'} size={26}/></span>
            {#if archive}<span><strong>{archive.name}</strong><small>{$t('{size} · нажмите, чтобы заменить', { size: formatBytes(archive.size) })}</small></span>{:else}<span><strong>{$t('Выберите архив версии')}</strong><small>{$t('ZIP или application/octet-stream, не более 64 МБ')}</small></span>{/if}
          </label>
          <div class="field-row">
            <label class="field"><span>{$t('Версия')}</span><input bind:value={version} required maxlength="64" placeholder="1.0.0"/><small>{$t('До 64 символов')}</small></label>
            <label class="field"><span>{$t('Канал')}</span><select bind:value={tag}><option value="VERSION_TAG_RELEASE">Release</option><option value="VERSION_TAG_BETA">Beta</option><option value="VERSION_TAG_ALPHA">Alpha</option></select></label>
          </div>
          <label class="field"><span>{$t('Краткое описание версии')}</span><textarea bind:value={description} rows="3" maxlength="4096" placeholder={$t('Главные изменения этого релиза')}></textarea><small>{description.length}/4096</small></label>
        </fieldset>

        <fieldset class="form-section">
          <legend>{$t('Совместимость')}</legend>
          <p>{$t('Bridge использует эти поля, чтобы подобрать подходящий пакет.')}</p>
          <div class="field-row">
            <label class="field"><span>{$t('Среда запуска')}</span><select bind:value={environment} required><option value="PLUGIN_ENVIRONMENT_BOTH">{$t('Клиент и сервер')}</option><option value="PLUGIN_ENVIRONMENT_SERVER">{$t('Только сервер')}</option><option value="PLUGIN_ENVIRONMENT_CLIENT">{$t('Только клиент')}</option></select></label>
            <label class="field"><span>{$t('Slug пакета')}</span><input bind:value={modSlug} required placeholder="world-sync"/></label>
          </div>
          <div class="field-row">
            <label class="field"><span>{$t('Версия Bridge ABI')}</span><input value={protocolVersion} readonly/></label>
            <label class="field"><span>{$t('Версия API')}</span><input value={protocolVersion} readonly/></label>
          </div>
          <label class="field"><span>{$t('Авторы')}</span><input bind:value={authors} required placeholder="alex_dev, maria"/><small>{$t('Несколько значений разделяйте запятыми')}</small></label>
          <label class="field"><span>{$t('Лицензии')}</span><input bind:value={licenses} required placeholder="MIT"/><small>{$t('Например: MIT, Apache-2.0')}</small></label>
          <label class="field"><span>{$t('Описание пакета')}</span><textarea bind:value={metadataDescription} rows="3" placeholder={$t('Техническое назначение пакета')}></textarea></label>
        </fieldset>

        <fieldset class="form-section">
          <legend>{$t('Документация')}</legend>
          <p>{$t('README показывается на странице проекта, changelog — рядом с версией.')}</p>
          <label class="field"><span>Changelog</span><textarea bind:value={changelog} rows="7" maxlength="50000" placeholder={changelogPlaceholder}></textarea><small>{changelog.length}/50 000</small></label>
          <label class="field"><span>README</span><textarea bind:value={readme} rows="12" maxlength="1000000" placeholder={readmePlaceholder}></textarea><small>{readme.length.toLocaleString('ru-RU')}/1 000 000</small></label>
        </fieldset>

        <div class="form-actions"><a class="button text" href={`/projects/${project.id}?tab=versions`} use:link>{$t('Отмена')}</a><button class="button filled" type="submit" disabled={pending}>{$t(pending ? 'Загружаем архив…' : 'Создать версию')}{#if !pending}<Icon name="release" size={20}/>{/if}</button></div>
      </div>

      <aside class="release-summary">
        <p class="overline">{$t('Перед публикацией')}</p>
        <h2>{version || $t('Новая версия')}</h2>
        <dl><div><dt>{$t('Проект')}</dt><dd>{project.name}</dd></div><div><dt>{$t('Канал')}</dt><dd>{tag.replace('VERSION_TAG_', '').toLowerCase()}</dd></div><div><dt>{$t('Среда')}</dt><dd>{$t(environment === 'PLUGIN_ENVIRONMENT_BOTH' ? 'Клиент и сервер' : environment === 'PLUGIN_ENVIRONMENT_SERVER' ? 'Сервер' : 'Клиент')}</dd></div><div><dt>{$t('Архив')}</dt><dd>{archive ? formatBytes(archive.size) : $t('Не выбран')}</dd></div></dl>
        <div class="support-note"><Icon name="warning" size={21}/><p>{$t('После загрузки содержимое версии изменить нельзя. Proto позволяет редактировать только её метаданные.')}</p></div>
      </aside>
    </form>
  {/if}
</div>
