// All TanStack Query keys in one place, so the code that writes a cache entry and the code that
// reads it can't drift apart (a typo would silently mean an extra request instead of a cache hit)
export const queryKeys = {
  me: ['me'] as const,
}
