# Макеты страниц DocForge (shadcn/ui + Tailwind)

Копируй нужные секции в `<main id="content">` файла `index.html`.
Заменяй `[REPLACE]` на реальный контент.

---

## Главная страница

```html
<section data-page="home" data-od-id="home-page" class="space-y-6">
  <h1 class="font-display text-2xl font-semibold text-ink-1">Главная</h1>

  <!-- Карточки статистики -->
  <div class="grid grid-cols-4 gap-4" data-od-id="home-stats">
    <div class="bg-surface border border-border rounded-lg p-4 shadow-sm">
      <div class="text-2xl font-semibold text-ink-1">12</div>
      <div class="text-xs text-ink-3 mt-1">Шаблонов</div>
    </div>
    <div class="bg-surface border border-border rounded-lg p-4 shadow-sm">
      <div class="text-2xl font-semibold text-ink-1">48</div>
      <div class="text-xs text-ink-3 mt-1">Документов</div>
    </div>
    <div class="bg-surface border border-border rounded-lg p-4 shadow-sm">
      <div class="text-2xl font-semibold text-ink-1">5</div>
      <div class="text-xs text-ink-3 mt-1">Соавторов</div>
    </div>
    <div class="bg-surface border border-border rounded-lg p-4 shadow-sm">
      <div class="text-2xl font-semibold text-ink-1">3</div>
      <div class="text-xs text-ink-3 mt-1">Папки</div>
    </div>
  </div>

  <!-- Продолжить работу -->
  <div>
    <h2 class="font-display text-xl font-semibold text-ink-1 mb-3">Продолжить работу</h2>
    <div class="bg-surface border border-border rounded-lg overflow-hidden" data-od-id="home-continue">
      <table class="w-full text-sm caption-bottom">
        <thead class="bg-bg">
          <tr class="border-b border-border">
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Название</th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Статус</th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Обновлен</th>
            <th class="p-3"></th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-border hover:bg-brand-soft">
            <td class="p-3 text-ink-2">Договор поставки №123</td>
            <td class="p-3"><span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold border-ink-4 text-ink-3">Черновик</span></td>
            <td class="p-3 text-ink-3">2 ч. назад</td>
            <td class="p-3"><button class="btn-ghost text-xs">Открыть</button></td>
          </tr>
          <tr class="border-b border-border hover:bg-brand-soft">
            <td class="p-3 text-ink-2">Приказ о приеме</td>
            <td class="p-3"><span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold bg-[oklch(0.95_0.02_160)] text-[oklch(0.50_0.16_160)]">Сформирован</span></td>
            <td class="p-3 text-ink-3">Вчера</td>
            <td class="p-3"><button class="btn-ghost text-xs">Открыть</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <!-- Быстрые действия -->
  <div>
    <h2 class="font-display text-xl font-semibold text-ink-1 mb-3">Быстрые действия</h2>
    <div class="grid grid-cols-3 gap-4" data-od-id="home-actions">
      <div class="bg-surface border border-border rounded-lg p-4 cursor-pointer hover:border-brand transition-colors">
        <div class="font-semibold text-ink-1 mb-1">Создать документ</div>
        <div class="text-xs text-ink-3">На основе шаблона</div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-4 cursor-pointer hover:border-brand transition-colors">
        <div class="font-semibold text-ink-1 mb-1">Создать шаблон</div>
        <div class="text-xs text-ink-3">Конструктор шаблонов</div>
      </div>
      <div class="bg-surface border border-border rounded-lg p-4 cursor-pointer hover:border-brand transition-colors">
        <div class="font-semibold text-ink-1 mb-1">Импорт</div>
        <div class="text-xs text-ink-3">Загрузить файлы</div>
      </div>
    </div>
  </div>
</section>
```

---

## Мои документы

