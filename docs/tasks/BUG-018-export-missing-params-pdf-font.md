# BUG-018: Экспорт без параметров в DOCX и символы # в PDF

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-19 |
| **Тип** | `bug` |
| **Статус** | `DONE` |
| **Агенты** | `/backend` |
| **Коммит** | `9a4b520` |
| **Зависит от** | — |
| **GitHub Issue** | [#64](https://github.com/NKamaev/DocForge/issues/64) |

---

## Постановка задачи (от пользователя)

```
Документ http://localhost:5173/documents/6e556505-3861-4966-b11a-35c5e2778511/fill
выгружается без параметров в docx, в pdf вместо букв отображаются символы #
(скорее всего из-за того что в системе нет шрифта Times New Roman)
```

---

## Интерпретация

Два независимых дефекта экспорта на странице `/fill`:

1. **DOCX**: при экспорте в документ не подставляются значения параметров (поля из опросника остаются пустыми или отсутствуют).
2. **PDF**: вместо текста рендерятся символы `#` — классический признак отсутствующего шрифта в iText/OpenPDF. Шрифт `Times New Roman` не установлен в системе; нужно встроить шрифт в JAR или переключиться на шрифт с открытой лицензией (Liberation Serif / FreeSerif).

---

## Причина ошибки

**DOCX — параметры не подставляются:**

**Корень проблемы:**  
`DocxExportServiceImpl` / `DocxConverter` не получает или не применяет ответы пользователя (answers) при генерации DOCX на маршруте `/fill`. Вероятно, параметры не передаются в запросе экспорта или не обрабатываются резолвером.

**Цепочка сбоя:**
1. Пользователь заполняет опросник на `/fill` и нажимает «Скачать DOCX».
2. Frontend вызывает endpoint экспорта (возможно, без тела с answers).
3. `DocxExportServiceImpl` генерирует документ с незаполненными плейсхолдерами параметров.

---

**PDF — символы `#`:**

**Корень проблемы:**  
`PdfExportServiceImpl` использует шрифт `Times New Roman`, которого нет в системе. iText/OpenPDF заменяет нерендерируемые глифы символом `#`.

**Цепочка сбоя:**
1. `PdfExportServiceImpl` загружает шрифт по системному пути или имени.
2. Шрифт не найден → fallback с `#` для каждого символа.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/export/ExportController.java
backend/src/main/java/com/docforge/export/DocxExportServiceImpl.java
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java
backend/src/main/java/com/docforge/document/export/DocxConverter.java
frontend/src/api/         — метод экспорта (что передаётся в теле запроса)
frontend/src/pages/       — FillPage / DocumentWorkspace (откуда вызывается экспорт)
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/export/PdfExportServiceImpl.java  — заменить шрифт на встроенный
backend/src/main/java/com/docforge/export/DocxExportServiceImpl.java — проверить передачу answers
backend/src/main/java/com/docforge/export/ExportController.java       — проверить DTO запроса
backend/src/resources/fonts/                                           — добавить встроенный шрифт (если нужно)
```

---

## Поэтапное исполнение

1. **Диагностика DOCX** — прочитать `ExportController`, понять какой DTO принимает запрос экспорта и передаются ли туда `answers`. Проверить `DocxExportServiceImpl` — как параметры резолвятся в текст.
2. **Исправление DOCX** — если `answers` не передаются с фронта, добавить их в запрос; если не применяются в бэкенде — починить резолвер.
3. **Диагностика PDF** — найти место загрузки шрифта в `PdfExportServiceImpl`. Определить, встроен ли шрифт в classpath.
4. **Исправление PDF** — добавить открытый шрифт (напр. `LiberationSerif-Regular.ttf` или `FreeSerif.ttf`) в `backend/src/main/resources/fonts/`, загружать через `getClass().getResourceAsStream(...)`. Либо использовать `BaseFont.HELVETICA` как fallback.
5. **Тесты** — добавить/обновить тест экспорта с непустыми answers (DOCX) и тест PDF на корректный рендер текста.

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| DOCX содержит подставленные значения параметров | unit | `DocxExportServiceImplTest.java` |
| PDF рендерится без символов # (байты не пусты) | unit | `PdfExportServiceImplTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.export.*"
```

---

## Чеклист ручного тестирования

- [ ] Открыть `/fill`, заполнить все поля опросника
- [ ] Скачать DOCX → убедиться, что значения параметров подставлены в текст
- [ ] Скачать PDF → убедиться, что текст читаем, символов `#` нет
- [ ] Проверить документ без параметров — экспорт не ломается
- [ ] Регрессия: экспорт с `/edit` (если есть) не сломан

---

## История

| Дата | Событие |
|---|---|
| 2026-04-19 | Задача создана |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(export): подставлять answers в DOCX, встроить шрифт для PDF`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
- [ ] GitHub Issue закрыт (state: closed) + комментарий с хешем коммита
