import { globalIgnores } from 'eslint/config'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import pluginVitest from '@vitest/eslint-plugin'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

// Flat config: an array of config objects, applied in order. Later entries win.
export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },

  // Generated files: the API types (pnpm gen:api) and the MSW service worker
  globalIgnores([
    '**/dist/**',
    '**/coverage/**',
    'src/api/generated/**',
    'public/mockServiceWorker.js',
  ]),

  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,

  {
    ...pluginVitest.configs.recommended,
    files: ['src/**/__tests__/*', 'src/test/**'],
  },

  // Prettier owns formatting, so turn off every ESLint rule that would fight it
  skipFormatting,
)
