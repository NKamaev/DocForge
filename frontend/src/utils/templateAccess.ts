/**
 * Проверки прав на шаблон на стороне frontend.
 *
 * Зеркалит серверную логику `TemplateServiceImpl#isOwner`:
 * владелец определяется строгим сравнением `ownerId` шаблона с id текущего
 * пользователя (шаблон без владельца — `ownerId == null` — никому не принадлежит).
 * Администратор имеет полный доступ наравне с владельцем (это также принято
 * на сервере — `admin || isOwner(...)`).
 */

/** Минимальный набор полей шаблона, достаточный для проверки прав. */
export interface TemplateOwnerLike {
  ownerId?: string | null
}

/** Минимальный набор полей пользователя, достаточный для проверки прав. */
export interface TemplateUserLike {
  id: string
  admin?: boolean
}

/** Является ли пользователь владельцем шаблона. */
export function isTemplateOwner(
  template: TemplateOwnerLike | null | undefined,
  userId: string | null | undefined,
): boolean {
  if (!template || !userId) return false
  const ownerId = template.ownerId
  if (!ownerId) return false
  return ownerId === userId
}

/**
 * Может ли пользователь управлять шаблоном (публикация, откат версий).
 *
 * Владелец — всегда; администратор — тоже. Для всех остальных доступ к
 * черновикам и черновым версиям закрыт сервером (403).
 */
export function canManageTemplate(
  template: TemplateOwnerLike | null | undefined,
  user: TemplateUserLike | null | undefined,
): boolean {
  if (!user) return false
  if (user.admin) return true
  return isTemplateOwner(template, user.id)
}

/**
 * Может ли пользователь редактировать шаблон.
 *
 * Редактирование доступно владельцу (или администратору) и только пока
 * шаблон не опубликован: опубликованный шаблон сервер на запись закрывает
 * (`PUT` → 409), поэтому фронтенд блокирует правку заранее.
 *
 * `user == null` → личность пользователя ещё не установлена → прав нет
 * (fail-closed): до приведения ответа `GET /users/me` нельзя ни показать
 * действия владельца, ни разрешить правку.
 */
export function canEditTemplate(
  template: TemplateOwnerLike | null | undefined,
  user: TemplateUserLike | null | undefined,
  published: boolean,
): boolean {
  if (published) return false
  return canManageTemplate(template, user)
}

/**
 * Подсказка для пользователя, которому шаблон не принадлежит.
 *
 * Возвращается только когда права уже известны (пользователь определён) и
 * прав на управление нет — чтобы не пугать владельца сообщением, пока
 * личность пользователя ещё выясняется.
 */
export function getReadOnlyHint(
  template: TemplateOwnerLike | null | undefined,
  user: TemplateUserLike | null | undefined,
): string | null {
  if (!template || !user) return null
  if (canManageTemplate(template, user)) return null
  return 'Вы не владелец шаблона — редактирование недоступно'
}

/**
 * Права на изменяющие операции над шаблоном.
 *
 * Зеркалит серверное правило BUG-068: любую изменяющую операцию
 * (`publish`, `unpublish`, `delete`, `POST /versions`, `rollback`) выполнять
 * может только владелец шаблона или администратор; остальным сервер
 * отвечает 403 «Нет прав на изменение шаблона».
 *
 * Флаги вынесены поимённо, чтобы UI мог решать по каждому действию
 * отдельно: правила сервера могут разойтись, а список действий уже сейчас
 * разный (в списке — публикация и удаление, в конструкторе — ещё версии
 * и откат).
 */
export interface TemplateMutationRights {
  /** Публикация / снятие с публикации (`POST .../publish`, `.../unpublish`). */
  canPublish: boolean
  /** Удаление шаблона (`DELETE /templates/{id}`). */
  canDelete: boolean
  /** Сохранение новой версии (`POST /templates/{id}/versions`). */
  canSaveVersion: boolean
  /** Откат к версии (`POST /templates/{id}/rollback/{n}`). */
  canRollback: boolean
}

/**
 * Права на изменяющие операции: владелец шаблона или администратор.
 *
 * Fail-closed: пока личность пользователя неизвестна (`user == null`),
 * никаких изменяющих действий не предлагаем — сервер в этом случае
 * всё равно ответит 403.
 */
export function getTemplateMutationRights(
  template: TemplateOwnerLike | null | undefined,
  user: TemplateUserLike | null | undefined,
): TemplateMutationRights {
  const allowed = canManageTemplate(template, user)
  return {
    canPublish: allowed,
    canDelete: allowed,
    canSaveVersion: allowed,
    canRollback: allowed,
  }
}
