import { Button, Input, Select, Space } from 'antd'
import type {
  ParameterType,
  ParameterFormat,
  DateFormat,
  NumberDisplayFormat,
  FioDisplayFormat,
  GrammaticalCase,
} from '../../types/templateModel'

const { Option } = Select

interface Props {
  paramType: ParameterType
  value: ParameterFormat | null
  onChange: (fmt: ParameterFormat | null) => void
}

// ── Option lists ──────────────────────────────────────────────────────────────

const DATE_OPTIONS: { value: DateFormat; label: string }[] = [
  { value: 'DOTS',            label: 'Точки — 20.03.2026' },
  { value: 'LONG_RU',         label: 'Полная — 20 марта 2026 г.' },
  { value: 'LONG_RU_GENITIVE',label: 'Прописью — двадцатого марта … (backend)' },
  { value: 'SHORT',           label: 'Краткая — 20.03.26' },
  { value: 'YEAR_ONLY',       label: 'Только год — 2026' },
  { value: 'MONTH_YEAR',      label: 'Месяц и год — март 2026' },
  { value: 'ISO',             label: 'ISO — 2026-03-20' },
]

const NUMBER_OPTIONS: { value: NumberDisplayFormat; label: string }[] = [
  { value: 'DEFAULT',                    label: 'Число — 1 000 000' },
  { value: 'WORDS',                      label: 'Прописью — один миллион (backend)' },
  { value: 'WORDS_WITH_NUMBER',          label: 'Число (прописью) (backend)' },
  { value: 'WITH_UNIT',                  label: 'С единицей — 1 000 000 штук' },
  { value: 'WORDS_WITH_UNIT',            label: 'Прописью с единицей (backend)' },
  { value: 'WORDS_WITH_NUMBER_AND_UNIT', label: 'Число (прописью) с единицей (backend)' },
  { value: 'PERCENTAGE',                 label: 'Процент — 50%' },
]

const FIO_OPTIONS: { value: FioDisplayFormat; label: string }[] = [
  { value: 'FULL',       label: 'Полностью — Иванов Иван Иванович' },
  { value: 'INITIALS',   label: 'С инициалами — Иванов И.И.' },
  { value: 'FIRST_LAST', label: 'Имя Фамилия — Иван Иванов' },
  { value: 'LAST_ONLY',  label: 'Только фамилия — Иванов' },
  { value: 'FIRST_ONLY', label: 'Только имя — Иван' },
]

const CASE_OPTIONS: { value: GrammaticalCase; label: string }[] = [
  { value: 'NOMINATIVE',    label: 'Именительный — Иванов' },
  { value: 'GENITIVE',      label: 'Родительный — Иванова' },
  { value: 'DATIVE',        label: 'Дательный — Иванову' },
  { value: 'ACCUSATIVE',    label: 'Винительный — Иванова' },
  { value: 'INSTRUMENTAL',  label: 'Творительный — Ивановым' },
  { value: 'PREPOSITIONAL', label: 'Предложный — Иванове' },
]

const UNIT_NEEDED: NumberDisplayFormat[] = [
  'WITH_UNIT', 'WORDS_WITH_UNIT', 'WORDS_WITH_NUMBER_AND_UNIT',
]

// ── Component ─────────────────────────────────────────────────────────────────

export default function ParameterFormatSelector({ paramType, value, onChange }: Props) {
  const fmt = value ?? {}

  function patch(partial: Partial<ParameterFormat>) {
    const next: ParameterFormat = { ...fmt, ...partial }
    // Если все поля null/undefined — сбрасываем в null
    const hasAny = Object.values(next).some(v => v !== null && v !== undefined && v !== '')
    onChange(hasAny ? next : null)
  }

  function reset() {
    onChange(null)
  }

  if (paramType === 'DATE') {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Select
          style={{ width: '100%' }}
          placeholder="Формат даты (по умолчанию: 20 марта 2026 г.)"
          allowClear
          value={fmt.dateFormat ?? undefined}
          onChange={(v) => patch({ dateFormat: v ?? null })}
        >
          {DATE_OPTIONS.map(o => (
            <Option key={o.value} value={o.value}>{o.label}</Option>
          ))}
        </Select>
        {value && (
          <Button size="small" onClick={reset}>Сбросить</Button>
        )}
      </Space>
    )
  }

  if (paramType === 'NUMBER') {
    const showUnit = fmt.numberFormat != null && UNIT_NEEDED.includes(fmt.numberFormat)
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Select
          style={{ width: '100%' }}
          placeholder="Формат числа (по умолчанию: 1 000)"
          allowClear
          value={fmt.numberFormat ?? undefined}
          onChange={(v) => patch({ numberFormat: v ?? null, unit: showUnit ? fmt.unit : null })}
        >
          {NUMBER_OPTIONS.map(o => (
            <Option key={o.value} value={o.value}>{o.label}</Option>
          ))}
        </Select>
        {showUnit && (
          <Input
            placeholder="штука|штуки|штук  или просто  рублей"
            value={fmt.unit ?? ''}
            onChange={(e) => patch({ unit: e.target.value || null })}
            size="small"
            title="Для автоматического склонения введите три формы через |: штука|штуки|штук"
          />
        )}
        {value && (
          <Button size="small" onClick={reset}>Сбросить</Button>
        )}
      </Space>
    )
  }

  if (paramType === 'FIO') {
    return (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Select
          style={{ width: '100%' }}
          placeholder="Формат ФИО (по умолчанию: Иванов Иван Иванович)"
          allowClear
          value={fmt.fioFormat ?? undefined}
          onChange={(v) => patch({ fioFormat: v ?? null })}
        >
          {FIO_OPTIONS.map(o => (
            <Option key={o.value} value={o.value}>{o.label}</Option>
          ))}
        </Select>
        <Select
          style={{ width: '100%' }}
          placeholder="Падеж (по умолчанию: именительный)"
          allowClear
          value={fmt.grammaticalCase ?? undefined}
          onChange={(v) => patch({ grammaticalCase: v ?? null })}
        >
          {CASE_OPTIONS.map(o => (
            <Option key={o.value} value={o.value}>{o.label}</Option>
          ))}
        </Select>
        {value && (
          <Button size="small" onClick={reset}>Сбросить</Button>
        )}
      </Space>
    )
  }

  return null
}
