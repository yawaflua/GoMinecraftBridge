<script lang="ts">
  import Icon from '../lib/Icon.svelte'
  import { link } from '../lib/router'
  import { t } from '../lib/i18n'
</script>

<div class="page not-found"><span class="error-code">404</span><h1>{$t('Такой страницы нет')}</h1><p>{$t('Возможно, ссылка устарела или в адресе есть опечатка.')}</p><a class="button filled" href="/" use:link><Icon name="arrow-left" size={20}/> {$t('В каталог')}</a></div>