```html
<section data-page="documents" data-od-id="documents-page" class="space-y-4">
  <div class="flex justify-between items-center">
    <h1 class="font-display text-2xl font-semibold text-ink-1">Мои документы</h1>
    <div class="flex gap-2">
      <button class="btn-secondary text-xs">Список</button>
      <button class="btn-secondary text-xs">Плитка</button>
      <button class="btn-primary text-xs">+ Создать</button>
    </div>
  </div>

  <div class="grid grid-cols-[200px_1fr] gap-4" data-od-id="docs-body">
    <!-- Дерево папок -->
    <div class="bg-surface border border-border rounded-lg p-3" data-od-id="docs-folder-tree">
      <div class="text-[0.7rem] font-semibold uppercase tracking-wider text-ink-4 mb-2">Папки</div>
      <div class="text-sm p-1.5 rounded-md bg-brand-soft text-brand mb-1 cursor-pointer">Все документы</div>
      <div class="text-sm p-1.5 rounded-md text-ink-2 hover:bg-brand-soft mb-1 cursor-pointer">Договоры</div>
      <div class="text-sm p-1.5 rounded-md text-ink-2 hover:bg-brand-soft mb-1 cursor-pointer">Приказы</div>
      <div class="text-sm p-1.5 rounded-md text-ink-2 hover:bg-brand-soft cursor-pointer">Уставы</div>
    </div>

    <!-- Список документов -->
    <div data-od-id="docs-list">
      <table class="w-full text-sm bg-surface border border-border rounded-lg overflow-hidden">
        <thead class="bg-bg">
          <tr class="border-b border-border">
            <th class="text-left p-3"><input type="checkbox" class="rounded border-border"></th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Название</th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Статус</th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Папка</th>
            <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Обновлен</th>
            <th class="p-3"></th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-border hover:bg-brand-soft">
            <td class="p-3"><input type="checkbox" class="rounded border-border"></td>
            <td class="p-3 text-ink-2">Договор поставки №123</td>
            <td class="p-3"><span class="badge badge--draft">Черновик</span></td>
            <td class="p-3 text-ink-3">Договоры</td>
            <td class="p-3 text-ink-3">2 ч. назад</td>
            <td class="p-3">
              <button class="btn-ghost text-xs">✏️</button>
              <button class="btn-ghost text-xs">🗑</button>
            </td>
          </tr>
          <tr class="hover:bg-brand-soft">
            <td class="p-3"><input type="checkbox" class="rounded border-border"></td>
            <td class="p-3 text-ink-2">Приказ о приеме</td>
            <td class="p-3"><span class="badge badge--generated">Сформирован</span></td>
            <td class="p-3 text-ink-3">Приказы</td>
            <td class="p-3 text-ink-3">Вчера</td>
            <td class="p-3">
              <button class="btn-ghost text-xs">✏️</button>
              <button class="btn-ghost text-xs">🗑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</section>
```

---

## Шаблоны

