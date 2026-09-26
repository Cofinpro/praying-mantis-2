import type { AdminUser } from '@/api/client'
import { conflictType, fieldErrors, problemMessage } from '@/api/problems'

// FE-9.1, frame "12 Admin – Edit user": the backend's answers belong under the field they are
// about. A 400 names its fields; a 409 names the rule, and each rule has an obvious field.

/** The dialog's fields, named as in the request body, so a 400's `field` maps straight onto them */
export type UserField =
  'name' | 'email' | 'client' | 'level' | 'teamLeadId' | 'isAdmin' | 'password'

export interface UserFormErrors {
  fields: Partial<Record<UserField, string>>
  /** Anything that doesn't belong to one field (a network error, a 500, an unknown 409) */
  banner: string | null
}

interface Context {
  /** Every user, to name who already has the email */
  users: AdminUser[]
  /** The user being edited (undefined when adding) and the team lead picked in the form */
  editedName?: string
  teamLeadName?: string
  email: string
}

export function userFormErrors(error: unknown, context: Context): UserFormErrors {
  switch (conflictType(error)) {
    case '/problems/email-taken': {
      // The backend stores emails lower-cased (BE-1.1), so compare them the same way
      const email = context.email.trim().toLowerCase()
      const owner = context.users.find((u) => u.email.toLowerCase() === email)
      return {
        fields: {
          email: owner
            ? `This email is already used by ${owner.name}.`
            : 'This email is already used by another user.',
        },
        banner: null,
      }
    }
    case '/problems/team-lead-cycle':
      return {
        fields: {
          teamLeadId:
            context.editedName && context.teamLeadName
              ? `${context.editedName} already leads ${context.teamLeadName}, directly or through others. Pick another team lead.`
              : problemMessage(error)!,
        },
        banner: null,
      }
    case '/problems/last-admin':
      return { fields: { isAdmin: problemMessage(error)! }, banner: null }
  }
  return { fields: fieldErrors(error), banner: problemMessage(error) }
}
