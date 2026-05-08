import { createContext, useContext } from 'react'
import type { Parameter } from '../../types/template'
import type { Phrasing } from '../../types/templateModel'

interface DesignerContextValue {
  parameters: Parameter[]
  phrasings: Phrasing[]
  published: boolean
}

export const DesignerContext = createContext<DesignerContextValue>({ parameters: [], phrasings: [], published: false })

export const useDesignerContext = () => useContext(DesignerContext)