```html
<section data-page="templates" data-od-id="templates-page" class="space-y-4">
  <div class="flex justify-between items-center">
    <h1 class="font-display text-2xl font-semibold text-ink-1">Шаблоны</h1>
    <button class="btn-primary text-xs">+ Создать шаблон</button>
  </div>

  <!-- Пилл-табы -->
  <div class="flex gap-1" data-od-id="pill-tabs">
    <span class="rounded-full px-4 py-1.5 text-xs font-medium bg-brand text-white cursor-pointer">Все</span>
    <span class="rounded-full px-4 py-1.5 text-xs font-medium bg-surface text-ink-3 border border-border cursor-pointer hover:bg-brand-soft">Опубликованные</span>
    <span class="rounded-full px-4 py-1.5 text-xs font-medium bg-surface text-ink-3 border border-border cursor-pointer hover:bg-brand-soft">Черновики</span>
  </div>

  <!-- Поиск -->
  <input type="text" placeholder="Поиск шаблонов..." class="w-full p-2 border border-border rounded-md text-sm bg-surface text-ink-2 placeholder:text-ink-4" data-od-id="search-bar">

  <!-- Таблица шаблонов -->
  <table class="w-full text-sm bg-surface border border-border rounded-lg overflow-hidden" data-od-id="template-table">
    <thead class="bg-bg">
      <tr class="border-b border-border">
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Название</th>
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Статус</th>
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Версия</th>
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Использований</th>
        <th class="p-3"></th>
      </tr>
    </thead>
    <tbody>
      <tr class="border-b border-border hover:bg-brand-soft">
        <td class="p-3 text-ink-2">Договор поставки</td>
        <td class="p-3"><span class="badge badge--published">Опубликован</span></td>
        <td class="p-3 text-ink-3">1.2</td>
        <td class="p-3 text-ink-3">48</td>
        <td class="p-3"><button class="btn-ghost text-xs">Редактировать</button> <button class="btn-ghost text-xs">🗑</button></td>
      </tr>
      <tr class="hover:bg-brand-soft">
        <td class="p-3 text-ink-2">Приказ о приеме</td>
        <td class="p-3"><span class="badge badge--published">Опубликован</span></td>
        <td class="p-3 text-ink-3">2.0</td>
        <td class="p-3 text-ink-3">120</td>
        <td class="p-3"><button class="btn-ghost text-xs">Редактировать</button> <button class="btn-ghost text-xs">🗑</button></td>
      </tr>
    </tbody>
  </table>
</section>
```

---

## Конструктор шаблона (Редактор)

```html
<section data-page="template-designer" data-od-id="template-designer-page">
  <div class="grid grid-cols-[1fr_280px] gap-4" style="height: calc(100vh - 56px - 48px);">
    <!-- Колонка редактора -->
    <div class="flex flex-col" data-od-id="editor-column">
      <!-- Тулбар -->
      <div class="flex flex-wrap gap-0.5 p-2 bg-bg border border-border rounded-t-lg" data-od-id="editor-toolbar">
        <button class="w-8 h-8 flex items-center justify-center rounded text-ink-3 hover:bg-brand-soft hover:text-brand" title="Жирный"><b>B</b></button>
        <button class="w-8 h-8 flex items-center justify-center rounded text-ink-3 hover:bg-brand-soft hover:text-brand" title="Курсив"><i>I</i></button>
        <button class="w-8 h-8 flex items-center justify-center rounded text-sm font-semibold text-ink-3 hover:bg-brand-soft hover:text-brand" title="Заголовок 1">H1</button>
        <button class="w-8 h-8 flex items-center justify-center rounded text-sm font-semibold text-ink-3 hover:bg-brand-soft hover:text-brand" title="Заголовок 2">H2</button>
        <div class="w-px h-5 bg-border mx-1 self-center"></div>
        <button class="w-8 h-8 flex items-center justify-center rounded text-ink-3 hover:bg-brand-soft hover:text-brand" title="Список">☰</button>
        <button class="w-8 h-8 flex items-center justify-center rounded text-ink-3 hover:bg-brand-soft hover:text-brand" title="Таблица">⊞</button>
        <div class="w-px h-5 bg-border mx-1 self-center"></div>
        <button class="w-8 h-8 flex items-center justify-center rounded text-xs font-mono text-brand hover:bg-brand-soft" title="Параметр">¶</button>
        <button class="w-8 h-8 flex items-center justify-center rounded text-xs text-ink-3 hover:bg-brand-soft" title="Условие">?if</button>
      </div>

      <!-- TipTap Редактор -->
      <div class="flex-1 p-6 overflow-y-auto bg-surface border-x border-border font-doc text-base leading-relaxed text-ink-2 outline-none" data-od-id="tiptap-editor" contenteditable="true">
        <h1 class="font-display text-2xl font-semibold text-ink-1 mb-4">Договор поставки №<span class="font-mono text-brand text-sm">{{номер_договора}}</span></h1>
        <p class="mb-4">г. <span class="font-mono text-brand text-sm">{{город_заключения}}</span>                                                                                                            «<span class="font-mono text-brand text-sm">{{дата_заключения}}</span>»</p>
        <p class="mb-2"><strong>{{наименование_поставщика}}</strong>, именуемое в дальнейшем «Поставщик»...</p>
        <h2 class="font-display text-xl font-semibold text-ink-1 mt-6 mb-2">1. Предмет договора</h2>
        <p>Поставщик обязуется передать в собственность Покупателя товар...</p>
      </div>
    </div>

    <!-- Боковая панель -->
    <div class="bg-surface border border-border rounded-lg p-4 overflow-y-auto" data-od-id="param-panel">
      <div class="font-semibold text-ink-1 mb-4">Параметры шаблона</div>

      <div class="border border-border rounded-md p-2 mb-2">
        <div class="font-mono text-xs font-semibold text-brand">{{номер_договора}}</div>
        <div class="text-[0.65rem] uppercase text-ink-4 mt-1">Текст</div>
      </div>
      <div class="border border-border rounded-md p-2 mb-2">
        <div class="font-mono text-xs font-semibold text-brand">{{город_заключения}}</div>
        <div class="text-[0.65rem] uppercase text-ink-4 mt-1">Справочник: Города</div>
      </div>
      <div class="border border-border rounded-md p-2 mb-2">
        <div class="font-mono text-xs font-semibold text-brand">{{дата_заключения}}</div>
        <div class="text-[0.65rem] uppercase text-ink-4 mt-1">Дата</div>
      </div>

      <div class="mt-4">
        <div class="font-semibold text-ink-1 mb-2">Анкета</div>
        <div class="text-xs text-ink-3 mb-2">Вопросы для заполнения</div>
        <div class="text-xs p-1.5 bg-bg rounded mb-1">Введите номер договора</div>
        <div class="text-xs p-1.5 bg-bg rounded mb-1">Выберите город заключения</div>
        <div class="text-xs p-1.5 bg-bg rounded mb-1">Укажите дату заключения</div>
      </div>
    </div>
  </div>
</section>
```

