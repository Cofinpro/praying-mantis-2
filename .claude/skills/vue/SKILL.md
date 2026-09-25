---
name: vue
description: Conventions for the Vue 3 + Vite + TypeScript frontend in frontend/ (Composition API, Vue Router, Pinia, Vitest). Use when creating or changing components, views, routes, stores, API clients or frontend tests.
---

# Vue frontend (frontend/)

Stack: Vue 3.5, Vite 8, TypeScript 5.9 (strict, `vue-tsc`), Vue Router 5, Pinia 4, Vitest 5 + `@vue/test-utils` + jsdom. The `@` alias maps to `frontend/src`.

## Layout

```
src/
├── api/          Typed fetch wrappers, one file per backend resource (e.g. hello.ts)
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
- Network calls live in `src/api/`, never inline `fetch` in components. Pattern (see `src/api/hello.ts`):
  - Export a TS interface mirroring the backend record/DTO.
  - Use relative `/api/...` URLs — Vite proxies `/api` to `http://localhost:8080` in dev, so no CORS or base URL config.
  - Throw an `Error` on `!response.ok`; the caller shows loading / error / data states.
- Shared or cross-view state goes in a Pinia setup store (`defineStore('name', () => { ... })`); local UI state stays in the component.
- Lazy-load non-home routes: `component: () => import('../views/XxxView.vue')`.

## Design (D24)

The Figma file is the UI source of truth; `design.md` summarises it. For any view or component:

- **Start from the frame.** Look up the story in `design.md` → "Frames per story" and build its layout, states (loading, empty, error, disabled, read-only) and copy. If the Figma MCP is connected, read the frame with it (the `figma-design-to-code` skill) instead of guessing from the summary.
- **No frame for what you need?** Stop and design it in Figma first (or ask the team). Don't invent UI in code.
- **Tokens only.** Use the CSS variables from `src/assets/tokens.css` (`var(--color-primary)`, `var(--radius-card)`, ...) inside `<style scoped>`. No hex values, font names or ad-hoc radii in components. A missing token gets added to `tokens.css` and `design.md` together.
- **Reuse the shared components** listed in `design.md` (`BaseButton`, `StatusBadge`, `BaseDialog`, ...) rather than restyling a new one. Icons are Lucide.
- If the implementation must differ from the frame, update Figma and `design.md` in the same PR.

## Tests

- Colocate as `components/__tests__/Name.spec.ts` (or `views/__tests__/`).
- Mount with `mount` from `@vue/test-utils`; mock API modules with `vi.mock('@/api/xxx', ...)` rather than stubbing global `fetch`.
- Await async rendering with `flushPromises()` before asserting.
- Components that use a store need `global: { plugins: [createPinia()] }` (or `createTestingPinia` if added).

## Commands (run in frontend/)

```bash
npm run dev          # http://localhost:5173
npm run test:unit -- --run   # single run (plain `test:unit` watches)
npm run type-check
npm run build        # type-check + production build
```

Before declaring frontend work done, run `npm run test:unit -- --run` and `npm run type-check`.
