<script lang="ts">
  import Icon from '../lib/Icon.svelte'
  import { api, ApiError } from '../lib/api'
  import { link, navigate } from '../lib/router'
  import { slugify } from '../utils'
  import { t } from '../lib/i18n'

  let name = ''
  let slug = ''
  let description = ''
  let slugTouched = false
  let slugState: 'idle' | 'checking' | 'available' | 'taken' = 'idle'
  let checkTimer: ReturnType<typeof setTimeout>
  let pending = false
  let error = ''

  $: if (!slugTouched) slug = slugify(name)

  function updateSlug(value: string) {
    slugTouched = true
    slug = slugify(value)
    slugState = 'idle'
    clearTimeout(checkTimer)
    if (/^[a-z0-9][a-z0-9-]{1,63}$/.test(slug)) {
      checkTimer = setTimeout(checkSlug, 450)
    }
  }

  async function checkSlug() {
    slugState = 'checking'
    try {
      slugState = (await api.checkSlug(slug)).available ? 'available' : 'taken'
    } catch {
      slugState = 'idle'
    }
  }

  async function submit() {
    error = ''
    pending = true
    try {
      const project = await api.createProject({ name: name.trim(), slug, description: description.trim() })
      navigate(`/projects/${project.id}?created=1`)
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось создать проект')
    } finally {
      pending = false
    }
  }
</script>

<div class="page editor-page">
  <a class="back-link" href="/projects" use:link><Icon name="arrow-left" size={20}/> {$t('Мои проекты')}</a>
  <header class="page-heading"><p class="overline">{$t('Новый проект')}</p><h1>{$t('Заложите основу')}</h1><p>{$t('Пока это черновик. После создания добавьте хотя бы одну полную версию перед ревью.')}</p></header>

  <div class="editor-layout">
    <form class="editor-form" on:submit|preventDefault={submit}>
      {#if error}<div class="inline-message error" role="alert"><Icon name="warning" size={20}/><span>{error}</span></div>{/if}
      <fieldset class="form-section">
        <legend>{$t('Основная информация')}</legend>
        <p>{$t('Эти данные увидят игроки в каталоге.')}</p>
        <label class="field"><span>{$t('Название проекта')}</span><input bind:value={name} required maxlength="100" placeholder={$t('Например, World Sync')}/><small>{name.length}/100</small></label>
        <label class="field"><span>{$t('Короткое описание')}</span><textarea bind:value={description} maxlength="4096" rows="5" placeholder={$t('Что делает мод и кому он будет полезен?')}></textarea><small>{description.length}/4096</small></label>
      </fieldset>
      <fieldset class="form-section">
        <legend>{$t('Адрес проекта')}</legend>
        <p>{$t('Используется в публичной ссылке и должен быть уникальным.')}</p>
        <label class="field" class:valid={slugState === 'available'} class:invalid={slugState === 'taken'}>
          <span>Slug</span>
          <div class="prefixed-input"><span>bridgemods.dev/mod/</span><input value={slug} on:input={(event) => updateSlug(event.currentTarget.value)} required minlength="2" maxlength="64" pattern="[a-z0-9][a-z0-9-]+" placeholder="world-sync"/></div>
          <small aria-live="polite">{$t(slugState === 'checking' ? 'Проверяем доступность…' : slugState === 'available' ? 'Адрес свободен' : slugState === 'taken' ? 'Этот адрес уже занят' : '2–64 символа: a–z, 0–9 и дефис')}</small>
        </label>
      </fieldset>
      <div class="form-actions"><a class="button text" href="/projects" use:link>{$t('Отмена')}</a><button class="button filled" type="submit" disabled={pending || slugState === 'taken'}>{$t(pending ? 'Создаём…' : 'Создать проект')}{#if !pending}<Icon name="arrow-right" size={20}/>{/if}</button></div>
    </form>
    <aside class="supporting-pane">
      <h2>{$t('Что дальше')}</h2>
      <ol class="progress-steps">
        <li class="current"><span>1</span><div><strong>{$t('Карточка проекта')}</strong><small>{$t('Название, описание и URL')}</small></div></li>
        <li><span>2</span><div><strong>{$t('Первая версия')}</strong><small>{$t('ZIP-архив и метаданные')}</small></div></li>
        <li><span>3</span><div><strong>{$t('Ревью')}</strong><small>{$t('Проверка модерацией')}</small></div></li>
      </ol>
      <div class="support-note"><Icon name="shield" size={22}/><p><strong>{$t('До публикации проект видите только вы и модерация.')}</strong><br/>{$t('После одобрения он появится в общем каталоге.')}</p></div>
    </aside>
  </div>
</div>