---

## Заполнение анкеты (Превью)

```html
<section data-page="questionnaire" data-od-id="questionnaire-page">
  <div class="grid grid-cols-2 gap-4" style="height: calc(100vh - 56px - 48px);" data-od-id="split-layout">
    <!-- Анкета (слева) -->
    <div class="bg-surface border border-border rounded-lg p-6 overflow-y-auto">
      <h2 class="font-display text-xl font-semibold text-ink-1 mb-1">Заполнение документа</h2>
      <div class="text-sm text-ink-3 mb-6">Договор поставки №123</div>

      <div class="mb-4">
        <label class="block text-xs font-medium text-ink-2 mb-1">Номер договора</label>
        <input type="text" value="123" class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2">
      </div>
      <div class="mb-4">
        <label class="block text-xs font-medium text-ink-2 mb-1">Город заключения</label>
        <input type="text" value="Москва" class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2">
      </div>
      <div class="mb-4">
        <label class="block text-xs font-medium text-ink-2 mb-1">Дата заключения</label>
        <input type="date" value="2026-05-06" class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2">
      </div>
      <button class="btn-primary w-full justify-center mt-2">Сформировать документ</button>
    </div>

    <!-- Превью (справа) -->
    <div class="bg-surface border border-border rounded-lg p-6 overflow-y-auto font-doc text-sm leading-relaxed text-ink-2">
      <div class="text-[0.7rem] text-ink-4 text-center mb-3 uppercase tracking-wider">ПРЕДПРОСМОТР</div>
      <h1 class="font-display text-lg font-semibold text-ink-1 mb-3">Договор поставки №123</h1>
      <p class="mb-3">г. Москва «6 мая 2026 г.»</p>
      <p class="mb-3"><strong>ООО «Ромашка»</strong>, именуемое в дальнейшем «Поставщик»...</p>
      <h2 class="font-display text-base font-semibold text-ink-1 mt-4 mb-2">1. Предмет договора</h2>
      <p>Поставщик обязуется передать в собственность Покупателя товар...</p>
    </div>
  </div>
</section>
```
