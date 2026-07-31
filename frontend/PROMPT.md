# Material 3 для web: конституция решений

> Версия 3.0 · Редакция: 23 июля 2026 года.  
> [`PROMPT.md`](PROMPT.md) — компактная runtime-инструкция для coding agent. Этот файл — подробный reference: как выбирать композицию, компоненты и проверять результат.

## 0. Как пользоваться документами

- Для конкретной задачи передай агенту `PROMPT.md` и продуктовый контекст.
- Открывай этот файл, когда нужны component recipe, adaptive pattern, пример композиции, исключение или источник.
- Не вставляй оба файла целиком в один prompt без необходимости: повторение ослабляет приоритет главных правил.
- Примеры ниже — стартовые стратегии, а не готовые экраны. Сначала докажи отношение между объектами, затем выбирай pattern.

Material 3 / Material You — не preset, не «большие радиусы + пастель» и не копия Google-приложения. Это согласованная грамматика:

```text
контекст
→ сущности и отношения
→ информационная и визуальная иерархия
→ canonical composition
→ компоненты
→ semantic tokens
→ interaction + motion
→ render verification
```

Интерфейс ощущается как Material 3, когда роли цвета, surface, type, shape, state и motion совместно объясняют структуру и действия. Наличие карточек или токенов само по себе ничего не гарантирует.

---

## 1. Приоритеты и критерии отказа

### 1.1 Порядок разрешения конфликтов

Если требования сталкиваются, выбирай в таком порядке:

1. **Пользовательская цель и сохранение функциональности.** Главный сценарий должен завершаться реальным результатом.
2. **Accessibility, понятность и предсказуемость.** Красивое решение не оправдывает потерянный focus или скрытое действие.
3. **Архитектура проекта.** Переиспользуй stack, компоненты, маршруты и данные; не переписывай продукт ради стилизации.
4. **Material 3 semantics и adaptive behavior.** Роли, компоненты и композиция должны соответствовать смыслу.
5. **Выразительность и бренд.** Они усиливают главное, но не заменяют пункты выше.

### 1.2 Fail-gates

Работа **не готова независимо от общего качества**, если верно хотя бы одно:

- главный сценарий сломан, подменён мокапом или не даёт resulting state;
- видимый control не работает либо выглядит доступным, оставаясь декоративным;
- основной сценарий нельзя пройти клавиатурой или focus теряется;
- layout только уменьшается и ломается вместо осмысленной перестройки;
- `primary`, `error`, status, selected или `on-*` colors используются не по роли;
- интерфейс выдаёт себя за Material только скруглениями, тенями и сеткой карточек;
- loading/error/empty уничтожают контекст и не дают recovery там, где оно необходимо;
- агент заявляет о браузерной, accessibility или test-проверке, которую не выполнял.

### 1.3 Двенадцать обязательных правил

1. Сначала продуктовая модель, затем styling.
2. Композиция следует отношениям, но проверяется по подходящим canonical layouts.
3. Один контекст имеет один доминирующий action; остальные получают меньший emphasis.
4. Destination, action, selection, active state, status и error не взаимозаменяемы.
5. Компоненты используют semantic roles, а не случайные значения.
6. Surface tones создают глубину раньше divider и shadow.
7. Shape сообщает тип, состояние или hierarchy; full/pill не применяется ко всему.
8. Каждый применимый interaction state входит в контракт компонента.
9. Motion показывает cause, origin, destination или direction.
10. Adaptive меняет navigation, число видимых regions и порядок работы, а не только размеры.
11. Accessibility проектируется вместе с component anatomy.
12. Финальное решение оценивается по отрендеренному UI, а не только по коду.

---

## 2. Модель до кода

Перед визуальным решением кратко зафиксируй:

1. **Пользователь и результат:** кто выполняет работу и что считается завершением.
2. **Главный сценарий:** вход → действия → resulting state; возможные ошибки и recovery.
3. **Сущности:** identity, ключевые свойства, отношения, статусы и жизненный цикл.
4. **Иерархия:** primary, secondary и supporting content; что видно за первые пять секунд.
5. **Действия:** одно главное действие текущего контекста, вторичные и destructive actions.
6. **Навигация:** устойчивые destinations, внутренние representations и временные commands.
7. **Adaptive composition:** что одновременно видно при разных доступных ширине **и высоте**.
8. **Visual character:** tokens проекта и один доменно обусловленный выразительный жест.
9. **Interaction/motion:** состояния, feedback, source → destination и reduced motion.
10. **Проверка:** каким действием, viewport и edge state решение будет опровергаться.

Если на эти вопросы нет ответа, добавление UI-компонентов преждевременно.

---

## 3. Грамматика объектов и композиции

### 3.1 Уровни

```text
screen / route
├── region
│   ├── group
│   │   └── component
│   └── group
└── temporary layer
```

| Уровень | Вопрос | Примеры |
|---|---|---|
| Screen / route | Какая устойчивая работа или state представлены? | очередь заявок, detail заказа, редактор |
| Region | Какую часть работы обслуживает область? | navigation, list, details, filters, supporting tools |
| Group | Какие элементы отвечают на один вопрос? | identity строки, поле с feedback, row actions |
| Component | Какая конкретная семантика и interaction нужны? | button, list row, field, tab, dialog |
| Temporary layer | Что появилось из trigger и должно закрыться? | menu, tooltip, bottom sheet, dialog |

