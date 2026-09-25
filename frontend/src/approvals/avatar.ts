// Avatars in the Approvals tables get one of the frame's colours, fixed per person
const AVATAR_COLORS = [
  'var(--color-primary)',
  'var(--color-training)',
  'var(--color-info)',
  'var(--color-success-ink)',
]

export const avatarColor = (userId: number) => AVATAR_COLORS[userId % AVATAR_COLORS.length]!
