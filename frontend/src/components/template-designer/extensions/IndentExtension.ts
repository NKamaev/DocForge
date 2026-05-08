import { Extension } from '@tiptap/core'

const INDENT_STEP = 2 // em
const MAX_INDENT = 10

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    indent: {
      indent: () => ReturnType
      outdent: () => ReturnType
    }
  }
}

export const IndentExtension = Extension.create({
  name: 'indent',

  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading', 'numberedClause'],
        attributes: {
          indent: {
            default: 0,
            keepOnSplit: true,
            parseHTML: (element) => {
              const ml = element.style.marginLeft
              if (!ml) return 0
              const val = parseFloat(ml)
              return isNaN(val) ? 0 : Math.round(val / INDENT_STEP)
            },
            renderHTML: (attributes) => {
              if (!attributes.indent || attributes.indent === 0) return {}
              return { style: `margin-left: ${attributes.indent * INDENT_STEP}em` }
            },
          },
        },
      },
    ]
  },

  addCommands() {
    return {
      indent: () => ({ tr, state, dispatch }) => {
        const { selection } = state
        const { from, to } = selection

        state.doc.nodesBetween(from, to, (node, pos) => {
          const types = ['paragraph', 'heading', 'numberedClause']
          if (!types.includes(node.type.name)) return

          const currentIndent = (node.attrs.indent as number) ?? 0
          if (currentIndent >= MAX_INDENT) return

          tr.setNodeMarkup(pos, undefined, {
            ...node.attrs,
            indent: currentIndent + 1,
          })
          if (node.type.name === 'numberedClause') return false
        })

        if (dispatch) dispatch(tr)
        return true
      },

      outdent: () => ({ tr, state, dispatch }) => {
        const { selection } = state
        const { from, to } = selection

        state.doc.nodesBetween(from, to, (node, pos) => {
          const types = ['paragraph', 'heading', 'numberedClause']
          if (!types.includes(node.type.name)) return

          const currentIndent = (node.attrs.indent as number) ?? 0
          if (currentIndent <= 0) return

          tr.setNodeMarkup(pos, undefined, {
            ...node.attrs,
            indent: currentIndent - 1,
          })
          if (node.type.name === 'numberedClause') return false
        })

        if (dispatch) dispatch(tr)
        return true
      },
    }
  },

  addKeyboardShortcuts() {
    return {
      Tab: () => this.editor.commands.indent(),
      'Shift-Tab': () => this.editor.commands.outdent(),
    }
  },
})
