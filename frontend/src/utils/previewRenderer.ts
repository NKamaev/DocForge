import type { JSONContent } from '@tiptap/core'
import type { Parameter } from '../types/template'
import type { Phrasing, ParameterFormat, DateFormat } from '../types/templateModel'
import { formatFio } from './fioFormatter'
import type { NumberingStyle } from '../components/template-designer/extensions/NumberedClauseNode'

type Answers = Record<string, unknown>

// ── Static label builder (no editor state, mirrors AutoNumberingPlugin) ────────

const RU_ALPHA = 'абвгдежзийклмнопрстуфхцчшщэюя'
const EN_ALPHA = 'abcdefghijklmnopqrstuvwxyz'

function formatCounter(n: number, style: NumberingStyle): string {
  if (style === 'alpha-ru') return RU_ALPHA[n - 1] ?? String(n)
  if (style === 'alpha-en') return EN_ALPHA[n - 1] ?? String(n)
  return String(n)
}

function buildStaticLabels(doc: JSONContent): Map<string, string> {
  const byId = new Map<string, string>()
  const counters: number[] = [0, 0, 0, 0, 0]
  const levelStyles: NumberingStyle[] = ['decimal', 'decimal', 'decimal', 'decimal', 'decimal']

  function walk(node: JSONContent): void {
    if (node.type === 'numberedClause') {
      const level = Math.min(Math.max(Number(node.attrs?.level ?? 1), 1), 5)
      const style: NumberingStyle = (node.attrs?.numberingStyle as NumberingStyle) || 'decimal'

      if (node.attrs?.listStart) counters.fill(0)

      for (let i = level; i < 5; i++) counters[i] = 0
      counters[level - 1]++
      levelStyles[level - 1] = style

      const segments = counters.slice(0, level).map((c, i) => formatCounter(c, levelStyles[i]))
      const label = segments.join('.') + '.'

      const id = node.attrs?.id as string | undefined
      if (id) byId.set(id, label)
    }
    node.content?.forEach(walk)
  }

  walk(doc)
  return byId
}

interface BlockCondition {
  type: 'simple' | 'python'
  expression: string
}

// ── Condition evaluation ──────────────────────────────────────────────────────

function evaluateOp(op: string, answer: unknown, value: string): boolean {
  const str = String(answer ?? '')
  const num = Number(answer)

  switch (op) {
    case '==':
      if (answer === true  && value === 'true')  return true
      if (answer === false && value === 'false') return true
      return str === value
    case '!=':
      if (answer === true  && value === 'true')  return false
      if (answer === false && value === 'false') return false
      return str !== value
    case '>':            return !isNaN(num) && num >  Number(value)
    case '<':            return !isNaN(num) && num <  Number(value)
    case '>=':           return !isNaN(num) && num >= Number(value)
    case '<=':           return !isNaN(num) && num <= Number(value)
    case 'in':           return value.split(',').map(s => s.trim()).includes(str)
    case 'not_in':       return !value.split(',').map(s => s.trim()).includes(str)
    case 'is_empty':     return answer === undefined || answer === null || answer === ''
    case 'is_not_empty': return answer !== undefined && answer !== null && answer !== ''
    default:             return true
  }
}

export function evaluateSimple(expression: string, answers: Answers): boolean {
  if (!expression.trim()) return true

  const tokens = expression.trim().split(/\s+/)
  let i = 0
  let result: boolean | null = null
  let connector: 'AND' | 'OR' = 'AND'

  while (i < tokens.length) {
    const paramCode = tokens[i++] ?? ''
    const op        = tokens[i++] ?? '=='
    const isNoValue = op === 'is_empty' || op === 'is_not_empty'
    const value     = isNoValue ? '' : (tokens[i++] ?? '')

    const cond = evaluateOp(op, answers[paramCode], value)

    if (result === null) {
      result = cond
    } else if (connector === 'AND') {
      result = result && cond
    } else {
      result = result || cond
    }

    const connToken = tokens[i]?.toUpperCase()
    if (connToken === 'AND' || connToken === 'OR') {
      connector = connToken as 'AND' | 'OR'
      i++
    }
  }

  return result ?? true
}

