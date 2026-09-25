import { http, HttpResponse } from 'msw'

import type { Hello } from '@/api/client'

// Mock backend that follows api/openapi.yaml (decision #4). Used by `pnpm dev:mock` and by Vitest.
// Paths are wildcards so they match both the dev origin and the jsdom origin in tests.
export const handlers = [
  http.get<never, never, Hello>('*/api/hello', () =>
    HttpResponse.json({ message: 'Hello from the MSW mock' }),
  ),
]
