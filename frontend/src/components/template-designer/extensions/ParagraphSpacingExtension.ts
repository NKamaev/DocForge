import { Extension } from '@tiptap/core'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    paragraphSpacing: {
      setParagraphSpacing: (opts: {
        spaceBefore?: string | null
        spaceAfter?: string | null
        firstLineIndent?: string | null
        marginLeft?: string | null
        marginRight?: string | null
      }) => ReturnType
    }
  }
}

export const ParagraphSpacingExtension = Extension.create({
  name: 'paragraphSpacing',

  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading', 'numberedClause'],
        attributes: {
          spaceBefore: {
            default: null as string | null,
            keepOnSplit: true,
            parseHTML: (element) => element.style.marginTop || null,
            renderHTML: (attributes) => {
              if (!attributes.spaceBefore) return {}
              return { style: `margin-top: ${attributes.spaceBefore}` }
            },
          },
          spaceAfter: {
            default: null as string | null,
            keepOnSplit: true,
            parseHTML: (element) => element.style.marginBottom || null,
            renderHTML: (attributes) => {
              if (!attributes.spaceAfter) return {}
              return { style: `margin-bottom: ${attributes.spaceAfter}` }
            },
          },
          firstLineIndent: {
            default: null as string | null,
            keepOnSplit: true,
            parseHTML: (element) => element.style.textIndent || null,
            renderHTML: (attributes) => {
              if (!attributes.firstLineIndent) return {}
              return { style: `text-indent: ${attributes.firstLineIndent}` }
            },
          },
          marginLeft: {
            default: null as string | null,
            keepOnSplit: true,
            parseHTML: (element) => {
              const ml = element.style.marginLeft
              // Only read mm values; em-based values belong to IndentExtension
              if (!ml || ml.endsWith('em')) return null
              return ml
            },
            renderHTML: (attributes) => {
              if (!attributes.marginLeft) return {}
              return { style: `margin-left: ${attributes.marginLeft}` }
            },
          },
          marginRight: {
            default: null as string | null,
            keepOnSplit: true,
            parseHTML: (element) => element.style.marginRight || null,
            renderHTML: (attributes) => {
              if (!attributes.marginRight) return {}
              return { style: `margin-right: ${attributes.marginRight}` }
            },
          },
        },
      },
    ]
  },

  addCommands() {
    return {
      setParagraphSpacing:
        (opts) =>
        ({ tr, state, dispatch }) => {
          const { from, to } = state.selection
          state.doc.nodesBetween(from, to, (node, pos) => {
            if (!['paragraph', 'heading', 'numberedClause'].includes(node.type.name)) return
            const updates: Record<string, string | null> = {}
            if (opts.spaceBefore !== undefined) updates.spaceBefore = opts.spaceBefore ?? null
            if (opts.spaceAfter  !== undefined) updates.spaceAfter  = opts.spaceAfter  ?? null
            if (opts.firstLineIndent !== undefined) updates.firstLineIndent = opts.firstLineIndent ?? null
            if (opts.marginLeft  !== undefined) updates.marginLeft  = opts.marginLeft  ?? null
            if (opts.marginRight !== undefined) updates.marginRight = opts.marginRight ?? null
            tr.setNodeMarkup(pos, undefined, { ...node.attrs, ...updates })
            // Don't descend into numberedClause children — spacing on the clause
            // is handled at the wrapper level; inner paragraph must not get
            // text-indent / margin-left applied as inline styles.
            if (node.type.name === 'numberedClause') return false
          })
          if (dispatch) dispatch(tr)
          return true
        },
    }
  },
})
