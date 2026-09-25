/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'true' serves /api from the MSW handlers instead of the backend (`pnpm dev:mock`) */
  readonly VITE_API_MOCKS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
