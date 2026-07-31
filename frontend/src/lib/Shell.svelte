<script lang="ts">
  import { onMount } from 'svelte'
  import Icon from './Icon.svelte'
  import { api } from './api'
  import { locale, setLocale, t } from './i18n'
  import { link, navigate } from './router'
  import { endSession, session } from './session'
  import { initials, isModerator } from '../utils'

  export let path = '/'

  let unread = 0
  let theme = 'light'

  $: user = $session?.user
  $: moderation = user ? isModerator(user.roles) : false

  const destinations = [
    { href: '/', label: 'Каталог', icon: 'explore', match: (value: string) => value === '/' || value.startsWith('/project/') },
    { href: '/projects', label: 'Мои проекты', icon: 'grid', auth: true, match: (value: string) => value.startsWith('/projects') || value.startsWith('/new-project') },
    { href: '/notifications', label: 'Уведомления', icon: 'bell', auth: true, badge: true, match: (value: string) => value.startsWith('/notifications') },
  ]

  onMount(async () => {
    theme = localStorage.getItem('bridgemods.theme') ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    applyTheme()
    if (user) {
      try {
        unread = (await api.notifications()).unread_count
      } catch {
        unread = 0
      }
    }
  })

  function applyTheme() {
    document.documentElement.dataset.theme = theme
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#11140f' : '#f7f9f2')
  }

  function toggleTheme() {
    theme = theme === 'dark' ? 'light' : 'dark'
    localStorage.setItem('bridgemods.theme', theme)
    applyTheme()
  }

  async function logout() {
    await endSession()
    navigate('/')
  }

  function active(item: (typeof destinations)[number]) {
    return item.match(path)
  }
</script>

<a class="skip-link" href="#main">{$t('Перейти к содержимому')}</a>
<div class="app-shell">
  <aside class="nav-drawer" aria-label="Основная навигация">
    <a class="brand" href="/" use:link aria-label={$t('BridgeMods — на главную')}>
      <span class="brand-mark" aria-hidden="true"><span></span><span></span><span></span><span></span></span>
      <span class="brand-name">Bridge<span>Mods</span></span>
    </a>

    <nav class="destinations">
      {#each destinations as item (item.href)}
        {#if !item.auth || user}
          <a class:active={active(item)} href={item.href} use:link aria-current={active(item) ? 'page' : undefined}>
            <span class="nav-icon"><Icon name={item.icon} size={22}/></span>
            <span class="nav-label">{$t(item.label)}</span>
            {#if item.badge && unread > 0}<span class="badge" aria-label={`${unread} ${$locale === 'en' ? 'unread' : 'непрочитанных'}`}>{unread > 99 ? '99+' : unread}</span>{/if}
          </a>
        {/if}
      {/each}
      {#if moderation}
        <a class:active={path.startsWith('/moderation')} href="/moderation" use:link aria-current={path.startsWith('/moderation') ? 'page' : undefined}>
          <span class="nav-icon"><Icon name="shield" size={22}/></span>
          <span class="nav-label">{$t('Модерация')}</span>
        </a>
      {/if}
    </nav>

    <div class="drawer-spacer"></div>

    <button class="nav-action" type="button" on:click={toggleTheme} aria-label={$t(theme === 'dark' ? 'Включить светлую тему' : 'Включить тёмную тему')}>
      <span class="nav-icon"><Icon name={theme === 'dark' ? 'sun' : 'moon'} size={22}/></span>
      <span class="nav-label">{$t(theme === 'dark' ? 'Светлая тема' : 'Тёмная тема')}</span>
    </button>
    <button class="nav-action" type="button" on:click={() => setLocale($locale === 'ru' ? 'en' : 'ru')} aria-label={$locale === 'ru' ? 'Switch to English' : 'Переключить на русский'}>
      <span class="locale-symbol">{$locale === 'ru' ? 'EN' : 'RU'}</span><span class="nav-label">{$locale === 'ru' ? 'English' : 'Русский'}</span>
    </button>

    {#if user}
      <a class="account-row" class:active={path.startsWith('/profile')} href="/profile" use:link>
        {#if user.avatar_url}<img src={user.avatar_url} alt=""/>{:else}<span class="avatar">{initials(user.username)}</span>{/if}
        <span class="account-copy"><strong>{user.username}</strong><small>{user.email}</small></span>
      </a>
      <button class="logout-button" type="button" on:click={logout} aria-label={$t('Выйти из аккаунта')}><Icon name="logout" size={20}/></button>
    {:else}
      <a class="button filled drawer-login" href="/auth" use:link>{$t('Войти')}</a>
    {/if}
  </aside>

  <header class="compact-app-bar">
    <a class="brand" href="/" use:link aria-label={$t('BridgeMods — на главную')}>
      <span class="brand-mark" aria-hidden="true"><span></span><span></span><span></span><span></span></span>
      <span class="brand-name">Bridge<span>Mods</span></span>
    </a>
    <div class="compact-actions">
      <button class="icon-button locale-button" type="button" on:click={() => setLocale($locale === 'ru' ? 'en' : 'ru')} aria-label={$locale === 'ru' ? 'Switch to English' : 'Переключить на русский'}>{$locale === 'ru' ? 'EN' : 'RU'}</button>
      <button class="icon-button" type="button" on:click={toggleTheme} aria-label={$t(theme === 'dark' ? 'Включить светлую тему' : 'Включить тёмную тему')}>
        <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={22}/>
      </button>
      {#if user}
        <a class="avatar small" href="/profile" use:link aria-label={`Профиль ${user.username}`}>{initials(user.username)}</a>
      {:else}
        <a class="button text" href="/auth" use:link>{$t('Войти')}</a>
      {/if}
    </div>
  </header>

  <main id="main" tabindex="-1">
    <slot></slot>
  </main>

  {#if user}
    <nav class="bottom-nav" aria-label={$t('Основная навигация')}>
      {#each destinations as item (item.href)}
        <a class:active={active(item)} href={item.href} use:link aria-current={active(item) ? 'page' : undefined}>
          <span class="bottom-icon"><Icon name={item.icon} size={22}/>{#if item.badge && unread > 0}<span class="badge dot" aria-label={`${unread} ${$locale === 'en' ? 'unread' : 'непрочитанных'}`}></span>{/if}</span>
          <span>{$t(item.label)}</span>
        </a>
      {/each}
      {#if moderation}
        <a class:active={path.startsWith('/moderation')} href="/moderation" use:link><span class="bottom-icon"><Icon name="shield" size={22}/></span><span>{$t('Модерация')}</span></a>
      {/if}
    </nav>
  {/if}
</div>
