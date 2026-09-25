import { QueryClient, VueQueryPlugin, type VueQueryPluginOptions } from '@tanstack/vue-query'

/** Vue Query plugin for `mount(..., { global: { plugins: [queryPlugin()] } })`. */
export function queryPlugin(): [typeof VueQueryPlugin, VueQueryPluginOptions] {
  // A fresh client per test so the cache doesn't leak between tests. No retries, so an error
  // shows up at once instead of after three attempts.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return [VueQueryPlugin, { queryClient }]
}
