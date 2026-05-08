import { Form, Typography } from 'antd'
import type { QuestionnaireQuestion } from '../../types/document'
import TextField from './field-types/TextField'
import NumberField from './field-types/NumberField'
import DateField from './field-types/DateField'
import FioField from './field-types/FioField'
import BooleanField from './field-types/BooleanField'
import SelectField from './field-types/SelectField'
import DirectoryField from './field-types/DirectoryField'

const { Text } = Typography

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
    if (!question.directoryId) return <Text type="warning">Справочник не задан</Text>
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
    <Form.Item
      label={
        <span>
          {question.label}
          {question.required && (
            <Text type="danger" style={{ marginLeft: 4 }}>*</Text>
          )}
        </span>
      }
      extra={question.hint
        ? <Text type="secondary" style={{ fontSize: 12 }}>{question.hint}</Text>
        : undefined}
      style={{ marginBottom: 20 }}
    >
      <FieldByType
        question={question}
        value={value}
        onChange={onChange}
        onMultiChange={onMultiChange}
      />
    </Form.Item>
  )
}
