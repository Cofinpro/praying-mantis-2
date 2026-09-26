import type { CurrentUser } from '@/api/client'

// The same people as the backend's dev seed (0002-SCRUM-23-users.yaml, test-users.md), so the
// mock demo and the real backend accept the same logins and show the same names and roles.
// Team leads: Ana (of Bruno, Carla, Diogo) and Bruno (of Eva, Filipe, Hugo). Alex is the admin.
const user = (
  u: Omit<CurrentUser, 'isAdmin' | 'isTeamLead'> & Partial<CurrentUser>,
): CurrentUser => ({
  isAdmin: false,
  isTeamLead: false,
  ...u,
})

export const mockUsers: CurrentUser[] = [
  user({
    id: 1,
    name: 'Alex Admin',
    email: 'alex.admin@cofinpro.pt',
    client: 'DBIS',
    level: 'ARCHITECT',
    isAdmin: true,
  }),
  user({
    id: 2,
    name: 'Ana Silva',
    email: 'ana.silva@cofinpro.pt',
    client: 'DKB',
    level: 'SENIOR_ARCHITECT',
    isTeamLead: true,
  }),
  user({
    id: 3,
    name: 'Bruno Costa',
    email: 'bruno.costa@cofinpro.pt',
    client: 'DEKA',
    level: 'ARCHITECT',
    isTeamLead: true,
  }),
  user({
    id: 4,
    name: 'Carla Mendes',
    email: 'carla.mendes@cofinpro.pt',
    client: 'DKB',
    level: 'SENIOR',
  }),
  user({
    id: 5,
    name: 'Diogo Pereira',
    email: 'diogo.pereira@cofinpro.pt',
    client: 'DKB',
    level: 'JUNIOR',
  }),
  user({
    id: 6,
    name: 'Eva Santos',
    email: 'eva.santos@cofinpro.pt',
    client: 'VV',
    level: 'EXPERT',
  }),
  user({
    id: 7,
    name: 'Filipe Rocha',
    email: 'filipe.rocha@cofinpro.pt',
    client: 'DEKA',
    level: 'SENIOR',
  }),
  user({
    id: 8,
    name: 'Hugo Marques',
    email: 'hugo.marques@cofinpro.pt',
    client: 'DBIS',
    level: 'EXPERT',
  }),
  user({
    id: 9,
    name: 'Gabriela Lopes',
    email: 'gabriela.lopes@cofinpro.pt',
    client: 'UNION',
    level: 'JUNIOR',
  }),
]

/** Who leads whom in the dev seed (test-users.md), by user id. Alex and Gabriela have no lead. */
export const mockTeamLeads: Record<number, number> = { 3: 2, 4: 2, 5: 2, 6: 3, 7: 3, 8: 3 }

/** Like the backend: emails are compared lower-cased and trimmed */
export const findMockUser = (email: string) =>
  mockUsers.find((u) => u.email === email.trim().toLowerCase())