### 3.2 Primary, secondary, supporting

- **Primary content** непосредственно нужен для результата и доминирует размером, placement или контрастом.
- **Secondary content** помогает изменить/сравнить primary object, но не конкурирует с ним.
- **Supporting content** даёт metadata, объяснение, историю, инструменты или последствия.

За пять секунд пользователь должен понять:

1. где находится;
2. с каким объектом/состоянием работает;
3. что здесь главное;
4. какой следующий шаг доступен.

Если все regions имеют одинаковые площадь, tone, radius и elevation, hierarchy отсутствует.

### 3.3 Минимально достаточная связь

Используй средства от самых лёгких к самым сильным:

1. proximity;
2. alignment;
3. whitespace;
4. typography;
5. surface tone;
6. divider;
7. containment;
8. elevation/overlap.

Card — позднее решение. Если связь уже читается через ось и spacing, контейнер не нужен.

### 3.4 Обязательные семантические различия

| Понятие | Значение | Типичный сигнал |
|---|---|---|
| Destination | устойчивое место/раздел | navigation item + selected indicator |
| Action | операция над объектом/данными | button, menu item, FAB |
| Representation | равноправный view того же context | tab |
| Selected | выбор пользователя | indicator + state layer + semantics |
| Active | текущий route или идущий процесс | position, label, progress |
| Status | состояние данных | текст + icon/shape/color при необходимости |
| Error | проблема, мешающая результату | причина + место + fix/retry |

Не превращай всё перечисленное в одну коллекцию цветных pills.

### 3.5 Базовые отношения

- **Navigation → content:** выбранный destination остаётся видимым; heading подтверждает место.
- **List → detail:** selection обновляет связанную область; scroll, selection и focus сохраняются.
- **Entity → metadata → actions:** identity доминирует, metadata спокойна, actions предсказуемы.
- **Label → control → feedback:** label, input, supporting text и error образуют одну группу.
- **Trigger → temporary layer:** origin, initial focus, закрытие и focus return определены.
- **Source → destination:** раскрываемая сущность сохраняет identity через content, position, shape или motion.
- **Action → feedback → result:** feedback не подменяет изменение данных или состояния.

---

## 4. Выбор композиции

### 4.1 Сначала отношения, затем canonical layout

После предметной модели проверь три официальных canonical pattern:

| Pattern | Когда подходит | Compact | Expanded |
|---|---|---|---|
| **List-detail** | коллекция и выбранная сущность нужны последовательно/одновременно | list и detail — отдельные состояния с Back | list и detail рядом; optional extra только при пользе |
| **Feed / adaptive grid** | поток равноправных самостоятельных единиц | одна колонка | несколько колонок по минимально полезной ширине unit |
| **Supporting pane** | основной объект требует связанного context/tool | supporting content ниже, на отдельном route или в sheet | main + supporting рядом |

Canonical layout — проверенная стартовая стратегия, не обязательная пропорция. Не превращай пример `180dp`, `50/50` или `70/30` из платформенного guide в глобальный token. Размеры выводятся из содержимого и доступного окна.

Не выбирай feed только потому, что можно нарисовать cards. Табличные записи, иерархический список или плотная очередь часто требуют list/table, а не grid.

### 4.2 Adaptive — это состояние доступного окна

- Ориентируйся на **доступную** ширину и высоту, а не на «телефон/планшет/desktop».
- Пересчитывай composition при resize, orientation, split-screen и изменении container.
- Различай логические pane roles и количество panes, видимых сейчас.
- Проверяй не только breakpoint, но и промежуточные размеры, где content начинает конфликтовать.
- Hinge/occlusion и posture складных устройств требуют отдельных platform APIs; width class их не описывает.

Официальные Android window size classes заданы в `dp` и включают Compact, Medium, Expanded, Large и Extra-large. Для web близкие точки `600`, `840`, `1200`, `1600` CSS px могут быть **начальными тестовыми ориентирами**, но не нормативом: breakpoint ставится там, где конкретная композиция перестаёт работать.

Пример web-стратегии:

| Доступное окно | Ожидаемое поведение |
|---|---|
| Compact | одна primary region; secondary regions становятся следующим состоянием, sheet или раскрываемой группой |
| Medium | navigation rail или компактная боковая navigation; main + ограниченный supporting context при достаточной высоте |
| Expanded | persistent navigation; list-detail/supporting panes при реальной пользе |
| Large / extra-large | стабильная многорегиональная работа; ограниченная длина текста/форм; пустота не заполняется бессмысленным pane |

### 4.3 Adaptive navigation

- Destinations остаются логически стабильными при переходе bar ↔ rail ↔ drawer.
- Не добавляй bottom navigation продукту с одним destination.
- Не превращай «Создать», «Сохранить», «Экспортировать» в destination.
- Compact-height может потребовать более короткой navigation даже при широкой ширине.
- Готовые Compose adaptive components можно использовать в Android-проекте, но их default policy не является универсальным законом web.

