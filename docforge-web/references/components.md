# Шпаргалка по shadcn/ui компонентам для DocForge

Используй классы Tailwind, чтобы максимально приблизить верстку к стилю shadcn/ui.
Если в среде выполнения доступен глобальный объект `shadcn`, используй его.

## Кнопки (Button)

```html
<!-- Первичная -->
<button class="bg-brand text-white px-4 py-2 rounded-md text-sm font-medium hover:opacity-90 transition-opacity">
  Создать документ
</button>

<!-- Вторичная -->
<button class="bg-surface text-ink-2 border border-border px-4 py-2 rounded-md text-sm font-medium hover:bg-brand-soft transition-colors">
  Отмена
</button>

<!-- Ghost -->
<button class="bg-transparent text-ink-3 px-3 py-1.5 rounded-md text-sm hover:bg-brand-soft hover:text-brand transition-colors">
  Подробнее
</button>
```

## Таблицы (Table)

```html
<div class="bg-surface border border-border rounded-lg overflow-hidden">
  <table class="w-full text-sm">
    <thead class="bg-bg">
      <tr class="border-b border-border">
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Название</th>
        <th class="text-left p-3 text-xs font-semibold uppercase tracking-wider text-ink-4">Статус</th>
        <th class="p-3"></th>
      </tr>
    </thead>
    <tbody>
      <tr class="border-b border-border hover:bg-brand-soft transition-colors">
        <td class="p-3 text-ink-2">Договор поставки</td>
        <td class="p-3">
          <!-- Бейдж -->
          <span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold border-ink-4 text-ink-3">
            Черновик
          </span>
        </td>
        <td class="p-3 text-right">
          <button class="text-sm text-brand hover:underline">Открыть</button>
        </td>
      </tr>
    </tbody>
  </table>
</div>
```

## Бейджи (Badge)

Используй inline-flex + специфичные цвета под статусы DocForge:

```html
<!-- Черновик (нейтральный) -->
<span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold border-ink-4 text-ink-3">
  Черновик
</span>

<!-- Сформирован / Опубликован (зеленый) -->
<span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold bg-[oklch(0.95_0.02_160)] text-[oklch(0.50_0.16_160)] border-[oklch(0.80_0.05_160)]">
  Сформирован
</span>

<!-- Экспортирован (бренд) -->
<span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold bg-brand-soft text-brand border-brand">
  Экспортирован
</span>
```

## Инпуты (Input)

```html
<div class="space-y-1">
  <label class="text-xs font-medium text-ink-2">Название документа</label>
  <input
    type="text"
    placeholder="Введите название..."
    class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2 placeholder:text-ink-4 focus:outline-none focus:ring-2 focus:ring-brand/50"
  />
</div>
```

## Диалоги (Dialog / Modal) — разметка

```html
<!-- Оверлей затемнения -->
<div class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
  <!-- Контент диалога -->
  <div class="bg-surface border border-border rounded-lg shadow-lg w-full max-w-md p-6 space-y-4">
    <h3 class="font-display text-lg font-semibold text-ink-1">Создать документ</h3>
    <div class="space-y-3">
      <div>
        <label class="text-xs font-medium text-ink-2">Название</label>
        <input type="text" class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2" />
      </div>
      <div>
        <label class="text-xs font-medium text-ink-2">Шаблон</label>
        <select class="w-full p-2 border border-border rounded-md text-sm bg-bg text-ink-2">
          <option>Договор поставки</option>
          <option>Приказ о приеме</option>
        </select>
      </div>
    </div>
    <div class="flex justify-end gap-2 pt-2">
      <button class="btn-secondary text-xs">Отмена</button>
      <button class="btn-primary text-xs">Создать</button>
    </div>
  </div>
</div>
```

## Тосты (Toast) — визуальная имитация

```html
<div class="fixed bottom-4 right-4 bg-surface border border-border rounded-lg shadow-pop p-4 flex items-start gap-3 max-w-sm z-50">
  <div class="flex-1">
    <div class="font-semibold text-sm text-ink-1">Шаблон сохранен</div>
    <div class="text-xs text-ink-3 mt-1">Договор поставки успешно обновлен</div>
  </div>
  <button class="text-ink-4 hover:text-ink-2">✕</button>
</div>
```

## Карточки (Card)

```html
<div class="bg-surface border border-border rounded-lg p-4 shadow-sm hover:border-brand transition-colors cursor-pointer">
  <div class="font-semibold text-ink-1 mb-1">Создать документ</div>
  <div class="text-xs text-ink-3">На основе существующего шаблона</div>
</div>
```

## Табы (Tabs) — пилл-стили

```html
<div class="flex gap-1">
  <span class="rounded-full px-4 py-1.5 text-xs font-medium bg-brand text-white cursor-pointer">Все</span>
  <span class="rounded-full px-4 py-1.5 text-xs font-medium bg-surface text-ink-3 border border-border cursor-pointer hover:bg-brand-soft">Опубликованные</span>
</div>
```

## Чекбоксы (Checkbox)

```html
<div class="flex items-center gap-2">
  <input type="checkbox" class="w-4 h-4 rounded border-border text-brand focus:ring-brand/50" />
  <label class="text-sm text-ink-2">Выбрать все документы</label>
</div>
```

## Статусы (Table Row Colors)

| Статус | Классы бейджа | Цвет текста |
|--------|--------------|-------------|
| Черновик | `border-ink-4 text-ink-3` | Нейтральный |
| Сформирован | `bg-[oklch(0.95_0.02_160)] text-[oklch(0.50_0.16_160)]` | Зеленый |
| Экспортирован | `bg-brand-soft text-brand` | Бренд индиго |
| Опубликован | `bg-[oklch(0.95_0.02_160)] text-[oklch(0.50_0.16_160)]` | Зеленый |
