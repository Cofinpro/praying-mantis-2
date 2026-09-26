import { conflictType, fieldErrors, problemMessage } from '@/api/problems'

// FE-9.3, the project dialog: what the admin typed is checked first, and the backend's answers go
// under the field they are about (a 400 names it; the 409 is about the code).

/** The dialog's fields, named as in the request body, so a 400's `field` maps straight onto them */
export type ProjectField = 'code' | 'name' | 'client' | 'isBillable' | 'isActive'

export interface ProjectFormErrors {
  fields: Partial<Record<ProjectField, string>>
  /** Anything that doesn't belong to one field (a network error, a 500, a 404) */
  banner: string | null
}

const CODE = /^[A-Za-z0-9-]{2,30}$/
const CODE_RULE = 'Use 2 to 30 letters, digits or dashes, e.g. DKB-CORE.'

/** The contract's rules (ProjectInput), before anything is sent */
export function checkProject(code: string, name: string): ProjectFormErrors['fields'] {
  const fields: ProjectFormErrors['fields'] = {}
  if (!CODE.test(code.trim())) fields.code = CODE_RULE
  if (!name.trim()) fields.name = 'Enter a name.'
  return fields
}

/** `sentCode` is the code of the request that failed, to name it in the 409 */
export function projectFormErrors(error: unknown, sentCode: string): ProjectFormErrors {
  if (conflictType(error) === '/problems/project-code-taken') {
    const code = sentCode.trim().toUpperCase()
    return { fields: { code: `Another project already has the code ${code}.` }, banner: null }
  }
  const fields = fieldErrors(error) as ProjectFormErrors['fields']
  // The generated validation answers with the regex ("must match ..."), which nobody should read
  if (fields.code?.startsWith('must match')) fields.code = CODE_RULE
  return { fields, banner: problemMessage(error) }
}