### 4.4 Component decision matrix

| Нужно выразить | Выбирай | Не подменяй этим |
|---|---|---|
| Перейти в устойчивый раздел | navigation bar/rail/drawer, link | button, chip |
| Выполнить главное действие context | filled button или один FAB | tab, nav item |
| Переключить view того же context | tabs | primary navigation, stepper |
| Выбрать один вариант из малого набора | radio или single-select segmented button | tabs, chips без selection semantics |
| Выбрать несколько независимых вариантов | checkbox | switch для submit-time выбора |
| Немедленно включить setting | switch | checkbox с отдельным Save, если изменение мгновенное |
| Фильтровать collection | filter chips, fields, select | status badges |
| Показать короткие contextual actions | anchored menu | dialog |
| Получить одно блокирующее решение | dialog | полноценный route/form |
| Дать связанный мобильный supporting content | bottom sheet | всегда открытый modal |
| Представить последовательные records | list/table | floating card на каждую строку |
| Представить самостоятельные визуальные units | cards/grid | таблицу, замаскированную cards |
| Сообщить краткий некритичный результат | snackbar | field error, единственное доказательство success |

---

## 5. Material foundations

### 5.1 Semantic color

Компонент получает цвет по роли, не по внешнему названию.

**Accent pairs**

- `primary` / `on-primary` — главное действие и редкий сильный акцент;
- `primary-container` / `on-primary-container` — спокойная акцентная поверхность;
- `secondary` и container pair — supporting emphasis;
- `tertiary` и container pair — редкий дополнительный contrast;
- `error` и container pair — ошибка/опасное последствие, не обычный status.

**Surface roles**

```text
surface
surface-container-lowest
surface-container-low
surface-container
surface-container-high
surface-container-highest
```

Не нужно использовать все уровни на одном экране. Соседние регионы различаются настолько, чтобы hierarchy читалась без полосатого фона.

- `on-surface` — основной text/icons;
- `on-surface-variant` — supporting text/metadata;
- `outline` — важная boundary;
- `outline-variant` — тихий divider;
- inverse roles — временная контрастная surface, когда схема это предусматривает.

**Правила**

- Любая цветная surface использует соответствующий `on-*` role.
- `primary` не заливает половину экрана только «для бренда».
- Color не является единственным признаком status/selection/error.
- Light и dark — две согласованные схемы, а не filter/invert.
- Проверяй фактические пары, включая focus, charts и disabled states.
- Для новой схемы можно использовать [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/), затем мапить roles в tokens проекта.

### 5.2 Typography

M3-группы ролей:

| Role | Работа |
|---|---|
| Display | редкий hero moment или главная крупная метрика |
| Headline | screen и крупный region |
| Title | entity, component, group |
| Body | чтение и объяснение |
| Label | controls, navigation, tabs, compact metadata |

На экране обычно достаточно 4–6 styles. Не делай весь UI bold. Display не заменяет heading hierarchy. Для сравниваемых чисел используй tabular figures, если шрифт поддерживает. Long-form text обычно удобнее при 45–75 символах в строке.

### 5.3 Shape hierarchy

Практическая отправная шкала, а не обязательные значения каждого проекта:

```css
:root {
  --md-sys-shape-extra-small: 4px;
  --md-sys-shape-small: 8px;
  --md-sys-shape-medium: 12px;
  --md-sys-shape-large: 16px;
  --md-sys-shape-extra-large: 28px;
  --md-sys-shape-full: 999px;
}
```

- Одинаковые component families имеют согласованную базовую форму.
- Small/medium shape чаще подходит плотным controls и workspaces.
- Large/extra-large концентрируется в важных containers или expressive moment.
- Full shape подходит chips, buttons и indicators, но не каждому panel.
- Shape change может поддержать selection/morph, но не быть единственным сигналом.

### 5.4 Spacing и density

4px-grid и шаги `4, 8, 12, 16, 24, 32, 48, 64` — полезная исходная система, а не запрет на оптическую коррекцию.

- Внутри группы spacing меньше, чем между группами.
- Плотность следует частоте работы и объёму данных.
- Визуально маленький control может иметь более крупную invisible hit area.
- Consumer UI не обязан быть пустым; B2B UI не обязан быть тесным.
- Формы, long-form text и таблицы не растягиваются на всю extra-large ширину.

### 5.5 Elevation

Сначала tonal elevation, затем shadow только для отделения/overlap.

| Условная роль | Пример |
|---|---|
| Base | основной canvas, встроенный content |
| Raised | app bar/rail, sticky region |
| Overlay | menu, tooltip, sheet |
| Modal | dialog, активный modal layer |

Не создавай grid из одинаково «парящих» containers. Shadow не означает важность.

### 5.6 State layers

Стартовые Material-ориентиры, если design system проекта не задаёт свои:

| State | Примерная opacity state layer |
|---|---:|
| Hover | 8% |
| Focus | 10% |
| Pressed | 10% |
| Dragged | 16% |
| Disabled content | 38% |
| Disabled container | 12% |

Это defaults, не замена contrast testing. Keyboard focus имеет отдельный явно видимый indicator.

### 5.7 Material Symbols и icons

