# TASK-015: Backend-исправления YjsDocumentStoreImpl по ревью TASK-006-2

| Поле | Значение |
|---|---|
| **Дата** | 2026-04-18 |
| **Тип** | `refactor` |
| **Статус** | `TODO` |
| **Агенты** | `/backend` |
| **Коммит** | — |
| **Зависит от** | — |
| **GitHub Issue** | [#18](https://github.com/NKamaev/DocForge/issues/18) |

---

## Постановка задачи

Код ревью TASK-006-2 выявил несколько WARNING в `YjsDocumentStoreImpl.java`:
1. Гонка в `schedulePersist` (не атомарная замена таймера)
2. `evict` не флашит несохранённые данные — последние 5 секунд правок теряются при отключении последнего клиента
3. `catch (Exception e)` — нарушение конвенции проекта
4. `Collections.synchronizedList` избыточен при явных `synchronized` блоках

---

## Интерпретация

Исправить все четыре замечания не меняя внешний API хранилища. Основной риск — потеря Yjs-правок при evict.

---

## Файлы для изучения перед исполнением

```
backend/src/main/java/com/docforge/collaboration/YjsDocumentStoreImpl.java
backend/src/main/java/com/docforge/collaboration/YjsDocumentStore.java
backend/src/main/java/com/docforge/collaboration/CollaborationWebSocketHandler.java
```

---

## Файлы для изменения

```
backend/src/main/java/com/docforge/collaboration/YjsDocumentStoreImpl.java
  — schedulePersist: атомарная замена через debounceTimers.compute(...)
  — evict: перед remove вызвать doPersist(docId) и отменить таймер
  — doPersist: catch конкретные типы вместо Exception
  — loadFromDb: вернуть обычный new ArrayList<>() без synchronizedList-обёртки
```

---

## Поэтапное исполнение

1. **`schedulePersist`** — заменить get/cancel/put на:
   ```java
   debounceTimers.compute(docId, (k, existing) -> {
       if (existing != null) existing.cancel(false);
       return scheduler.schedule(() -> doPersist(k), DEBOUNCE_SECONDS, TimeUnit.SECONDS);
   });
   ```

2. **`evict`** — добавить flush перед удалением из кеша:
   ```java
   public void evict(String docId) {
       persistNow(docId);          // флаш несохранённых правок
       updateCache.remove(docId);
       log.debug("YjsDocumentStore: evicted docId={}", docId);
   }
   ```
   _(Примечание: `persistNow` уже отменяет debounce-таймер.)_

3. **`doPersist`** — уточнить catch:
   ```java
   } catch (IllegalArgumentException | RuntimeException e) {
   ```

4. **`loadFromDb`** — убрать `Collections.synchronizedList`:
   ```java
   return new ArrayList<>(updates);   // вместо Collections.synchronizedList(...)
   return new ArrayList<>();          // для пустого случая
   ```

---

## Автоматические тесты

| Сценарий | Тип теста | Файл |
|---|---|---|
| `schedulePersist` вызванный дважды подряд не создаёт два persist | unit | `backend/src/test/java/com/docforge/collaboration/YjsDocumentStoreImplTest.java` |
| `evict` вызывает `doPersist` перед удалением из кеша | unit | `backend/src/test/java/com/docforge/collaboration/YjsDocumentStoreImplTest.java` |

### Команды запуска

```bash
cd backend && ./gradlew test --tests "com.docforge.collaboration.YjsDocumentStoreImplTest"
```

---

## Чеклист ручного тестирования

- [ ] Открыть `/fill/{id}` в двух вкладках → оба соединяются
- [ ] Закрыть обе вкладки → через 5 секунд данные сохраняются в MongoDB (проверить через API `/api/documents/{id}`)
- [ ] Регрессия: перезагрузка страницы восстанавливает контент

---

## История

| Дата | Событие |
|---|---|
| 2026-04-18 | Задача создана по результатам код ревью TASK-006-2 |

---

## Завершение задачи

> **Обязательно выполнить перед отчётом о выполнении!**

- [ ] Автотесты запущены и проходят (`./gradlew test`)
- [ ] Ручной чеклист выполнен полностью
- [ ] Код прошёл ревью (`/review`)
- [ ] Git-коммит создан с сообщением: `fix(collab): atomic debounce, evict flush, narrow catch in YjsDocumentStoreImpl`
- [ ] Статус обновлён в этом файле: `DONE`
- [ ] Реестр обновлён: [docs/tasks/README.md](README.md)
