# Design

The UI source of truth is the Figma file **Praying Mantis – Timesheets & Vacations** (D24):
https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ

It follows the Cofinpro brand (cofinpro.pt). This page is a summary of the file, so that people and the AI reviewers can check work without opening Figma. **If this page and Figma disagree, Figma wins.** Update this page in the same PR as the Figma change.

## Rules for FE stories

1. **Find your frame before you start.** Every `FE-x.y` story has a frame in the table below. Open it and build what it shows: layout, content, states and copy.
2. **No frame, no code.** If a story has no frame (marked *to design*), or needs a state the frame doesn't show, add it to Figma first, in the same style, and agree it with the team. Then add it to the table.
3. **Change the design first.** If the implementation needs to differ from the frame, for example because the API contract can't support it, update Figma (or agree the change in the story's `together` session) before merging. Don't let code and design drift apart silently.
4. **Use the tokens, not raw values.** Colours, font, radii and spacing come from the CSS variables in `frontend/src/assets/tokens.css` (below). No hex codes, font names or ad-hoc radii in components.
5. **Show it in the PR.** Every FE PR links its Figma frame and includes a screenshot of the implementation.

## Frames per story

Links open the frame directly (`?node-id=`).

| Frame | Stories | Link |
|---|---|---|
| 00 Foundations | all: tokens, buttons, badges | [2-2](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=2-2) |
| 01 Login | FE-1.1 | [2-89](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=2-89) |
| 02 Absences (header, nav, balance cards, calendar) | FE-1.2, FE-2.1, FE-2.2 | [2-132](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=2-132) |
| 03 Absences – Request dialog | FE-3.1 | [2-382](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=2-382) |
| 04 Approvals (tabs, table, reject comment) | FE-5.1, FE-7.1 | [3-2](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=3-2) |
| 05 Timesheets (week grid) | FE-6.1, FE-6.2 | [3-188](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=3-188) |
| 06 Timesheets – Export dialog | FE-8.1 | [3-430](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=3-430) |
| 07 Notifications dropdown | FE-4.1 | [3-746](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=3-746) |
| 08 Absences – Details | FE-3.2 | [17-2](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=17-2) |
| 09 Absences – Cancel confirmation | FE-3.2 | [17-299](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=17-299) |
| 10 Approvals – Team calendar | FE-5.3 | [17-563](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=17-563) |
| 11 Admin – Users | FE-9.1 | [18-12](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=18-12) |
| 12 Admin – Edit user (incl. 409 and team-lead cycle states) | FE-9.1 | [18-237](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=18-237) |
| 13 Admin – Entitlements | FE-9.2 | [18-547](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=18-547) |
| 14 Admin – Projects | FE-9.3 | [18-759](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=18-759) |
| 15 Admin – Public holidays | FE-9.4 | [18-970](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ?node-id=18-970) |

Integration stories (FE-1.3, FE-2.3, ...) don't change the UI. They must still match the frame once real data is in.

## Tokens

They live in `frontend/src/assets/tokens.css` (created in FE-1.1). Use them everywhere with `var(--…)`. Spacing is available as `--space-1` … `--space-10`, `--space-12` and `--space-16` (the number × 4px; add a step when a frame needs it) and the float shadow as `--shadow-float`.

### Colour