- Используй один icon family/style в одном продукте; не смешивай случайные outline, emoji и filled packs.
- Для Material Symbols согласуй `opsz`, `wght`, `FILL` и `GRAD` с размером и emphasis; не меняй axes хаотично между соседними controls.
- Icon наследует semantic color и оптически выравнивается с label.
- Не используй icon ради украшения каждого heading.
- Неоднозначная или редкая action получает видимый label; tooltip не заменяет accessible name.
- Selected/active icon может менять fill, если это последовательно и не является единственным сигналом.

### 5.8 M3 Expressive как профиль, а не режим «всё громче»

Уместно:

- hero/celebratory moment;
- ключевой creation action;
- media, lifestyle, consumer discovery;
- один source-to-destination transition;
- брендовый контраст scale, type, shape или motion.

Сдерживай:

- формы с риском ошибки;
- settings;
- плотные tables и operational queues;
- repeated rows;
- critical flows, где predictability важнее novelty.

Expressive не означает одновременно огромный type, wavy shapes, яркие containers и emphasized motion на каждом элементе. Выбери 1–2 согласованных оси выразительности и оставь рабочий слой спокойным.

---

## 6. Component recipes

Формат каждого recipe: **когда → anatomy → default → states → не использовать как**.

### 6.1 Top app bar и contextual app bar

**Когда:** identity текущего screen, navigation affordance и 1–3 screen-level actions.

**Anatomy:** leading navigation → title → optional supporting identity → trailing actions/overflow.

**Default:** title подтверждает route; leading Back появляется только при реальном возврате; редкие actions уходят в overflow. При scroll bar может менять elevation/tone, но не прыгать без причины.

**States:** normal, scrolled, contextual selection, narrow overflow.

**Не использовать как:** hero-banner, склад всех filters или второй ряд primary navigation.

### 6.2 Navigation bar, rail и drawer

**Когда:** небольшой устойчивый набор top-level destinations.

**Anatomy:** destination icon + label + selected indicator; optional badge только для meaningful count/state.

**Default:** одинаковый order и labels во всех adaptive variants. Bar чаще compact; rail/drawer — при достаточном окне. Конкретный variant определяется доступными width/height и продуктом.

**States:** rest, hover, focus, pressed, selected, optional badge.

**Не использовать как:** command toolbar, список row actions или wizard steps.

### 6.3 Buttons, icon buttons и FAB

**Когда:** действие над текущим object/context.

**Anatomy:** optional leading icon + concise verb label; icon button имеет accessible name; FAB — icon и при необходимости label.

**Default emphasis:**

- filled — одно главное действие group;
- filled tonal — важное supporting;
- outlined — альтернатива;
- text — тихое/contextual;
- FAB — одна частая screen-level creation action.

**States:** hover/focus/pressed/disabled/loading; pending сохраняет ширину и блокирует duplicate submit.

**Не использовать как:** destination, status chip или декоративный Material-силуэт. FAB не заменяет submit формы.

### 6.4 Search, filters и chips

**Когда:** поиск/сужение collection.

**Anatomy:** search icon + input/query + clear + optional filter trigger; active filters рядом с результатами; result count/status доступен.

**Default:** обычная search bar для inline query; expanded search view — только если поиск становится отдельной задачей с history/suggestions/results. Filter chips отражают реальное состояние query и могут сниматься.

**States:** empty query, typing, suggestions, loading, results, no results, error, active filters.

**Не использовать как:** giant decorative pill, navigation или набор status badges. No-results предлагает очистить/изменить filters, а не «создать данные» без связи.

### 6.5 Cards, lists и settings rows

**Cards — когда:** самостоятельная entity/theme повторяется как unit, выбирается, перемещается или имеет общую surface/action.

**Card anatomy:** media/identity → title → supporting content → optional actions/status.

**Lists — когда:** последовательная collection, scanning и сравнение важнее визуальной самостоятельности.

**List row anatomy:** optional leading → headline → supporting/metadata → trailing status/value/action.

**Settings row anatomy:** setting label + explanation → current value/control; вся row активируется только если это ожидаемо и не конфликтует с вложенным control.

**States:** selected, focused, disabled, loading placeholder, empty collection.

**Не использовать как:** card вокруг каждого paragraph/row. Не вкладывай card в card. Row action не перекрывает identity.

### 6.6 Text fields и forms

**Когда:** ввод/редактирование данных.

**Anatomy:** persistent label → control/value → optional prefix/suffix → supporting/error text.

**Default:** группируй по смыслу, сохраняй введённое при recoverable error, показывай constraints до ошибки, когда возможно. Placeholder — пример, не label.

**States:** empty, focus, filled, disabled/read-only, validating, error, success where useful.

**Не использовать как:** floating decorative bar или замену read-only text. Не валидируй пустое поле агрессивно до meaningful interaction.

### 6.7 Tabs и segmented buttons

**Tabs — когда:** sibling representations одного устойчивого context.

**Segmented buttons — когда:** малый набор взаимно исключающих views/options или компактный multi-select, если это ясно.

**Anatomy:** concise labels, optional icons, единый selected indicator/segment surface.

**Default:** tabs сохраняют context и поддерживают Arrow/Home/End keyboard model; segmented control не разрастается на десяток choices.

