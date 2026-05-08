import { Select } from 'antd'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  options: string[] | null
  disabled?: boolean
}

export default function SelectField({ value, onChange, options, disabled }: Props) {
  const selectOptions = (options ?? []).map(o => ({ value: o, label: o }))

  return (
    <Select
      value={typeof value === 'string' && value ? value : undefined}
      onChange={v => onChange(v)}
      options={selectOptions}
      disabled={disabled}
      style={{ width: '100%' }}
      allowClear
      placeholder="Выберите значение"
    />
  )
}