// ── Value formatting ──────────────────────────────────────────────────────────

const MONTHS_RU_GENITIVE = [
  '', 'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
]
const MONTHS_RU_NOMINATIVE = [
  '', 'январь', 'февраль', 'март', 'апрель', 'май', 'июнь',
  'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь',
]

function formatDateByFormat(answer: string, dateFormat: DateFormat): string {
  try {
    const d = new Date(answer)
    if (isNaN(d.getTime())) return answer
    const dd   = String(d.getDate()).padStart(2, '0')
    const mm   = String(d.getMonth() + 1).padStart(2, '0')
    const yyyy = d.getFullYear()
    const yy   = String(yyyy).slice(-2)
    switch (dateFormat) {
      case 'DOTS':     return `${dd}.${mm}.${yyyy}`
      case 'LONG_RU':
      case 'LONG_RU_GENITIVE': // backend-only; preview как LONG_RU
        return `${d.getDate()} ${MONTHS_RU_GENITIVE[d.getMonth() + 1]} ${yyyy} г.`
      case 'SHORT':    return `${dd}.${mm}.${yy}`
      case 'YEAR_ONLY': return String(yyyy)
      case 'MONTH_YEAR': return `${MONTHS_RU_NOMINATIVE[d.getMonth() + 1]} ${yyyy}`
      case 'ISO':      return `${yyyy}-${mm}-${dd}`
      default:         return `${dd}.${mm}.${yyyy}`
    }
  } catch {
    return answer
  }
}

/**
 * Выбирает нужную форму единицы измерения по числу.
 * Поддерживает формат "штука|штуки|штук" (1|2-4|5+).
 * Если разделителей нет — возвращает строку как есть.
 */
function pluralizeUnit(n: number, unit: string): string {
  const parts = unit.split('|')
  if (parts.length < 3) return unit
  const [one, few, many] = parts
  const abs    = Math.abs(Math.floor(n))
  const mod100 = abs % 100
  const mod10  = abs % 10
  if (mod100 >= 11 && mod100 <= 19) return many
  if (mod10 === 1) return one
  if (mod10 >= 2 && mod10 <= 4) return few
  return many
}

/**
 * Formats a parameter value for client-side preview.
 *
 * @param paramCode      parameter code
 * @param answer         raw answer value
 * @param parameters     list of parameter definitions
 * @param formatOverride per-node format override (from parameterField.attrs.format)
 */
export function formatValue(
  paramCode: string,
  answer: unknown,
  parameters: Parameter[],
  formatOverride?: ParameterFormat | null,
): string {
  if (answer === undefined || answer === null || answer === '') return ''

  const param = parameters.find(p => p.code === paramCode)
  const fmt: ParameterFormat = formatOverride ?? param?.format ?? {}

  if (param?.type === 'BOOLEAN') {
    return (answer === true || answer === 'true') ? 'Да' : 'Нет'
  }

  if (param?.type === 'DATE' && typeof answer === 'string') {
    const dateFormat = fmt.dateFormat ?? 'DOTS'
    return formatDateByFormat(answer, dateFormat)
  }

  if (param?.type === 'NUMBER') {
    const numFmt = fmt.numberFormat ?? 'DEFAULT'
    const rawUnit = fmt.unit ?? ''
    const raw    = String(answer)
    const num    = parseFloat(raw.replace(',', '.'))
    if (isNaN(num)) return raw
    const formatted = new Intl.NumberFormat('ru-RU').format(num)
    const unit = rawUnit ? pluralizeUnit(num, rawUnit) : ''
    switch (numFmt) {
      case 'DEFAULT':  return formatted
      case 'WITH_UNIT': return unit ? `${formatted} ${unit}` : formatted
      case 'PERCENTAGE': return `${formatted}%`
      // backend-only formats — показываем DEFAULT в превью
      case 'WORDS':
      case 'WORDS_WITH_NUMBER':
      case 'WORDS_WITH_UNIT':
      case 'WORDS_WITH_NUMBER_AND_UNIT':
        return unit ? `${formatted} ${unit}` : formatted
      default: return formatted
    }
  }

  if (param?.type === 'FIO' && typeof answer === 'string') {
    return formatFio(answer, fmt.fioFormat, fmt.grammaticalCase)
  }

  return String(answer)
}

