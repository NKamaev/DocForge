/**
 * Client-side FIO formatter — port of FioFormatterService.java.
 *
 * Input: «Фамилия Имя Отчество» (2 or 3 words).
 * Handles the most common Russian name patterns.
 * Non-standard / foreign names are returned unchanged.
 */

import type { FioDisplayFormat, GrammaticalCase } from '../types/templateModel'

export function formatFio(
  fullFio: string,
  displayFormat?: FioDisplayFormat | null,
  grammaticalCase?: GrammaticalCase | null,
): string {
  if (!fullFio?.trim()) return fullFio ?? ''

  const fmt = displayFormat   ?? 'FULL'
  const cas = grammaticalCase ?? 'NOMINATIVE'

  const parts = fullFio.trim().split(/\s+/)
  let last  = capitalize(parts[0] ?? '')
  let first = capitalize(parts[1] ?? '')
  let patr  = capitalize(parts[2] ?? '')

  const male = detectMale(patr, first)

  if (cas !== 'NOMINATIVE') {
    last  = declineSurname(last,  cas, male)
    first = declineFirstName(first, cas, male)
    patr  = declinePatronymic(patr, cas, male)
  }

  switch (fmt) {
    case 'FULL':
      return [last, first, patr].filter(Boolean).join(' ')
    case 'INITIALS': {
      let result = last
      if (first) result += ` ${first[0].toUpperCase()}.`
      if (patr)  result += `${patr[0].toUpperCase()}.`
      return result
    }
    case 'FIRST_LAST': return [first, last].filter(Boolean).join(' ')
    case 'LAST_ONLY':  return last
    case 'FIRST_ONLY': return first
    default:           return fullFio
  }
}

// ── Gender detection ──────────────────────────────────────────────────────────

function detectMale(patronymic: string, firstName: string): boolean {
  if (patronymic) {
    const p = patronymic.toLowerCase()
    if (p.endsWith('ович') || p.endsWith('евич') || p.endsWith('ич')) return true
    if (p.endsWith('овна') || p.endsWith('евна') || p.endsWith('ична') || p.endsWith('инична')) return false
  }
  if (firstName) {
    const f = firstName.toLowerCase()
    if (f.endsWith('а') || f.endsWith('я')) return false
  }
  return true
}

// ── Decline surname ───────────────────────────────────────────────────────────

function declineSurname(name: string, cas: GrammaticalCase, male: boolean): string {
  if (!name) return name
  const low = name.toLowerCase()

  if (low.endsWith('ский') || low.endsWith('цкий')) {
    return name.slice(0, -2) + adjMaleSuffix(cas)
  }
  if (low.endsWith('ской')) {
    return name.slice(0, -2) + adjMaleSuffix(cas)
  }
  if (low.endsWith('ская') || low.endsWith('цкая')) {
    return name.slice(0, -2) + adjFemaleSuffix(cas)
  }
  if ((low.endsWith('ий') || low.endsWith('ый')) && male) {
    return name.slice(0, -2) + adjMaleSuffix(cas)
  }
  if (low.endsWith('ая') || low.endsWith('яя')) {
    return name.slice(0, -2) + adjFemaleSuffix(cas)
  }
  if (low.endsWith('ов') || low.endsWith('ев') || low.endsWith('ёв')) {
    return male ? name + noun1Suffix(cas) : name
  }
  if ((low.endsWith('ова') || low.endsWith('ева') || low.endsWith('ёва')) && !male) {
    return nounASuffix(name, name.length - 1, cas)
  }
  if (low.endsWith('ин') || low.endsWith('ын')) {
    return male ? name + noun2Suffix(cas) : name
  }
  if ((low.endsWith('ина') || low.endsWith('ына')) && !male) {
    return nounASuffix(name, name.length - 1, cas)
  }
  // Несклоняемые окончания
  if (/[оеиу]$/.test(low)) return name
  if (low.endsWith('а')) return nounASuffix(name, name.length - 1, cas)
  if (low.endsWith('я')) return nounYaSuffix(name, name.length - 1, cas)
  // Мужские на согласную
  if (male) return name + noun1Suffix(cas)
  return name
}

// ── Decline first name ────────────────────────────────────────────────────────

function declineFirstName(name: string, cas: GrammaticalCase, male: boolean): string {
  if (!name) return name
  const low = name.toLowerCase()

  if (male) {
    if (low.endsWith('й')) return name.slice(0, -1) + maleJSuffix(cas)
    if (low.endsWith('ь')) return name.slice(0, -1) + maleSoftSuffix(cas)
    if (low.endsWith('а')) return nounASuffix(name, name.length - 1, cas)
    if (low.endsWith('я')) return nounYaSuffix(name, name.length - 1, cas)
    return name + noun1Suffix(cas)
  } else {
    if (low.endsWith('ья') || low.endsWith('ия')) {
      return name.slice(0, -1) + femaleIySuffix(cas)
    }
    if (low.endsWith('я')) return nounYaSuffix(name, name.length - 1, cas)
    if (low.endsWith('а')) return nounASuffix(name, name.length - 1, cas)
    return name
  }
}

