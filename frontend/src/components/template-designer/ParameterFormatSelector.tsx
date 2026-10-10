import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type {
  ParameterType,
  ParameterFormat,
  DateFormat,
  NumberDisplayFormat,
  FioDisplayFormat,
  GrammaticalCase,
} from '../../types/templateModel'

interface Props {
  paramType: ParameterType
  value: ParameterFormat | null
  onChange: (fmt: ParameterFormat | null) => void
}

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

export default function ParameterFormatSelector({ paramType, value, onChange }: Props) {
  const fmt = value ?? {}

  function patch(partial: Partial<ParameterFormat>) {
    const next: ParameterFormat = { ...fmt, ...partial }
    const hasAny = Object.values(next).some(v => v !== null && v !== undefined && v !== '')
    onChange(hasAny ? next : null)
  }

  function reset() {
    onChange(null)
  }

  if (paramType === 'DATE') {
    return (
      <div className="flex flex-col gap-1 w-full">
        <Select
          value={fmt.dateFormat ?? undefined}
          onValueChange={(v) => patch({ dateFormat: v as DateFormat ?? null })}
        >
          <SelectTrigger className="w-full h-8 text-xs">
            <SelectValue placeholder="Формат даты (по умолчанию: 20 марта 2026 г.)" />
          </SelectTrigger>
          <SelectContent>
            {DATE_OPTIONS.map(o => (
              <SelectItem key={o.value} value={o.value} className="text-xs">
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {value && (
          <Button variant="outline" size="sm" onClick={reset} className="h-7 text-xs w-fit">
            Сбросить
          </Button>
        )}
      </div>
    )
  }

  if (paramType === 'NUMBER') {
    const showUnit = fmt.numberFormat != null && UNIT_NEEDED.includes(fmt.numberFormat)
    return (
      <div className="flex flex-col gap-1 w-full">
        <Select
          value={fmt.numberFormat ?? undefined}
          onValueChange={(v) => patch({ 
            numberFormat: v as NumberDisplayFormat ?? null, 
            unit: showUnit ? fmt.unit : null 
          })}
        >
          <SelectTrigger className="w-full h-8 text-xs">
            <SelectValue placeholder="Формат числа (по умолчанию: 1 000)" />
          </SelectTrigger>
          <SelectContent>
            {NUMBER_OPTIONS.map(o => (
              <SelectItem key={o.value} value={o.value} className="text-xs">
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {showUnit && (
          <Input
            placeholder="штука|штуки|штук  или просто  рублей"
            value={fmt.unit ?? ''}
            onChange={(e) => patch({ unit: e.target.value || null })}
            className="h-8 text-xs"
            title="Для автоматического склонения введите три формы через |: штука|штуки|штук"
          />
        )}
        {value && (
          <Button variant="outline" size="sm" onClick={reset} className="h-7 text-xs w-fit">
            Сбросить
          </Button>
        )}
      </div>
    )
  }

  if (paramType === 'FIO') {
    return (
      <div className="flex flex-col gap-1 w-full">
        <Select
          value={fmt.fioFormat ?? undefined}
          onValueChange={(v) => patch({ fioFormat: v as FioDisplayFormat ?? null })}
        >
          <SelectTrigger className="w-full h-8 text-xs">
            <SelectValue placeholder="Формат ФИО (по умолчанию: Иванов Иван Иванович)" />
          </SelectTrigger>
          <SelectContent>
            {FIO_OPTIONS.map(o => (
              <SelectItem key={o.value} value={o.value} className="text-xs">
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={fmt.grammaticalCase ?? undefined}
          onValueChange={(v) => patch({ grammaticalCase: v as GrammaticalCase ?? null })}
        >
          <SelectTrigger className="w-full h-8 text-xs">
            <SelectValue placeholder="Падеж (по умолчанию: именительный)" />
          </SelectTrigger>
          <SelectContent>
            {CASE_OPTIONS.map(o => (
              <SelectItem key={o.value} value={o.value} className="text-xs">
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {value && (
          <Button variant="outline" size="sm" onClick={reset} className="h-7 text-xs w-fit">
            Сбросить
          </Button>
        )}
      </div>
    )
  }

  return null
}
