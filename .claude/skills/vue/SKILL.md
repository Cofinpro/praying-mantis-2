---
name: vue
description: Conventions for the Vue 3 + Vite + TypeScript frontend in frontend/ (Composition API, Vue Router, TanStack Query, Pinia, Vitest). Use when creating or changing components, views, routes, stores, API clients or frontend tests.
---

# Vue frontend (frontend/)

Stack: Vue 3.5, Vite 8, TypeScript 5.9 (strict, `vue-tsc`), Vue Router 5, TanStack Query 5 (`@tanstack/vue-query`), Pinia 4, Vitest 5 + `@vue/test-utils` + jsdom, ESLint 10 (flat config) + Prettier. The `@` alias maps to `frontend/src`.

## Layout

```
src/
├── api/          client.ts (the only HTTP code) and the types generated from openapi.yaml (FE-0.2)
├── assets/       Global CSS
├── components/   Reusable components; tests in components/__tests__/*.spec.ts
├── router/       index.ts – route table
├── stores/       Pinia stores (create when first needed), one per domain: useXxxStore
├── views/        Route-level components, named *View.vue
├── App.vue
└── main.ts
```

## Conventions

- Always `<script setup lang="ts">`, Composition API only. Order blocks: script, template, style (`scoped` when adding styles).
- Type props/emits with generics: `defineProps<{ id: number }>()`, `defineEmits<{ saved: [item: Item] }>()`. Use `withDefaults` or destructured defaults for optional props.
- State: `ref` for primitives/replaced values, `computed` for derived state. No Options API, no `this`.
- Components are PascalCase multi-word filenames (`BackendGreeting.vue`); views end in `View`.
- All HTTP goes through `src/api/client.ts` (decision #6), never inline `fetch` in components. It handles 401 and Problem Details errors.
  - Use relative `/api/...` URLs — Vite proxies `/api` to `http://localhost:8080` in dev, so no CORS or base URL config.
- API types come from `pnpm gen:api` (openapi-typescript, decision #3). Never hand-write DTO types, and never edit the generated file.
- Server data goes through TanStack Query (`useQuery` / `useMutation`), never into a Pinia store (decision #6). The `QueryClient` is created in `main.ts`.
- Client-only state shared across views goes in a Pinia setup store (`defineStore('name', () => { ... })`); local UI state stays in the component.
- Lazy-load non-home routes: `component: () => import('../views/XxxView.vue')`.

## Tests

- Colocate as `components/__tests__/Name.spec.ts` (or `views/__tests__/`).
- Mount with `mount` from `@vue/test-utils`. Mock the network with the shared MSW handlers (decision #4), not `vi.mock` of API modules or a stubbed global `fetch`.
- Components that use `useQuery` need `global: { plugins: [[VueQueryPlugin, { queryClient }]] }` with a fresh `QueryClient` per test (`retry: false`, so failures show up at once).
- Await async rendering with `flushPromises()` before asserting.
- Components that use a store need `global: { plugins: [createPinia()] }` (or `createTestingPinia` if added).

## Commands (run in frontend/)

```bash
pnpm dev             # http://localhost:5173
pnpm test            # single run (`pnpm test:watch` watches)
pnpm lint            # ESLint; `pnpm lint:fix` auto-fixes
pnpm format          # Prettier; `pnpm format:check` only reports
pnpm type-check
pnpm build           # type-check + production build
```

Before declaring frontend work done, run `pnpm lint`, `pnpm test` and `pnpm type-check`.