// ── Tree transformation ───────────────────────────────────────────────────────

/**
 * Internal sentinel — used when a conditional block expands to multiple sibling nodes.
 * The parent is responsible for flattening these into its content array.
 */
type Fragment = { type: '__fragment__'; content: JSONContent[] }

function renderNode(
  node: JSONContent,
  answers: Answers,
  parameters: Parameter[],
  clauseLabels?: Map<string, string>,
  showAll?: boolean,
  phrasings?: Phrasing[],
  preserveConditionalBlocks?: boolean,
): JSONContent | Fragment | null {

  // ── numberedClause → evaluate own condition ──────────────────────────────
  if (node.type === 'numberedClause') {
    const cond = node.attrs?.condition as BlockCondition | undefined
    if (!showAll && cond?.type === 'simple') {
      const show = evaluateSimple(cond.expression, answers)
      if (!show) return null
    }
    if (!node.content?.length) return node
    const children = flatMap(node.content, child =>
      renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
    return { ...node, content: children }
  }

  // ── phrasingsRef → раскрыть в контент подходящего варианта ──────────────
  if (node.type === 'phrasingsRef') {
    if (!phrasings) return node  // нет данных о формулировках → оставить как есть

    // New format or legacy fallback
    const rawIds = node.attrs?.phrasingsIds as string[] | undefined
    const allIds = rawIds?.length
      ? rawIds
      : node.attrs?.phrasingsId ? [node.attrs.phrasingsId as string] : []

    if (allIds.length === 0) return null

    const sep = (node.attrs?.separator as string | undefined) ?? ', '
    const sepNode: JSONContent = (sep === '\n' || sep === '\\n')
      ? { type: 'hardBreak' }
      : { type: 'text', text: sep }

    const parts: JSONContent[][] = []
    for (const phrasingsId of allIds) {
      const phrasing = phrasings.find(p => p.id === phrasingsId)
      if (!phrasing) continue
      const variant = phrasing.variants.find(v =>
        !v.condition || (v.condition.type === 'SIMPLE'
          ? evaluateSimple(v.condition.expression ?? '', answers)
          : true) // python: show by default on client
      ) ?? phrasing.variants[phrasing.variants.length - 1]
      if (!variant?.content?.length) continue
      const children = flatMap(variant.content as JSONContent[], child =>
        renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
      if (children.length) parts.push(children)
    }

    if (parts.length === 0) return null

    const merged: JSONContent[] = []
    parts.forEach((part, i) => {
      if (i > 0) merged.push(sepNode)
      merged.push(...part)
    })
    return { type: '__fragment__', content: merged }
  }

  // ── clauseRef → resolve label statically ────────────────────────────────
  if (node.type === 'clauseRef') {
    const refId = node.attrs?.refId as string | undefined
    const label = refId ? (clauseLabels?.get(refId) ?? '???') : '???'
    return { ...node, attrs: { ...node.attrs, _resolvedLabel: label } }
  }

  // ── parameterField → keep node, inject current value as attr ────────────
  if (node.type === 'parameterField') {
    const code          = node.attrs?.parameterCode as string
    const formatOverride = node.attrs?.format as ParameterFormat | undefined
    const value         = formatValue(code, answers[code], parameters, formatOverride)
    return { ...node, attrs: { ...node.attrs, value } }
  }

  // ── conditionalInline → preserve as node or evaluate ────────────────────
  if (node.type === 'conditionalInline') {
    if (preserveConditionalBlocks) {
      if (!node.content?.length) return node
      const children = flatMap(node.content, child =>
        renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
      return { ...node, content: children }
    }
    const cond = node.attrs?.condition as BlockCondition | undefined
    let show = true
    if (!showAll && cond?.type === 'simple') {
      show = evaluateSimple(cond.expression, answers)
    }
    if (!show || !node.content?.length) return null
    const children = flatMap(node.content, child =>
      renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
    return { type: '__fragment__', content: children }
  }

  // ── conditionalBlock → preserve as node or evaluate/unwrap ───────────────
  if (node.type === 'conditionalBlock') {
    if (preserveConditionalBlocks) {
      if (!node.content?.length) return node
      const children = flatMap(node.content, child =>
        renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
      return { ...node, content: children }
    }
    const cond = node.attrs?.condition as BlockCondition | undefined
    let show = true
    if (!showAll && cond?.type === 'simple') {
      show = evaluateSimple(cond.expression, answers)
    }
    // python conditions require server — show by default in client preview

    if (!show || !node.content?.length) return null

    const children = flatMap(node.content, child =>
      renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
    return { type: '__fragment__', content: children }
  }

  // ── Everything else: recurse ─────────────────────────────────────────────
  if (!node.content?.length) return node

  const children = flatMap(node.content, child =>
    renderNode(child, answers, parameters, clauseLabels, showAll, phrasings, preserveConditionalBlocks))
  return { ...node, content: children }
}

function flatMap(
  nodes: JSONContent[],
  fn: (n: JSONContent) => JSONContent | Fragment | null,
): JSONContent[] {
  const out: JSONContent[] = []
  for (const n of nodes) {
    const result = fn(n)
    if (!result) continue
    if (result.type === '__fragment__') {
      out.push(...(result as Fragment).content)
    } else {
      out.push(result as JSONContent)
    }
  }
  return out
}

// ── Public API ────────────────────────────────────────────────────────────────

export interface RenderPreviewOptions {
  /**
   * When true, all conditional blocks/clauses are shown regardless of condition result.
   * Use this in the fill/questionnaire workspace where conditions should not
   * collapse content on initial render with empty answers.
   */
  showAllConditionals?: boolean
  /**
   * When true, conditionalBlock/conditionalInline nodes are kept as nodes (not expanded).
   * Use this when seeding the DocumentWorkspace editor so that WorkspaceConditionalBlockNode
   * can handle dynamic visibility based on answers.
   */
  preserveConditionalBlocks?: boolean
  /** Список формулировок для раскрытия phrasingsRef нодов. */
  phrasings?: Phrasing[]
}

/**
 * Transforms template body JSON for live preview:
 * - parameterField nodes gain a `value` attr with the formatted answer
 * - conditionalBlock/numberedClause nodes are evaluated and either flattened or removed
 * - phrasingsRef nodes are expanded to the matching variant content (when phrasings provided)
 *
 * The resulting JSON is suitable for a read-only TipTap editor that uses
 * PreviewParameterFieldNode (which reads the `value` attr for display).
 */
export function renderPreview(
  body: Record<string, unknown>,
  answers: Answers,
  parameters: Parameter[],
  options: RenderPreviewOptions = {},
): JSONContent {
  const doc = body as JSONContent
  // Build clause labels statically (no editor state available here)
  const clauseLabels = buildStaticLabels(doc)
  const rendered = renderNode(
    doc, answers, parameters, clauseLabels,
    options.showAllConditionals, options.phrasings, options.preserveConditionalBlocks,
  )
  if (!rendered || rendered.type === '__fragment__') {
    return { type: 'doc', content: [] }
  }
  return rendered as JSONContent
}
