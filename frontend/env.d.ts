/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'true' serves /api from the MSW handlers instead of the backend (`pnpm dev:mock`) */
  readonly VITE_API_MOCKS?: string
  /** External "Trainings" app, owned by another group. Opens in a new tab from the header. */
  readonly VITE_TRAININGS_URL?: string
  /** External "Seats" app, owned by another group. Opens in a new tab from the header. */
  readonly VITE_SEATS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
