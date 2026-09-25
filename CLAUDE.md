# Project guide for Claude

- Monorepo: `backend/` (Spring Boot 4, Java 21, Maven) and `frontend/` (Vue 3 + Vite + TypeScript). See README.md.
- Conventions live in `.claude/skills/` (springboot, vue, postgres). Follow them.
- No automated tests in this project: don't write or ask for tests, and skip the test sections of the skills. Verify changes manually.
- PR policy: see `CONTRIBUTING.md`. Conventional Commits, squash merge, and PRs under 400 lines.
- Reviewers: use the `be-specialist` subagent for `backend/**` changes and `fe-specialist` for `frontend/**`. If a change touches both, run both. They review and teach, because this is a learning project.
- To review locally before opening a PR, ask: "review my branch against main with the be-specialist / fe-specialist agent".
