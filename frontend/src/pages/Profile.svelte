<script lang="ts">
  import { session, endSession } from '../lib/session'
  import { api, ApiError } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import { initials } from '../utils'
  import { navigate } from '../lib/router'
  import { t } from '../lib/i18n'

  let username = $session?.user.username ?? ''
  let email = $session?.user.email ?? ''
  let minecraftUuid = $session?.user.minecraft_uuid ?? ''
  let password = ''
  let avatar: File | null = null
  let pending = false
  let error = ''
  let notice = ''

  async function save() {
    if (!$session) return
    pending = true
    error = ''
    notice = ''
    try {
      const fields: string[] = []
      const update: Record<string, string> = {}
      if (username !== $session.user.username) { fields.push('username'); update.username = username.trim() }
      if (email !== $session.user.email) { fields.push('email'); update.email = email.trim() }
      if (minecraftUuid !== $session.user.minecraft_uuid) { fields.push('minecraft_uuid'); update.minecraft_uuid = minecraftUuid.trim() }
      if (password) { fields.push('password'); update.password = password }
      if (avatar) {
        const bytes = new Uint8Array(await avatar.arrayBuffer())
        let binary = ''
        bytes.forEach((byte) => (binary += String.fromCharCode(byte)))
        update.avatar = btoa(binary)
        update.avatar_content_type = avatar.type
        fields.push('avatar', 'avatar_content_type')
      }
      if (!fields.length) { notice = $t('Нет изменений для сохранения.'); return }
      const user = await api.updateUser(update, fields)
      session.update((value) => (value ? { ...value, user } : value))
      password = ''
      avatar = null
      notice = $t('Профиль обновлён.')
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось обновить профиль')
    } finally {
      pending = false
    }
  }

  async function logout() {
    await endSession()
    navigate('/')
  }
</script>

<div class="page editor-page profile-page">
  <header class="page-heading"><p class="overline">{$t('Аккаунт')}</p><h1>{$t('Профиль')}</h1><p>{$t('Данные автора, игровой профиль и безопасность входа.')}</p></header>
  {#if $session}
    <div class="profile-layout">
      <aside class="profile-identity">
        {#if $session.user.avatar_url}<img src={$session.user.avatar_url} alt=""/>{:else}<span class="avatar huge">{initials($session.user.username)}</span>{/if}
        <h2>{$session.user.username}</h2><p>{$session.user.email}</p>
        <div class="role-list">{#each $session.user.roles as role (role)}<span>{role.replace('USER_ROLE_', '').toLowerCase()}</span>{/each}</div>
        <button class="button text danger" on:click={logout}><Icon name="logout" size={20}/> {$t('Выйти')}</button>
      </aside>
      <form class="settings-form" on:submit|preventDefault={save}>
        <div class="section-heading"><div><h2>{$t('Данные аккаунта')}</h2><p>{$t('Поля проверяются теми же правилами, что и при регистрации.')}</p></div><button class="button filled" type="submit" disabled={pending}>{$t(pending ? 'Сохраняем…' : 'Сохранить')}</button></div>
        {#if error}<div class="inline-message error" role="alert"><Icon name="warning" size={20}/><span>{error}</span></div>{/if}
        {#if notice}<div class="inline-message success" role="status"><Icon name="check" size={20}/><span>{notice}</span></div>{/if}
        <label class="field"><span>{$t('Имя пользователя')}</span><input bind:value={username} required minlength="3" maxlength="32" pattern="[A-Za-z0-9._-]+"/></label>
        <label class="field"><span>{$t('Почта')}</span><input bind:value={email} type="email" required maxlength="254"/></label>
        <label class="field"><span>{$t('Minecraft UUID')}</span><input bind:value={minecraftUuid} required/></label>
        <label class="field"><span>{$t('Новый пароль')}</span><input bind:value={password} type="password" minlength="8" maxlength="72" autocomplete="new-password"/><small>{$t('Оставьте пустым, чтобы не менять.')}</small></label>
        <label class="field"><span>{$t('Аватар')}</span><input type="file" accept="image/png,image/jpeg,image/webp" on:change={(event) => (avatar = event.currentTarget.files?.[0] ?? null)}/><small>{$t('PNG, JPEG или WebP до 1 МБ.')}</small></label>
      </form>
    </div>
  {/if}
</div>
