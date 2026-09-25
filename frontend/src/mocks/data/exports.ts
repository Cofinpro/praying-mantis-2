import type { ExportTemplate } from '@/api/client'

// The backend's templates (decision 33, BE-8.3), ordered by name as GET /export-templates sends them
export const exportTemplates: ExportTemplate[] = [
  { code: 'DBIS', name: 'DBIS Stundenübersicht (mock)', client: 'DBIS' },
  { code: 'DEKA', name: 'Deka Stundennachweis (mock)', client: 'DEKA' },
  { code: 'DKB', name: 'DKB Leistungsnachweis (mock)', client: 'DKB' },
  { code: 'GENERIC', name: 'Generic monthly timesheet' },
  { code: 'UNION', name: 'Union Investment Leistungsnachweis (mock)', client: 'UNION' },
  { code: 'VV', name: 'VV Tätigkeitsnachweis (mock)', client: 'VV' },
]

export const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
