import { Extension } from '@tiptap/core'
import type { Editor } from '@tiptap/core'
import Suggestion from '@tiptap/suggestion'

export interface SlashCommand {
  title: string
  aliases: string[]
  description: string
  action: (editor: Editor) => void
}

export function buildSlashCommands(
  params: { code: string; label: string }[],
  onImageClick?: () => void,
): SlashCommand[] {
  return [
    {
      title: 'Заголовок 1',
      aliases: ['h1', 'heading1', 'заголовок1'],
      description: 'Большой заголовок',
      action: (e) => e.chain().focus().toggleHeading({ level: 1 }).run(),
    },
    {
      title: 'Заголовок 2',
      aliases: ['h2', 'heading2', 'заголовок2'],
      description: 'Средний заголовок',
      action: (e) => e.chain().focus().toggleHeading({ level: 2 }).run(),
    },
    {
      title: 'Заголовок 3',
      aliases: ['h3', 'heading3', 'заголовок3'],
      description: 'Малый заголовок',
      action: (e) => e.chain().focus().toggleHeading({ level: 3 }).run(),
    },
    {
      title: 'Маркированный список',
      aliases: ['bullet', 'список', 'ul'],
      description: 'Ненумерованный список',
      action: (e) => e.chain().focus().toggleBulletList().run(),
    },
    {
      title: 'Нумерованный список',
      aliases: ['numbered', 'нумер', 'ol'],
      description: 'Нумерованный список',
      action: (e) => e.chain().focus().toggleOrderedList().run(),
    },
    {
      title: 'Нумерованный пункт',
      aliases: ['clause', 'пункт'],
      description: 'Пункт договора с авто-нумерацией',
      action: (e) => e.chain().focus().insertNumberedClause(1).run(),
    },
    {
      title: 'Таблица',
      aliases: ['table', 'таблица'],
      description: 'Вставить таблицу 3×3',
      action: (e) => e.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: false }).run(),
    },
    {
      title: 'Параметр',
      aliases: ['param', 'параметр'],
      description: 'Вставить поле параметра',
      action: (e) => {
        const p = params[0]
        if (p) e.chain().focus().insertParameterField({ parameterCode: p.code, display: p.label }).run()
      },
    },
    {
      title: 'Горизонтальная линия',
      aliases: ['hr', 'линия', 'divider'],
      description: 'Разделительная линия',
      action: (e) => e.chain().focus().setHorizontalRule().run(),
    },
    {
      title: 'Разрыв страницы',
      aliases: ['break', 'разрыв', 'pagebreak'],
      description: 'Принудительный разрыв страницы',
      action: (e) => e.chain().focus().insertPageBreak().run(),
    },
    {
      title: 'Изображение',
      aliases: ['image', 'фото', 'картинка'],
      description: 'Вставить изображение',
      action: (_e) => onImageClick?.(),
    },
  ]
}

export const SlashCommandExtension = Extension.create({
  name: 'slashCommand',

  addOptions() {
    return {
      suggestion: {
        char: '/',
        startOfLine: false,
        allowSpaces: false,
        command: ({ editor, range, props }: { editor: Editor; range: { from: number; to: number }; props: SlashCommand }) => {
          editor.chain().focus().deleteRange(range).run()
          props.action(editor)
        },
      },
    }
  },

  addProseMirrorPlugins() {
    return [
      Suggestion({
        editor: this.editor,
        ...this.options.suggestion,
      }),
    ]
  },
})
