export type ParameterType = 'TEXT' | 'NUMBER' | 'DATE' | 'FIO' | 'BOOLEAN' | 'SELECT' | 'DIRECTORY'

// ── Parameter Formats ─────────────────────────────────────────────────────────

export type DateFormat =
  | 'DOTS'             // 20.03.2026
  | 'LONG_RU'          // 20 марта 2026 г.
  | 'LONG_RU_GENITIVE' // двадцатого марта ... (только backend)
  | 'SHORT'            // 20.03.26
  | 'YEAR_ONLY'        // 2026
  | 'MONTH_YEAR'       // март 2026
  | 'ISO'              // 2026-03-20

export type NumberDisplayFormat =
  | 'DEFAULT'
  | 'WORDS'
  | 'WORDS_WITH_NUMBER'
  | 'WITH_UNIT'
  | 'WORDS_WITH_UNIT'
  | 'WORDS_WITH_NUMBER_AND_UNIT'
  | 'PERCENTAGE'

export type FioDisplayFormat =
  | 'FULL'
  | 'INITIALS'
  | 'FIRST_LAST'
  | 'LAST_ONLY'
  | 'FIRST_ONLY'

export type GrammaticalCase =
  | 'NOMINATIVE'
  | 'GENITIVE'
  | 'DATIVE'
  | 'ACCUSATIVE'
  | 'INSTRUMENTAL'
  | 'PREPOSITIONAL'

export interface ParameterFormat {
  dateFormat?: DateFormat | null
  numberFormat?: NumberDisplayFormat | null
  /** Единица измерения для WITH_UNIT вариантов: «рублей», «штук» и т.д. */
  unit?: string | null
  fioFormat?: FioDisplayFormat | null
  grammaticalCase?: GrammaticalCase | null
}

// ── Phrasings ─────────────────────────────────────────────────────────────────

export interface PhrasingVariant {
  id: string
  /** null = вариант-умолчание (показывается, если ни один из предыдущих не подошёл). */
  condition?: Condition | null
  /** Список инлайн TipTap-нодов (text, parameterField, clauseRef, ...). */
  content: import('@tiptap/core').JSONContent[]
}

export interface Phrasing {
  id: string
  name: string
  description?: string | null
  variants: PhrasingVariant[]
}
export type ConditionType = 'SIMPLE' | 'PYTHON'

export interface ParameterValidation {
  min?: number | null
  max?: number | null
  pattern?: string | null
}

export interface Condition {
  type: ConditionType
  expression?: string | null
  scriptId?: string | null
}

export interface Parameter {
  code: string
  label: string
  type: ParameterType
  required: boolean
  defaultValue?: unknown
  options?: string[] | null
  validation?: ParameterValidation | null
  /** UUID справочника (только для type=DIRECTORY) */
  directoryId?: string | null
  /** Ключи полей справочника для подстановки в шаблон */
  directoryFields?: string[] | null
  /** Настройки отображения при подстановке. null = системные defaults. */
  format?: ParameterFormat | null
}

export interface Question {
  id: string
  parameterCode: string
  label: string
  hint?: string | null
  order: number
  visibilityCondition?: Condition | null
}

export interface QuestionnairePage {
  title: string
  questions: Question[]
}

export interface Questionnaire {
  title: string
  pages: QuestionnairePage[]
}

export interface Script {
  id: string
  name: string
  code: string
  description?: string | null
}

export interface PageMargins {
  top: number
  right: number
  bottom: number
  left: number
}

export interface ParagraphDefaults {
  spaceBefore?: number     // pt
  spaceAfter?: number      // pt
  marginLeft?: number      // mm
  marginRight?: number     // mm
  firstLineIndent?: number // mm
}

export interface DocumentFormatting {
  pageSize: string
  orientation?: 'portrait' | 'landscape'
  margins: PageMargins
  defaultFont: string
  defaultFontSize: number
  lineSpacing: number
  paragraphDefaults?: ParagraphDefaults
}