| Token | Value | Use |
|---|---|---|
| `--color-primary` | `#FD6202` | Primary buttons, active nav underline, links, Vacation |
| `--color-primary-soft` | `#FFF0E5` | Pending badge, selected option, info and warning boxes |
| `--color-ink` | `#131313` | Text, dark buttons, login brand panel |
| `--color-muted` | `#6E6E6E` | Secondary text, labels |
| `--color-bg` | `#F5F4F2` | App background |
| `--color-surface` | `#FFFFFF` | Cards, header, dialogs, inputs |
| `--color-surface-alt` | `#FAF9F7` | Table headers, weekends, unread notifications |
| `--color-line` | `#E7E5E1` | Borders, dividers |
| `--color-grey` | `#EFEEEB` | Segmented control, public holidays, Draft badge |
| `--color-success` / `--color-success-soft` / `--color-success-ink` | `#60D391` / `#E6F8EE` / `#1E7A4A` | Approved |
| `--color-training` / `--color-training-soft` | `#8242D8` / `#F1E9FC` | Training absences |
| `--color-info` / `--color-info-soft` | `#006CFF` / `#E5F0FF` | Sick absences, Submitted badge |
| `--color-danger` / `--color-danger-soft` | `#D93A3A` / `#FDECEC` | Errors, Rejected |
| `--color-overlay` | `rgb(19 19 19 / 50%)` | Behind dialogs |

Absence type colours: Vacation = primary, Training = training, Sick = info. **Pending** = the type's soft colour with a dashed primary border. Rejected and cancelled are hidden by default (FE-2.2).

Status badges: Draft = grey/muted · Submitted = info-soft/info · Pending = primary-soft/primary · Approved = success-soft/success-ink · Rejected = danger-soft/danger.

### Type

Font: **Inter**, self-hosted from the `@fontsource-variable/inter` package (`--font-sans: 'Inter Variable', 'Inter', system-ui, sans-serif`), so there is no request to Google Fonts.

| Role | Size / weight | Notes |
|---|---|---|
| Display | 56 / 700 | Login headline only, letter-spacing −2% |
| Page title | 32 / 700 | letter-spacing −1% |
| Dialog title | 24 / 700 | |
| Section title | 20 / 700 | Calendar month, week label |
| Card title | 15 / 600 | |
| Body | 14 / 400 | Default |
| Label | 13 / 500 | Form labels, muted |
| Caption | 12 / 500–600 | Badges, helper text |
| Table header | 11 / 600 | Uppercase, letter-spacing 6%, muted |

### Shape and spacing

- Radius: `--radius-pill: 999px` (buttons, badges, segmented control) · `--radius-card: 16px` (cards, popovers) · `--radius-dialog: 20px` · `--radius-input: 10px` · `--radius-chip: 6px` (calendar chips).
- Spacing is a 4px scale: 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 48, 64. Pages have 48px side padding and 36px top padding. Cards have 24px padding. Gaps between sections are 24–28px.
- Header: 72px high, white, 1px bottom border, active nav item has a 3px primary underline.
- Shadows only on floating things (dialogs, popovers, dropdowns): `0 24px 48px rgb(0 0 0 / 18%)`.

## Components in the file

Build these once as Vue components and reuse them. The names are a suggestion:

- `AppHeader`: logo, nav (Absences, Timesheets, Approvals if team lead, Admin if admin; Trainings and Seats as external links), bell, user, logout.
- `BaseButton`: variants `primary` · `secondary` (ink outline) · `dark` · `ghost` · `danger` (red outline) · `danger-fill` (destructive confirm), sizes default/small, optional leading icon. Disabled = 40% opacity.
- `StatusBadge`: the five statuses above.
- `BaseInput` (error state: 2px danger border + danger helper text), `BaseSelect`, `SegmentedControl` (full day / morning / afternoon), `BaseToggle`.
- `BaseDialog`: overlay, 20px radius, title plus close icon, actions right-aligned (secondary, then primary).
- `BalanceCard`, `AbsenceCalendar` (month grid, chips, half days), `TeamCalendar` (people × days, conflict days highlighted), `TimesheetGrid`, `NotificationBell`, `DataTable` (uppercase muted header on surface-alt, 1px row dividers).
- Admin pages share a layout: page title, a 220px sub-nav on the left (active item white with a 3px primary bar), and the section on the right.

Icons are [Lucide](https://lucide.dev) at stroke width 2 (bell, chevrons, plus, x, check, calendar, download, alert-triangle, info, external-link, log-out, trash, pencil, search, copy, key).