**States:** selected, hover, focus, pressed, disabled.

**Не использовать как:** top-level navigation, wizard или action row.

### 6.8 Switch, checkbox, radio и slider

| Control | Используй для | Поведение |
|---|---|---|
| Switch | setting, которое применяется немедленно | изменение сразу отражается в system state; label описывает enabled condition |
| Checkbox | независимый boolean или multi-select перед submit | может ждать Save/Apply; group label нужен |
| Radio | один вариант из видимого набора | один selected; Arrow navigation в group |
| Slider | приблизительный выбор в диапазоне | показывай значение/единицу, keyboard step и min/max |

Не меняй данные немедленно через checkbox, если интерфейс обещает Apply. Не используй switch как action «Запустить». Не применяй slider там, где нужно точное число без дополнительного input.

### 6.9 Menus, dialogs и bottom sheets

**Menu:** anchored список коротких contextual actions. Initial focus, arrows, Escape, focus return.

**Dialog:** блокирующее решение с accessible name, коротким content и обычно 1–2 actions. Destructive label называет действие и объект.

**Bottom sheet:** связанный supporting content/actions на compact или временная задача, естественно исходящая из нижнего края. Modal sheet управляет focus; standard/non-modal не должен неожиданно блокировать background.

Не используй dialog для обычной navigation, большой settings area или длинной формы. Не используй sheet только потому, что экран мобильный.

### 6.10 Snackbar, tooltip и badge

**Snackbar:** краткий некритичный завершённый результат; максимум одно action, часто Undo. Resulting state уже изменён.

**Tooltip:** краткое название/пояснение control по hover **и focus**; не хранит critical instructions.

**Badge:** маленький count/status, меняющий решение пользователя. Для больших значений используй согласованное сокращение/limit и accessible text.

Не показывай field error в snackbar. Tooltip не исправляет icon-only button без accessible name. Badge не украшение.

### 6.11 Progress, skeleton, empty, no-results и error

| State | Recipe |
|---|---|
| Determinate progress | известен объём; показывай значение/этап, если полезно |
| Indeterminate progress | длительность неизвестна; animation прекращается сразу после result |
| Skeleton | повторяет ожидаемую anatomy и сохраняет layout; shimmer не бесконечен |
| Empty | объясняет отсутствие сущностей и даёт релевантный first step |
| No results | сохраняет query/filters и предлагает изменить их |
| Error | сообщает, что произошло/сохранилось, и даёт retry/fix |
| Success | новое устойчивое состояние видно в object/collection, не только в toast |

State относится к region/object. Не заменяй весь экран spinner, если navigation и независимые regions уже доступны.

### 6.12 Table / data grid

**Когда:** точное сравнение records по общим columns, сортировка, selection или bulk actions.

**Anatomy:** caption/heading → toolbar/filter → header cells → rows → optional pagination/status. Числа выравниваются для сравнения; actions имеют устойчивую column/position.

**Default:** native table semantics для read-only tabular data; ARIA grid только при настоящем spreadsheet-like keyboard interaction. На compact выбери meaningful horizontal strategy: приоритетные columns, detail route или controlled scroll — не превращай каждую строку автоматически в card.

**States:** sort, row focus/selection, loading, empty, error, bulk selection.

**Не использовать как:** декоративный dashboard. Не делай custom grid semantics без реализации ожидаемой keyboard model.

### 6.13 Data visualization

Chart выбирается по аналитическому вопросу:

- одно headline value часто лучше chart;
- magnitude, part-to-whole и change-over-time требуют разных forms;
- categorical color остаётся закреплённым за entity;
- не используй rainbow palette и dual y-axis по умолчанию;
- interactive marks имеют увеличенные hit targets, focus и tooltip;
- точные значения доступны в table/text alternative;
- dark palette проверяется отдельно.

---

## 7. Good / bad: быстрые коррекции

| Плохо | Почему | Лучше |
|---|---|---|
| Card вокруг каждой секции и строки | containment перестаёт что-либо значить | regions через spacing/tone; cards только самостоятельным units |
| `border-radius: 24px` на всём | shape hierarchy исчезает | шкала shapes по component family и importance |
| Три filled buttons рядом | primary action неразличим | один filled, остальные tonal/outlined/text |
| Все labels/status/counts — pills | разные семантики выглядят одинаково | status text/icon, metadata text, chip только для choice/filter |
| Иконка без label у редкой action | affordance приходится угадывать | text button/menu item или icon + visible label |
| Dialog открывает большой editor | modality блокирует нормальную работу | route, pane или full-screen state |
| Bottom bar содержит Save/Create | action притворяется destination | app bar/FAB/button в контексте объекта |
| Табличные records разложены cards | сравнение columns затруднено | table/list + detail |
| Snackbar — единственный success | пользователь не видит новый state | обновить object/collection, snackbar оставить дополнением |
| Пустой dashboard с выдуманными KPI | визуальная полнота подменяет продукт | реальный task, entities и достижимые states |
| Fade-in всего экрана при каждом update | motion не объясняет изменение | локальный state feedback/source transition |
| Gradient glow + glassmorphism | модный слой не связан с Material semantics | tonal surfaces, ясный accent и controlled elevation |