// ── Decline patronymic ────────────────────────────────────────────────────────

function declinePatronymic(name: string, cas: GrammaticalCase, male: boolean): string {
  if (!name) return name
  const low = name.toLowerCase()

  if (male) {
    if (low.endsWith('ович') || low.endsWith('евич') || low.endsWith('ич')) {
      return name + noun1Suffix(cas)
    }
  } else {
    if (low.endsWith('овна') || low.endsWith('евна') || low.endsWith('ична')) {
      return nounASuffix(name, name.length - 1, cas)
    }
  }
  return name
}

// ── Suffix tables ─────────────────────────────────────────────────────────────

function noun1Suffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return ''
    case 'GENITIVE':      return 'а'
    case 'DATIVE':        return 'у'
    case 'ACCUSATIVE':    return 'а'
    case 'INSTRUMENTAL':  return 'ом'
    case 'PREPOSITIONAL': return 'е'
  }
}

function noun2Suffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return ''
    case 'GENITIVE':      return 'а'
    case 'DATIVE':        return 'у'
    case 'ACCUSATIVE':    return 'а'
    case 'INSTRUMENTAL':  return 'ым'
    case 'PREPOSITIONAL': return 'е'
  }
}

function maleJSuffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return 'й'
    case 'GENITIVE':      return 'я'
    case 'DATIVE':        return 'ю'
    case 'ACCUSATIVE':    return 'я'
    case 'INSTRUMENTAL':  return 'ем'
    case 'PREPOSITIONAL': return 'е'
  }
}

function maleSoftSuffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return 'ь'
    case 'GENITIVE':      return 'я'
    case 'DATIVE':        return 'ю'
    case 'ACCUSATIVE':    return 'я'
    case 'INSTRUMENTAL':  return 'ем'
    case 'PREPOSITIONAL': return 'е'
  }
}

function femaleIySuffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return 'я'
    case 'GENITIVE':      return 'и'
    case 'DATIVE':        return 'и'
    case 'ACCUSATIVE':    return 'ю'
    case 'INSTRUMENTAL':  return 'ей'
    case 'PREPOSITIONAL': return 'и'
  }
}

function nounASuffix(name: string, stemEnd: number, cas: GrammaticalCase): string {
  const stem = name.slice(0, stemEnd)
  const last = stem ? stem[stem.length - 1].toLowerCase() : ''
  const hissing = 'жшщчц'.includes(last)
  switch (cas) {
    case 'NOMINATIVE':    return stem + 'а'
    case 'GENITIVE':      return stem + 'ы'
    case 'DATIVE':        return stem + 'е'
    case 'ACCUSATIVE':    return stem + 'у'
    case 'INSTRUMENTAL':  return stem + (hissing ? 'ей' : 'ой')
    case 'PREPOSITIONAL': return stem + 'е'
  }
}

function nounYaSuffix(name: string, stemEnd: number, cas: GrammaticalCase): string {
  const stem = name.slice(0, stemEnd)
  switch (cas) {
    case 'NOMINATIVE':    return stem + 'я'
    case 'GENITIVE':      return stem + 'и'
    case 'DATIVE':        return stem + 'е'
    case 'ACCUSATIVE':    return stem + 'ю'
    case 'INSTRUMENTAL':  return stem + 'ей'
    case 'PREPOSITIONAL': return stem + 'е'
  }
}

function adjMaleSuffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return 'ий'
    case 'GENITIVE':      return 'ого'
    case 'DATIVE':        return 'ому'
    case 'ACCUSATIVE':    return 'ого'
    case 'INSTRUMENTAL':  return 'им'
    case 'PREPOSITIONAL': return 'ом'
  }
}

function adjFemaleSuffix(cas: GrammaticalCase): string {
  switch (cas) {
    case 'NOMINATIVE':    return 'ая'
    case 'GENITIVE':      return 'ой'
    case 'DATIVE':        return 'ой'
    case 'ACCUSATIVE':    return 'ую'
    case 'INSTRUMENTAL':  return 'ой'
    case 'PREPOSITIONAL': return 'ой'
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function capitalize(s: string): string {
  if (!s) return s
  return s[0].toUpperCase() + s.slice(1).toLowerCase()
}
