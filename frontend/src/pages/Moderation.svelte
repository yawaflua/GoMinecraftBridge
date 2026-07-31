<script lang="ts">
  import { onMount } from 'svelte'
  import { api, ApiError } from '../lib/api'
  import Icon from '../lib/Icon.svelte'
  import StateView from '../lib/StateView.svelte'
  import Status from '../lib/Status.svelte'
  import { environmentLabel, formatDateTime, tagLabel } from '../utils'
  import type { Project, ProjectReviewRequest, ProjectVersion } from '../types'
  import { locale, t } from '../lib/i18n'

  interface QueueItem {
    review: ProjectReviewRequest
    project: Project | null
  }

  let items: QueueItem[] = []
  let selected: QueueItem | null = null
  let versions: ProjectVersion[] = []
  let loading = true
  let detailLoading = false
  let error = ''
  let status = 'REVIEW_STATUS_PENDING'
  let decision: 'REVIEW_DECISION_APPROVE' | 'REVIEW_DECISION_REJECT' = 'REVIEW_DECISION_APPROVE'
  let comment = ''
  let reviewPending = false
  let message = ''
  let messagePending = false
  let notice = ''

  onMount(load)

  async function load() {
    loading = true
    error = ''
    selected = null
    try {
      const response = await api.reviewRequests(status)
      items = await Promise.all((response.review_requests ?? []).map(async (review) => {
        try { return { review, project: await api.projectById(review.project_id) } }
        catch { return { review, project: null } }
      }))
      if (items.length) await selectItem(items[0])
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Очередь недоступна')
    } finally {
      loading = false
    }
  }

  async function selectItem(item: QueueItem) {
    selected = item
    versions = []
    comment = ''
    notice = ''
    detailLoading = true
    try {
      versions = (await api.versions(item.review.project_id)).versions ?? []
    } catch {
      versions = []
    } finally {
      detailLoading = false
    }
  }

  function changeStatus(value: string) {
    status = value
    load()
  }

  async function submitDecision() {
    if (!selected) return
    if (decision === 'REVIEW_DECISION_REJECT' && !comment.trim()) {
      error = $t('Для отклонения нужен понятный комментарий владельцу.')
      return
    }
    reviewPending = true
    error = ''
    notice = ''
    const current = selected
    try {
      await api.reviewProject(current.review.id, decision, comment.trim())
      const approved = decision === 'REVIEW_DECISION_APPROVE'
      const projectName = current.project?.name ?? current.review.project_id
      const text = $locale === 'en'
        ? approved
          ? `Project “${projectName}” has been approved and published.${comment.trim() ? ` Comment: ${comment.trim()}` : ''}`
          : `Changes were requested for project “${projectName}”. ${comment.trim()}`
        : approved
          ? `Проект «${projectName}» одобрен и опубликован.${comment.trim() ? ` Комментарий: ${comment.trim()}` : ''}`
          : `Проект «${projectName}» возвращён на доработку. ${comment.trim()}`
      try {
        await api.notifyProjectOwner(current.review.project_id, current.review.id, text)
        notice = $t(approved ? 'Проект опубликован, владелец получил уведомление.' : 'Проект возвращён на доработку, замечания отправлены.')
      } catch {
        notice = $t('Решение сохранено, но уведомление владельцу отправить не удалось.')
      }
      items = items.filter((item) => item.review.id !== current.review.id)
      selected = null
      versions = []
      if (items.length) await selectItem(items[0])
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось сохранить решение')
    } finally {
      reviewPending = false
    }
  }

  async function sendMessage() {
    if (!selected || !message.trim()) return
    messagePending = true
    error = ''
    try {
      await api.notifyProjectOwner(selected.review.project_id, selected.review.id, message.trim())
      message = ''
      notice = $t('Сообщение появилось в обсуждении проекта владельца.')
    } catch (reason) {
      error = reason instanceof ApiError ? reason.message : $t('Не удалось отправить сообщение')
    } finally {
      messagePending = false
    }
  }
</script>