---

## 8. Worked compositions

Это не templates. Каждый пример показывает, как отношения приводят к разным структурам.

### 8.1 List → detail: очередь обращений

```text
Expanded
[rail] [queue: search + filters + rows] [selected ticket detail + actions]

Compact
[list route] → select ticket → [detail route + Back]
```

- **Hierarchy:** detail — primary после selection; list остаётся navigation context на wide.
- **Primary action:** `Ответить` внутри detail, не глобальный FAB.
- **Surfaces:** rail raised; list и detail разделены tone/divider, не nested cards.
- **States:** list skeleton/empty/error; selected row; detail pending send + resulting message.
- **Adaptive:** selection и list scroll сохраняются; Back возвращает к выбранной row.
- **Motion:** row → detail сохраняет title/identity; reduced motion использует мгновенную смену с корректным focus.

### 8.2 Search → filters → results: каталог

```text
Compact:  app bar / search / filter chips / one-column results
Expanded: app bar / search / filter pane | adaptive result grid
```

- **Hierarchy:** query и result count выше filters; item identity сильнее metadata.
- **Primary action:** открыть result; filters — controls, не destinations.
- **States:** suggestions, loading, no results с `Сбросить фильтры`, network retry.
- **Adaptive:** filters могут стать modal sheet на compact; selected filters остаются видимы.
- **Cards:** только если result — самостоятельный visual unit; плотный catalogue может использовать list.

### 8.3 Создание/редактирование: форма договора

```text
[app bar: Back · title · Save]
[form column: parties / dates / terms]
[supporting summary or validation context on wide]
```

- **Hierarchy:** форма — primary; summary — supporting pane, не равная card-grid.
- **Primary action:** один `Сохранить`; destructive action в overflow/danger region.
- **Surfaces:** группы полей через headings/spacing; card только для самостоятельного повторяемого object.
- **States:** dirty, saving, recoverable server error, field errors, saved result.
- **Adaptive:** supporting pane уходит ниже/на отдельный state; form width ограничен.
- **Focus:** submit error ведёт к summary/первому invalid field.

### 8.4 Settings со switch rows

```text
[settings heading]
Section
  Notifications          [switch]
  Send weekly summary    [switch]
  Language               [current value >]
```

- Switch применяется немедленно; checkbox используется только при последующем Apply.
- Label формулирует enabled state, supporting text объясняет последствие.
- Section separation строится spacing/divider, не floating card на каждую row.
- Pending/error остаются у setting; rollback объясняется.
- Compact и expanded могут иметь одинаковую content column с ограниченной шириной — лишний pane не обязателен.

### 8.5 Operational workspace + supporting pane

```text
Expanded
[rail] [primary schedule/canvas] [selected item tools/history]

Compact
[primary schedule] → item tools as route or bottom sheet
```

- **Primary region:** canvas/расписание получает большую площадь.
- **Supporting pane:** существует только при выбранном item и реальной пользе.
- **Actions:** contextual toolbar меняется с selection; destination rail остаётся стабильным.
- **States:** no selection, selection, optimistic move with rollback, conflict error.
- **Motion:** selected item связывается с pane; reorder/move показывает старое и новое место.

### 8.6 Data workspace / dashboard

```text
[app bar + range/filter]
[one primary metric/trend]
[data table or operational queue]
[small supporting summaries]
```

- Главная metric может быть выразительной, но не все KPI равноправны.
- Cards допустимы для самостоятельных summaries; records остаются table/list.
- Filters изменяют реальные данные и отражаются в heading/range.
- Loading сохраняет geometry; chart error не скрывает независимую table.
- Expanded добавляет полезное сравнение, а не пустые декоративные панели.

---

## 9. Interaction contract

### 9.1 State machine

```text
rest
├── hover
├── focus
├── pressed
├── selected
└── disabled

trigger
→ immediate feedback
→ pending/loading when needed
→ success/resulting state
   or recoverable error
→ retry / undo / fix
→ focus + announcement
```

Для каждого действия ответь:

1. trigger и доступные input methods;
2. feedback в первые мгновения;
3. local state/data change;
4. pending и защита от duplicate action;
5. confirmed result;
6. error и сохранённый context;
7. retry/undo/confirmation;
8. итоговый focus;
9. announcement assistive technology.

### 9.2 Optimistic и confirmed

- **Optimistic:** быстрые обратимые selection, favorite, reorder; failure откатывает и объясняет.
- **Confirmed:** payment, publish, permissions, irreversible operations; pending до настоящего ответа.
- **Undo:** предпочтительнее лишнего confirmation для частого обратимого действия.
- **Confirmation:** только для серьёзного, плохо обратимого и неочевидного последствия.

### 9.3 Focus и announcements

- Temporary layer возвращает focus trigger.
- После удаления focus идёт к логичному соседу/region heading.
- Validation ведёт к summary/первой ошибке.
- Background update использует подходящий live region и не перехватывает focus.
- Loading не объявляется на каждом animation frame.

---

## 10. Motion

Motion должен отвечать хотя бы на один вопрос: что вызвало изменение, откуда объект пришёл, куда ушёл, как source связан с destination или какое направление означает forward/back.

