<script lang="ts">
  import { onMount } from 'svelte'
  import Shell from './lib/Shell.svelte'
  import StateView from './lib/StateView.svelte'
  import { location, navigate } from './lib/router'
  import { hydrateSession, session, sessionReady } from './lib/session'
  import { isModerator } from './utils'
  import Auth from './pages/Auth.svelte'
  import Discover from './pages/Discover.svelte'
  import MyProjects from './pages/MyProjects.svelte'
  import CreateProject from './pages/CreateProject.svelte'
  import ProjectDetail from './pages/ProjectDetail.svelte'
  import Release from './pages/Release.svelte'
  import Notifications from './pages/Notifications.svelte'
  import Moderation from './pages/Moderation.svelte'
  import Profile from './pages/Profile.svelte'
  import NotFound from './pages/NotFound.svelte'
  import { t } from './lib/i18n'

  onMount(hydrateSession)

  $: path = $location.split('?')[0]
  $: ownerMatch = path.match(/^\/projects\/([^/]+)$/)
  $: releaseMatch = path.match(/^\/projects\/([^/]+)\/release$/)
  $: publicMatch = path.match(/^\/project\/([^/]+)$/)
  $: protectedRoute = path === '/projects' || path === '/new-project' || path === '/notifications' || path === '/profile' || Boolean(ownerMatch) || Boolean(releaseMatch) || path === '/moderation'
  $: if ($sessionReady && protectedRoute && !$session) navigate('/auth', true)
  $: if ($sessionReady && path === '/auth' && $session) navigate('/projects', true)
</script>

{#if !$sessionReady}
  <main class="boot-screen"><div class="brand-mark large" aria-hidden="true"><span></span><span></span><span></span><span></span></div><StateView kind="loading" title={$t('Запускаем BridgeMods')} message={$t('Восстанавливаем безопасную сессию.')} compact/></main>
{:else if path === '/auth'}
  <Auth/>
{:else}
  <Shell {path}>
    {#key path}
      {#if path === '/'}
        <Discover/>
      {:else if path === '/projects' && $session}
        <MyProjects/>
      {:else if path === '/new-project' && $session}
        <CreateProject/>
      {:else if ownerMatch && $session}
        <ProjectDetail identifier={ownerMatch[1]} ownerRoute/>
      {:else if releaseMatch && $session}
        <Release projectId={releaseMatch[1]}/>
      {:else if publicMatch}
        <ProjectDetail identifier={publicMatch[1]}/>
      {:else if path === '/notifications' && $session}
        <Notifications/>
      {:else if path === '/profile' && $session}
        <Profile/>
      {:else if path === '/moderation' && $session && isModerator($session.user.roles)}
        <Moderation/>
      {:else}
        <NotFound/>
      {/if}
    {/key}
  </Shell>
{/if}
