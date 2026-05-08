import { Extension } from '@tiptap/core'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    lineSpacing: {
      setLineSpacing: (spacing: string) => ReturnType
    }
  }
}

export const LineSpacingExtension = Extension.create({
  name: 'lineSpacing',

  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading', 'numberedClause'],
        attributes: {
          lineSpacing: {
            default: null,
            keepOnSplit: true,
            parseHTML: (element) => element.style.lineHeight ?? null,
            renderHTML: (attributes) => {
              if (!attributes.lineSpacing) return {}
              return { style: `line-height: ${attributes.lineSpacing}` }
            },
          },
        },
      },
    ]
  },

  addCommands() {
    return {
      setLineSpacing:
        (spacing: string) =>
        ({ tr, state, dispatch }) => {
          const { from, to } = state.selection
          state.doc.nodesBetween(from, to, (node, pos) => {
            const types = ['paragraph', 'heading', 'numberedClause']
            if (!types.includes(node.type.name)) return
            tr.setNodeMarkup(pos, undefined, {
              ...node.attrs,
              lineSpacing: spacing,
            })
          })
          if (dispatch) dispatch(tr)
          return true
        },
    }
  },
})