### 10.1 Практические web tokens

Это starting points; используй существующие project tokens, если они согласованы.

```css
:root {
  --md-sys-motion-duration-short-1: 100ms;
  --md-sys-motion-duration-short-2: 150ms;
  --md-sys-motion-duration-medium-1: 250ms;
  --md-sys-motion-duration-medium-2: 300ms;
  --md-sys-motion-duration-long-1: 400ms;
  --md-sys-motion-duration-long-2: 500ms;

  --md-sys-motion-easing-standard: cubic-bezier(.2, 0, 0, 1);
  --md-sys-motion-easing-decelerate: cubic-bezier(0, 0, 0, 1);
  --md-sys-motion-easing-accelerate: cubic-bezier(.3, 0, 1, 1);
  --md-sys-motion-easing-emphasized-decelerate: cubic-bezier(.05, .7, .1, 1);
  --md-sys-motion-easing-emphasized-accelerate: cubic-bezier(.3, 0, .8, .15);
}
```

| Событие | Ориентир | Смысл |
|---|---:|---|
| state layer / small control | 100–200ms | immediate input feedback |
| selection/menu/small expand | 150–300ms | local state/origin |
| pane/dialog/navigation | 250–400ms | hierarchy/direction |
| container transform | 300–500ms | shared identity |

Exit обычно быстрее enter. Не задерживай input до декоративного завершения.

### 10.2 Choreography

- Один dominant transition на главный сценарий.
- Container задаёт движение; children поддерживают, а не исполняют отдельное шоу.
- Menu/sheet/dialog возникают из логичного trigger/origin.
- Forward/back имеют согласованное противоположное направление.
- Изменение одной row не перезапускает entrance всего list.
- В первую очередь animate `transform`/`opacity`; не используй `transition: all`.
- Stagger — короткий и только для маленькой связанной группы.

### 10.3 Reduced motion

Сохраняй visibility, focus, selection, loading, success/error и resulting state; убирай большие translate/scale, parallax, decorative loops, smooth scrolling и необязательный morph.

```css
@media (prefers-reduced-motion: reduce) {
  html:focus-within { scroll-behavior: auto; }
  /* В ключевых компонентах сократи duration и отключи spatial transform,
     но не скрывай state change. */
}
```

Проверяй ключевые transitions вручную; wildcard сам по себе недостаточен.

---

## 11. Accessibility и границы платформ

### 11.1 Universal

- Native semantics прежде custom ARIA/semantics replacement.
- Весь главный сценарий доступен keyboard и pointer.
- Focus indicator заметен на каждой surface; DOM/traversal order следует смыслу.
- Accessible name, role, state и value соответствуют control.
- Color/motion не являются единственным носителем смысла.
- Hover, drag и gesture имеют альтернативу.
- Errors называют причину и исправление.
- Automated audit дополняет, но не заменяет manual keyboard/screen-reader/zoom testing.

### 11.2 Contrast

- Обычный текст: WCAG AA минимум `4.5:1`.
- Крупный текст: минимум `3:1` при условиях WCAG.
- Значимые non-text boundaries/states и focus: минимум `3:1` относительно соседнего цвета.
- Декоративные и неактивные элементы имеют оговорённые WCAG exceptions; не используй исключение, чтобы скрыть важную affordance.

### 11.3 Web

- Используй CSS px/rem/em и проверяй browser zoom/reflow; не переноси `dp`/`sp` буквально.
- WCAG 2.2 Target Size (Minimum) требует минимум `24 × 24 CSS px` либо соблюдения предусмотренных spacing/exceptions. Для touch-first интерфейса предпочитай более крупную область, близкую к Material-рекомендации.
- Не отключай zoom, text scaling, outline, forced colors или system preferences.
- Проверяй 200% zoom, длинный/локализованный text и narrow viewport без двухмерного scroll для обычного content.

### 11.4 Android / Compose

- Android рекомендует touch target не менее `48 × 48dp`, даже если visible icon меньше.
- Text задаётся в `sp`; body text не следует делать меньше `12sp`, но это не универсальный минимум для каждой Material label role и не web-unit.
- Compose semantics передаёт meaning/role/state accessibility services; custom components требуют осознанной semantics и traversal проверки.
- Compose adaptive scaffolds/API — implementation help конкретной платформы, не универсальная структура любого web UI.

---

## 12. Анти-pattern generic AI UI

По умолчанию исключи:

- purple/blue gradient glow, glassmorphism и blurred blobs без продуктовой причины;
- giant centered hero, eyebrow badge и две шаблонные CTA;
- одинаковую сетку rounded cards для любого домена;
- pills для status, category, metric и metadata одновременно;
- вымышленные KPI, отзывы, логотипы и «рост 24%»;
- color icon-square у каждого heading;
- gradient text, excessive shadows и одинаковый большой radius;
- декоративный dashboard без завершаемой работы;
- parallax, cursor glow, 3D tilt, perpetual floating/pulsing;
- cascade fade-in всего экрана;
- control, который ничего не делает.

Противоядие:

