import { Extension } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import type { Transaction } from '@tiptap/pm/state'
import type { Node } from '@tiptap/pm/model'

export interface SearchReplaceState {
  searchTerm: string
  caseSensitive: boolean
  wholeWord: boolean
  matches: Array<{ from: number; to: number }>
  currentIndex: number
}

export const searchReplaceKey = new PluginKey<SearchReplaceState>('searchReplace')

function buildRegex(term: string, caseSensitive: boolean, wholeWord: boolean): RegExp | null {
  if (!term) return null
  let escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  if (wholeWord) escaped = `\\b${escaped}\\b`
  return new RegExp(escaped, caseSensitive ? 'g' : 'gi')
}

function findMatches(doc: Node, regex: RegExp): Array<{ from: number; to: number }> {
  const matches: Array<{ from: number; to: number }> = []
  doc.descendants((node, pos) => {
    if (!node.isText || !node.text) return
    regex.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = regex.exec(node.text)) !== null) {
      matches.push({ from: pos + m.index, to: pos + m.index + m[0].length })
    }
  })
  return matches
}

function buildDecorations(
  doc: Node,
  matches: Array<{ from: number; to: number }>,
  currentIndex: number,
): DecorationSet {
  const decorations = matches.map((m, i) =>
    Decoration.inline(m.from, m.to, {
      class: i === currentIndex ? 'search-match-current' : 'search-match',
    }),
  )
  return DecorationSet.create(doc, decorations)
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    searchReplace: {
      setSearch: (term: string, options?: { caseSensitive?: boolean; wholeWord?: boolean }) => ReturnType
      clearSearch: () => ReturnType
      nextMatch: () => ReturnType
      prevMatch: () => ReturnType
      replaceMatch: (replacement: string) => ReturnType
      replaceAll: (replacement: string) => ReturnType
    }
  }
}

export const SearchReplaceExtension = Extension.create({
  name: 'searchReplace',

  addProseMirrorPlugins() {
    return [
      new Plugin<SearchReplaceState>({
        key: searchReplaceKey,

        state: {
          init(): SearchReplaceState {
            return { searchTerm: '', caseSensitive: false, wholeWord: false, matches: [], currentIndex: 0 }
          },

          apply(tr: Transaction, prev: SearchReplaceState): SearchReplaceState {
            const meta = tr.getMeta(searchReplaceKey) as Partial<SearchReplaceState> | undefined
            if (!meta && !tr.docChanged) return prev

            const next: SearchReplaceState = { ...prev, ...(meta ?? {}) }

            if (!next.searchTerm) {
              return { ...next, matches: [], currentIndex: 0 }
            }

            const regex = buildRegex(next.searchTerm, next.caseSensitive, next.wholeWord)
            if (!regex) return { ...next, matches: [], currentIndex: 0 }

            const matches = findMatches(tr.doc, regex)
            const currentIndex = Math.min(prev.currentIndex, Math.max(0, matches.length - 1))
            return { ...next, matches, currentIndex }
          },
        },

        props: {
          decorations(state) {
            const pluginState = searchReplaceKey.getState(state)
            if (!pluginState?.matches.length) return DecorationSet.empty
            return buildDecorations(state.doc, pluginState.matches, pluginState.currentIndex)
          },
        },
      }),
    ]
  },

  addCommands() {
    return {
      setSearch:
        (term, options = {}) =>
        ({ tr, dispatch }) => {
          tr.setMeta(searchReplaceKey, {
            searchTerm: term,
            caseSensitive: options.caseSensitive ?? false,
            wholeWord: options.wholeWord ?? false,
            currentIndex: 0,
          })
          if (dispatch) dispatch(tr)
          return true
        },

      clearSearch:
        () =>
        ({ tr, dispatch }) => {
          tr.setMeta(searchReplaceKey, { searchTerm: '', matches: [], currentIndex: 0 })
          if (dispatch) dispatch(tr)
          return true
        },

      nextMatch:
        () =>
        ({ tr, state, dispatch }) => {
          const pluginState = searchReplaceKey.getState(state)
          if (!pluginState?.matches.length) return false
          const next = (pluginState.currentIndex + 1) % pluginState.matches.length
          tr.setMeta(searchReplaceKey, { currentIndex: next })
          if (dispatch) dispatch(tr)
          return true
        },

      prevMatch:
        () =>
        ({ tr, state, dispatch }) => {
          const pluginState = searchReplaceKey.getState(state)
          if (!pluginState?.matches.length) return false
          const next = (pluginState.currentIndex - 1 + pluginState.matches.length) % pluginState.matches.length
          tr.setMeta(searchReplaceKey, { currentIndex: next })
          if (dispatch) dispatch(tr)
          return true
        },

      replaceMatch:
        (replacement) =>
        ({ tr, state, dispatch }) => {
          const pluginState = searchReplaceKey.getState(state)
          if (!pluginState?.matches.length) return false
          const { from, to } = pluginState.matches[pluginState.currentIndex]
          tr.replaceWith(from, to, replacement ? state.schema.text(replacement) : ([] as never))
          tr.setMeta(searchReplaceKey, { currentIndex: pluginState.currentIndex })
          if (dispatch) dispatch(tr)
          return true
        },

      replaceAll:
        (replacement) =>
        ({ tr, state, dispatch }) => {
          const pluginState = searchReplaceKey.getState(state)
          if (!pluginState?.matches.length) return false
          // Process in reverse to preserve positions
          const sorted = [...pluginState.matches].sort((a, b) => b.from - a.from)
          for (const { from, to } of sorted) {
            tr.replaceWith(from, to, replacement ? state.schema.text(replacement) : ([] as never))
          }
          tr.setMeta(searchReplaceKey, { searchTerm: '', matches: [], currentIndex: 0 })
          if (dispatch) dispatch(tr)
          return true
        },
    }
  },
})
