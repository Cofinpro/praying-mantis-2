import { QueryClient } from '@tanstack/vue-query'

// One QueryClient for the whole app: it holds the cache of all server data (decision #6). Its own
// module so client.ts can drop the cached user on a 401 without importing main.ts.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Treat fetched data as fresh for 30 s, so navigating between pages doesn't refetch it
      staleTime: 30_000,
    },
  },
})