- реальный domain language и entities;
- hierarchy из риска, частоты, данных и среды использования;
- variative rhythm: dominant workspace + supporting region вместо равной grid;
- один узнаваемый продуктовый жест;
- edge states с сохранением context;
- повторение pattern только для равноправных objects;
- удаление всего, что не помогает понять, решить или сделать.

---

## 13. Render verification loop

Проверка — часть проектирования, а не финальная формальность:

1. реализуй главный end-to-end сценарий;
2. запусти приложение и открой реальный UI;
3. пройди сценарий pointer и keyboard;
4. проверь compact, medium и expanded, включая промежуточные width/height;
5. проверь light/dark, 200% zoom и long/unbroken content;
6. достигни loading, empty/no-results, error, disabled/pending и success, где применимо;
7. проверь menu/dialog/sheet, Escape, focus trap/return;
8. проверь обычный и reduced motion;
9. оцени hierarchy, density, surfaces, shape, typography и state feedback по отрендерованному экрану;
10. исправь и повтори до прохождения fail-gates и rubric;
11. запусти доступные lint/typecheck/tests/build и проверь console/network errors;
12. сообщи только о реально выполненных проверках.

---

## 14. Rubric готовности

Оцени каждую категорию `0–2`:

| Категория | 0 | 1 | 2 |
|---|---|---|---|
| Product clarity | сценарий/результат неясны | понятны после изучения | object, state и next action ясны сразу |
| Material semantics | роли перепутаны/декоративны | в основном корректны | color/surface/type/shape/state согласованы |
| Visual hierarchy | всё равнозначно | hierarchy есть, но нестабильна | primary/secondary/supporting читаются уверенно |
| Component correctness | компоненты не по смыслу | отдельные компромиссы | anatomy, semantics и behavior соответствуют задаче |
| Adaptive behavior | shrink/overflow | базовая перестройка | navigation/regions/workflow адаптируются осмысленно |
| Interaction states | controls декоративны/теряют context | happy path + часть states | полный применимый feedback/recovery/focus contract |
| Accessibility | главный сценарий недоступен | базовая semantics/keyboard | contrast, focus, targets, zoom и announcements проверены |
| Visual authenticity | generic AI template | Material cues непоследовательны | узнаваемая M3-система без клише |

**Проход:** не менее `13/16`, ни одной оценки `0`, все fail-gates закрыты. Высокий total не компенсирует провал accessibility или главного сценария.

Финальный ответ агента сообщает:

1. реализованный сценарий;
2. выбранную composition и причину;
3. ключевые Material roles/adaptive changes;
4. выполненные проверки и их результат;
5. реальные оставшиеся ограничения.

---

## 15. Официальные источники и статус implementation libraries

При расхождении приоритет у актуального первичного источника и правил целевой платформы.

### Material foundations

- [Material Design 3](https://m3.material.io/)
- [Color roles](https://m3.material.io/styles/color/roles)
- [Typography](https://m3.material.io/styles/typography/overview)
- [Elevation](https://m3.material.io/styles/elevation/overview)
- [Interaction states](https://m3.material.io/foundations/interaction/states/overview)
- [Material Symbols](https://m3.material.io/styles/icons/overview)
- [Motion](https://m3.material.io/styles/motion/overview)
- [Easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
- [Building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

### Layout и components

- [Canonical layouts — Material](https://m3.material.io/foundations/layout/canonical-examples/overview)
- [Canonical layouts — Android Developers](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts)
- [Window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)
- [List-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
- [Supporting pane](https://developer.android.com/develop/adaptive-apps/guides/build-a-supporting-pane-layout)
- [App bars](https://m3.material.io/components/app-bars/guidelines)
- [Search](https://m3.material.io/components/search/guidelines)
- [Bottom sheets](https://m3.material.io/components/bottom-sheets/guidelines)
- [Segmented buttons](https://m3.material.io/components/segmented-buttons/guidelines)
- [Switch](https://m3.material.io/components/switch/guidelines)
- [Buttons](https://m3.material.io/components/buttons/guidelines)
- [Text fields](https://m3.material.io/components/text-fields/guidelines)
- [Dialogs](https://m3.material.io/components/dialogs/guidelines)

### Accessibility

- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- [Understanding Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast)
- [Understanding Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum)
- [Android accessibility foundations](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility)
- [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing)

### Material Web

[`material-components/material-web`](https://github.com/material-components/material-web) находится в **maintenance mode**. Старый [`material-components-web`](https://github.com/material-components/material-components-web) архивирован. Не подключай их автоматически как новую зависимость. Material 3 для web можно и часто лучше реализовать через существующий framework/design system, semantic tokens, native elements и проверенные accessible primitives.

---

## 16. Что не требуется

- Не устанавливай новый UI-kit, icon pack или animation library только ради сходства.
- Не создавай отдельный файл на каждый token без архитектурной причины.
- Не заменяй native control custom implementation ради внешности.
- Не воспроизводи конкретное Google-приложение.
- Не добавляй каждый M3 component «для полноты».
- Не анимируй изменение, если статический state feedback яснее.
- Не заполняй широкое окно дополнительным pane без пользовательской пользы.

Для новой задачи начинай с [`PROMPT.md`](PROMPT.md); этот документ используй как reference, а не как второй конкурирующий prompt.
