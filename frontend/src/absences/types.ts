import type { AbsenceType, AbsenceTypeCode } from '@/api/client'

// Colour per absence type, as CSS variables from tokens.css (design.md, "Absence type colours")
const COLORS: Record<AbsenceTypeCode, { solid: string; soft: string }> = {
  VACATION: { solid: 'var(--color-primary)', soft: 'var(--color-primary-soft)' },
  TRAINING: { solid: 'var(--color-training)', soft: 'var(--color-training-soft)' },
  SICK: { solid: 'var(--color-info)', soft: 'var(--color-info-soft)' },
  PARENTAL: { solid: 'var(--color-success)', soft: 'var(--color-success-soft)' },
  UNPAID: { solid: 'var(--color-muted)', soft: 'var(--color-grey)' },
}

export const typeColor = (code: AbsenceTypeCode) => COLORS[code]

// Display order, as in the Figma frame: vacation first (the API sorts by name)
const ORDER: AbsenceTypeCode[] = ['VACATION', 'TRAINING', 'SICK', 'PARENTAL', 'UNPAID']

export const byTypeOrder = <T extends { type: AbsenceTypeCode }>(a: T, b: T) =>
  ORDER.indexOf(a.type) - ORDER.indexOf(b.type)

/** The type's name from GET /absence-types, or a readable fallback while it loads */
export function typeName(code: AbsenceTypeCode, types: AbsenceType[] | undefined): string {
  return types?.find((t) => t.code === code)?.name ?? code.charAt(0) + code.slice(1).toLowerCase()
}
