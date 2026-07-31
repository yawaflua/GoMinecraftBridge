<script lang="ts">
  import Icon from '../lib/Icon.svelte'
  import { api, ApiError } from '../lib/api'
  import { link, navigate } from '../lib/router'
  import { setSession } from '../lib/session'
  import { locale, setLocale, t } from '../lib/i18n'

  let mode: 'login' | 'register' = 'login'
  let pending = false
  let error = ''
  let login = ''
  let password = ''
  let email = ''
  let username = ''
  let minecraftUuid = ''
  const eulaUrl = new URL('../../EULA.md', import.meta.url).href

  async function submit() {
    error = ''
    pending = true
    try {
      const response = mode === 'login'
        ? await api.login(login.trim(), password)
        : await api.register({ email: email.trim(), username: username.trim(), password, minecraft_uuid: minecraftUuid.trim() })
      setSession(response)
      navigate('/projects')
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось завершить вход')
    } finally {
      pending = false
    }
  }

  function switchMode(next: 'login' | 'register') {
    mode = next
    error = ''
  }
</script>

<div class="auth-screen">
  <section class="auth-intro" aria-labelledby="auth-heading">
    <div class="auth-top"><a class="back-link" href="/" use:link><Icon name="arrow-left" size={20}/> {$t('Вернуться в каталог')}</a><button class="locale-toggle" type="button" on:click={() => setLocale($locale === 'ru' ? 'en' : 'ru')}>{$locale === 'ru' ? 'EN' : 'RU'}</button></div>
    <div class="auth-copy">
      <span class="auth-cube" aria-hidden="true"><i></i><i></i><i></i></span>
      <h1 id="auth-heading">{#if $locale === 'en'}Mods in Go.<br/>Delivered to Minecraft.{:else}Моды на Go.<br/>Доставлены в Minecraft.{/if}</h1>
      <p>{$t('Публикуйте пакеты для GoMinecraftBridge, проходите прозрачное ревью и доставляйте обновления игрокам.')}</p>
      <ol class="auth-steps" aria-label={$t('Как работает публикация')}>
        <li><span>1</span><div><strong>{$t('Создайте проект')}</strong><small>{$t('Название, URL и понятное описание')}</small></div></li>
        <li><span>2</span><div><strong>{$t('Загрузите версию')}</strong><small>{$t('Архив, совместимость и changelog')}</small></div></li>
        <li><span>3</span><div><strong>{$t('Пройдите ревью')}</strong><small>{$t('Решение появится в обсуждении проекта')}</small></div></li>
      </ol>
    </div>
  </section>

  <section class="auth-panel" aria-label={$t('Авторизация')}>
    <div class="auth-form-wrap">
      <div class="segmented" aria-label={$t('Выберите действие')}>
        <button aria-pressed={mode === 'login'} class:active={mode === 'login'} on:click={() => switchMode('login')}>{$t('Вход')}</button>
        <button aria-pressed={mode === 'register'} class:active={mode === 'register'} on:click={() => switchMode('register')}>{$t('Регистрация')}</button>
      </div>

      <header class="form-heading">
        <h2>{$t(mode === 'login' ? 'С возвращением' : 'Создайте аккаунт')}</h2>
        <p>{$t(mode === 'login' ? 'Продолжите работу над проектами и релизами.' : 'Minecraft UUID связывает аккаунт с игровым профилем.')}</p>
      </header>

      {#if error}
        <div class="inline-message error" role="alert"><Icon name="warning" size={20}/><span>{error}</span></div>
      {/if}

      <form on:submit|preventDefault={submit} class="form-stack">
        {#if mode === 'login'}
          <label class="field">
            <span>{$t('Почта или имя пользователя')}</span>
            <input bind:value={login} autocomplete="username" required placeholder="alex@example.com"/>
          </label>
        {:else}
          <label class="field">
            <span>{$t('Почта')}</span>
            <input bind:value={email} type="email" autocomplete="email" required placeholder="alex@example.com" maxlength="254"/>
          </label>
          <label class="field">
            <span>{$t('Имя пользователя')}</span>
            <input bind:value={username} autocomplete="username" required minlength="3" maxlength="32" pattern="[A-Za-z0-9._-]+" placeholder="alex_dev"/>
            <small>{$t('3–32 латинских символа: буквы, цифры, точка, _ или -')}</small>
          </label>
          <label class="field">
            <span>{$t('Minecraft UUID')}</span>
            <input bind:value={minecraftUuid} required minlength="32" maxlength="36" pattern="[0-9a-fA-F-]+" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/>
          </label>
        {/if}
        <label class="field">
          <span>{$t('Пароль')}</span>
          <input bind:value={password} type="password" autocomplete={mode === 'login' ? 'current-password' : 'new-password'} required minlength="8" maxlength="72"/>
          {#if mode === 'register'}<small>{$t('От 8 до 72 символов')}</small>{/if}
        </label>
        <button class="button filled large" type="submit" disabled={pending} aria-busy={pending}>
          {pending ? $t('Подождите…') : mode === 'login' ? $t('Войти') : $t('Создать аккаунт')}
          {#if !pending}<Icon name="arrow-right" size={20}/>{/if}
        </button>
      </form>
      <p class="legal">{$t('Создавая аккаунт, вы принимаете')} <a href={eulaUrl} target="_blank" rel="noreferrer">EULA</a> {$t('и правила публикации.')}</p>
    </div>
  </section>
</div>