<div class="moderation-page">
  <header class="moderation-bar">
    <div><p class="overline">{$t('Модерация')}</p><h1>{$t('Очередь ревью')}</h1></div>
    <div class="segmented compact" aria-label={$t('Статус заявок')}>
      <button class:active={status === 'REVIEW_STATUS_PENDING'} on:click={() => changeStatus('REVIEW_STATUS_PENDING')}>{$t('Ожидают')}</button>
      <button class:active={status === 'REVIEW_STATUS_UNSPECIFIED'} on:click={() => changeStatus('REVIEW_STATUS_UNSPECIFIED')}>{$t('Все')}</button>
    </div>
  </header>

  {#if notice}<div class="inline-message success moderation-notice" role="status"><Icon name="check" size={20}/><span>{notice}</span></div>{/if}
  {#if error}<div class="inline-message error moderation-notice" role="alert"><Icon name="warning" size={20}/><span>{error}</span></div>{/if}

  {#if loading}
    <StateView kind="loading" title="Загружаем очередь" message="Получаем заявки и связанные проекты."/>
  {:else if error && !items.length}
    <StateView kind="error" title="Очередь недоступна" message={error}><button class="button tonal" on:click={load}>{$t('Повторить')}</button></StateView>
  {:else if items.length === 0 && !selected}
    <StateView title="Очередь пуста" message={status === 'REVIEW_STATUS_PENDING' ? 'Все новые проекты проверены.' : 'Заявок на ревью пока нет.'}/>
  {:else}
    <div class="review-workspace" class:detail-open={selected}>
      <aside class="review-queue" aria-label={$t('Заявки на ревью')}>
        <div class="queue-summary"><strong>{items.length}</strong><span>{$t(status === 'REVIEW_STATUS_PENDING' ? 'ожидают проверки' : 'заявок')}</span></div>
        {#each items as item (item.review.id)}
          <button class="review-row" class:selected={selected?.review.id === item.review.id} on:click={() => selectItem(item)}>
            <span class="review-mark">{item.project?.name.slice(0, 2).toUpperCase() ?? '??'}</span>
            <span><strong>{item.project?.name ?? $t('Проект недоступен')}</strong><small>/{item.project?.slug ?? item.review.project_id}</small><time>{formatDateTime(item.review.created_at)}</time></span>
            <Icon name="arrow-right" size={18}/>
          </button>
        {/each}
      </aside>

      {#if selected}
        <section class="review-detail" aria-labelledby="review-heading">
          <button class="back-link mobile-only" on:click={() => (selected = null)}><Icon name="arrow-left" size={20}/> {$t('К очереди')}</button>
          {#if !selected.project}
            <StateView kind="error" title="Проект недоступен" message="Заявка существует, но данные проекта не удалось получить." compact/>
          {:else}
            <header class="review-title">
              <div><div class="project-heading-line"><h2 id="review-heading">{selected.project.name}</h2><Status status={selected.project.status}/></div><p>{selected.project.description || $t('Описание отсутствует')}</p></div>
              <a class="button text" href={`/projects/${selected.project.id}`} target="_blank" rel="noreferrer">{$t('Открыть проект')} <Icon name="arrow-right" size={18}/></a>
            </header>

            <div class="review-facts"><div><small>{$t('Отправлено')}</small><strong>{formatDateTime(selected.review.created_at)}</strong></div><div><small>{$t('Владелец')}</small><strong title={selected.review.submitted_by}>{selected.review.submitted_by.slice(0, 8)}…</strong></div><div><small>{$t('Версии')}</small><strong>{versions.length}</strong></div></div>

            {#if selected.review.review_comment}<div class="submission-comment"><strong>{$t('Комментарий к заявке')}</strong><p>{selected.review.review_comment}</p></div>{/if}

            <section class="review-section"><h3>{$t('Проверка версий')}</h3>
              {#if detailLoading}<div class="thin-progress" aria-label={$t('Загрузка версий')}></div>
              {:else if versions.length === 0}<div class="inline-message error"><Icon name="warning" size={20}/><span>{$t('У проекта нет доступных версий. Одобрять его не следует.')}</span></div>
              {:else}<div class="review-version-list">{#each versions as version (version.id)}<article><span class="release-icon"><Icon name="package" size={21}/></span><div><strong>{version.version} · {$t(tagLabel[version.tag])}</strong><small>{$t(environmentLabel[version.metadata.environment])} · ABI {version.metadata.abi_version || '—'} · API {version.metadata.api_version || '—'}</small><p>{version.description || $t('Без описания')}</p></div></article>{/each}</div>{/if}
            </section>

            <section class="review-section message-composer"><h3>{$t('Сообщение владельцу')}</h3><p>{$t('Сообщение появится в подвкладке «Обсуждение» этого проекта.')}</p><label class="field"><span>{$t('Текст сообщения')}</span><textarea bind:value={message} rows="3" maxlength="10000" placeholder={$t('Уточните настройку или попросите исправление…')}></textarea></label><button class="button tonal" on:click={sendMessage} disabled={messagePending || !message.trim()}>{$t(messagePending ? 'Отправляем…' : 'Отправить сообщение')}<Icon name="chat" size={19}/></button></section>

            {#if selected.review.status === 'REVIEW_STATUS_PENDING'}
              <section class="decision-panel"><h3>{$t('Решение по проекту')}</h3><div class="decision-options"><label class:active={decision === 'REVIEW_DECISION_APPROVE'}><input type="radio" bind:group={decision} value="REVIEW_DECISION_APPROVE"/><span><Icon name="check" size={20}/><strong>{$t('Одобрить')}</strong><small>{$t('Проект появится в каталоге')}</small></span></label><label class="error-choice" class:active={decision === 'REVIEW_DECISION_REJECT'}><input type="radio" bind:group={decision} value="REVIEW_DECISION_REJECT"/><span><Icon name="warning" size={20}/><strong>{$t('Вернуть на доработку')}</strong><small>{$t('Владелец сможет исправить и отправить снова')}</small></span></label></div><label class="field"><span>{$t(decision === 'REVIEW_DECISION_REJECT' ? 'Причина возврата' : 'Комментарий (необязательно)')}</span><textarea bind:value={comment} required={decision === 'REVIEW_DECISION_REJECT'} rows="4" placeholder={$t(decision === 'REVIEW_DECISION_REJECT' ? 'Что именно нужно исправить?' : 'Короткое сообщение владельцу')}></textarea></label><button class="button filled" class:danger-filled={decision === 'REVIEW_DECISION_REJECT'} on:click={submitDecision} disabled={reviewPending || !versions.length}>{$t(reviewPending ? 'Сохраняем решение…' : decision === 'REVIEW_DECISION_APPROVE' ? 'Одобрить и опубликовать' : 'Вернуть на доработку')}</button></section>
            {/if}
          {/if}
        </section>
      {/if}
    </div>
  {/if}
</div>
