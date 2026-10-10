#!/usr/bin/env bash
#
# memory-lint.sh — валидатор долговременной памяти проекта (.memory/).
#
# Проверяет:
#   1. каждый файл под .memory/tags/ зарегистрирован в tag-index.md;
#   2. каждая ссылка из tag-index.md существует (поддержка масок *);
#   3. пустые (0 байт) файлы тегов;
#   4. внутренние markdown-ссылки в .memory/ не ведут в никуда.
#
# Использование:
#   memory-lint.sh [путь-к-проекту]        # по умолчанию — текущий каталог
#
# Код выхода: 0 — чисто, 1 — найдены проблемы, 2 — нет .memory/.

set -u

ROOT="${1:-$PWD}"
MEM="$ROOT/.memory"
INDEX="$MEM/tag-index.md"
TAGS="$MEM/tags"

if [[ ! -d "$MEM" ]]; then
  echo "memory-lint: нет каталога $MEM" >&2
  exit 2
fi

errors=0
warn() { echo "  ✗ $*"; errors=$((errors + 1)); }
ok()   { echo "  ✓ $*"; }

echo "memory-lint: $MEM"

# --- 1. Собираем файлы тегов ------------------------------------------------
mapfile -t FILES < <(cd "$TAGS" 2>/dev/null && find . -name '*.md' | sed 's|^\./||' | sort)

# --- 2. Извлекаем ссылки из tag-index.md -----------------------------------
declare -A REFSET=()
if [[ -f "$INDEX" ]]; then
  while IFS= read -r token; do
    # пропускаем пустые и плейсхолдеры вида <name> / {category}
    [[ -z "$token" ]] && continue
    case "$token" in *'{'*|*'}'*|*'<'*|*'>'*) continue;; esac
    token="${token#./}"
    if [[ "$token" == *'*'* ]]; then
      while IFS= read -r m; do
        [[ -n "$m" ]] && REFSET["${m#./}"]=1
      done < <(cd "$TAGS" 2>/dev/null && compgen -G "$token" || true)
    else
      REFSET["$token"]=1
    fi
  done < <(grep -oE '`[^`"]+\.md`' "$INDEX" | tr -d '`' | sort -u)
else
  echo "  ! нет $INDEX — проверка index пропущена"
fi

# --- 3. Сверяем index <-> файлы --------------------------------------------
if [[ -f "$INDEX" ]]; then
  echo "[index <-> files]"

  for ref in "${!REFSET[@]}"; do
    [[ -f "$TAGS/$ref" ]] || warn "в index есть '$ref', но файла нет"
  done

  declare -A FILESET=()
  for f in "${FILES[@]:-}"; do
    [[ -n "$f" ]] || continue
    FILESET["$f"]=1
    [[ -n "${REFSET[$f]:-}" ]] || warn "файл '$f' не зарегистрирован в tag-index.md"
  done

  [[ $errors -eq 0 ]] && ok "index и файлы согласованы (${#FILESET[@]} файлов)"
fi

# --- 4. Пустые файлы --------------------------------------------------------
echo "[пустые файлы]"
empty=0
for f in "${FILES[@]:-}"; do
  [[ -n "$f" ]] || continue
  if [[ ! -s "$TAGS/$f" ]]; then
    warn "пустой файл: tags/$f"
    empty=$((empty + 1))
  fi
done
[[ $empty -eq 0 ]] && ok "пустых тегов нет"

# --- 5. Битые внутренние markdown-ссылки -----------------------------------
echo "[внутренние ссылки]"
broken=0
while IFS= read -r md; do
  dir="$(dirname "$md")"
  while IFS= read -r target; do
    [[ -z "$target" ]] && continue
    case "$target" in
      http*|\#*|mailto:*) continue;;   # внешние/якоря не проверяем
    esac
    target="${target%%#*}"            # отбрасываем якорь
    [[ -z "$target" ]] && continue
    case "$target" in *[[:space:]]*) continue;; esac   # многословные фрагменты — не файлы
    case "$target" in *.*|*/*) ;; *) continue;; esac   # без пути/расширения — не файл (напр. код `[h.fn](qn)`)
    if [[ ! -e "$dir/$target" ]]; then
      warn "битая ссылка в ${md#$MEM/}: -> $target"
      broken=$((broken + 1))
    fi
  done < <(sed -E 's/`[^`]*`//g' "$md" 2>/dev/null | grep -oE '\[[^]]*\]\([^)]+\)' | sed -E 's/.*\]\(([^)]+)\)/\1/')
done < <(find "$MEM" -name '*.md' | sort)
[[ $broken -eq 0 ]] && ok "битых ссылок нет"

# --- Итог -------------------------------------------------------------------
echo
if [[ $errors -eq 0 ]]; then
  echo "memory-lint: OK (проблем не найдено)"
  exit 0
else
  echo "memory-lint: $errors проблем(ы)"
  exit 1
fi
