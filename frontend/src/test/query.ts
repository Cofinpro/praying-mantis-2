import { QueryClient, VueQueryPlugin, type VueQueryPluginOptions } from '@tanstack/vue-query'

/** A fresh client per test so the cache doesn't leak between tests. */
export function testQueryClient(): QueryClient {
  // No retries, so an error shows up at once instead of after three attempts
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

/**
 * Vue Query plugin for `mount(..., { global: { plugins: [queryPlugin()] } })`. Pass your own client
 * when the test needs to look at the cache.
 */
export function queryPlugin(
  queryClient: QueryClient = testQueryClient(),
): [typeof VueQueryPlugin, VueQueryPluginOptions] {
  return [VueQueryPlugin, { queryClient }]
}
