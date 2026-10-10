import type { QuestionnaireQuestion } from '../../types/document'
import TextField from './field-types/TextField'
import NumberField from './field-types/NumberField'
import DateField from './field-types/DateField'
import FioField from './field-types/FioField'
import BooleanField from './field-types/BooleanField'
import SelectField from './field-types/SelectField'
import DirectoryField from './field-types/DirectoryField'

interface Props {
  question: QuestionnaireQuestion
  value: unknown
  onChange: (val: unknown) => void
  /** For directory type — called with all sub-field changes at once */
  onMultiChange?: (changes: Record<string, unknown>) => void
}

function FieldByType({ question, value, onChange, onMultiChange }: Props) {
  const common = { value, onChange }

  if (question.type === 'directory') {
    if (!question.directoryId) return <span className="text-amber-600 text-sm">Справочник не задан</span>
    return (
      <DirectoryField
        directoryId={question.directoryId}
        directoryFields={question.directoryFields ?? []}
        paramCode={question.parameterCode}
        value={value}
        onMultiChange={onMultiChange ?? (() => {})}
      />
    )
  }

  switch (question.type) {
    case 'number':  return <NumberField {...common} />
    case 'date':    return <DateField   {...common} />
    case 'fio':     return <FioField    {...common} />
    case 'boolean': return <BooleanField {...common} />
    case 'select':  return <SelectField  {...common} options={question.options} />
    default:        return <TextField   {...common} />
  }
}

export default function QuestionRenderer({ question, value, onChange, onMultiChange }: Props) {
  return (
    <div className="space-y-1 mb-5">
      <div className="flex items-start gap-1">
        <label className="text-sm font-medium text-foreground">
          {question.label}
          {question.required && (
            <span className="text-destructive ml-1">*</span>
          )}
        </label>
      </div>
      <FieldByType
        question={question}
        value={value}
        onChange={onChange}
        onMultiChange={onMultiChange}
      />
      {question.hint && (
        <p className="text-xs text-muted-foreground">{question.hint}</p>
      )}
    </div>
  )
}
