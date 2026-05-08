import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import ParameterPanel from './ParameterPanel'
import type { Parameter } from '../../types/template'

const PARAMS: Parameter[] = [
  {
    code: 'name',
    label: 'Имя',
    type: 'TEXT',
    required: true,
    defaultValue: null,
    options: null,
    validation: null,
    directoryId: null,
    directoryFields: null,
    format: null,
  },
]

describe('ParameterPanel', () => {
  it('shows "Добавить" button in editable mode', () => {
    render(
      <ParameterPanel
        parameters={PARAMS}
        onChange={() => {}}
        usedCodes={new Set()}
        readOnly={false}
      />
    )
    expect(screen.getByRole('button', { name: /добавить/i })).toBeInTheDocument()
  })

  it('hides "Добавить" button in readOnly mode', () => {
    render(
      <ParameterPanel
        parameters={PARAMS}
        onChange={() => {}}
        usedCodes={new Set()}
        readOnly={true}
      />
    )
    expect(screen.queryByRole('button', { name: /добавить/i })).not.toBeInTheDocument()
  })

  it('hides edit/delete buttons for parameters in readOnly mode', () => {
    render(
      <ParameterPanel
        parameters={PARAMS}
        onChange={() => {}}
        usedCodes={new Set()}
        readOnly={true}
      />
    )
    expect(screen.queryByRole('button', { name: /редактировать/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /удалить/i })).not.toBeInTheDocument()
  })

  it('drag handle has cursor:default in readOnly mode', () => {
    render(
      <ParameterPanel
        parameters={PARAMS}
        onChange={() => {}}
        usedCodes={new Set()}
        readOnly={true}
      />
    )
    const handle = screen.getByTestId('drag-handle')
    expect(handle).toHaveStyle({ cursor: 'default' })
  })

  it('drag handle has cursor:grab in editable mode', () => {
    render(
      <ParameterPanel
        parameters={PARAMS}
        onChange={() => {}}
        usedCodes={new Set()}
        readOnly={false}
      />
    )
    const handle = screen.getByTestId('drag-handle')
    expect(handle).toHaveStyle({ cursor: 'grab' })
  })
})
