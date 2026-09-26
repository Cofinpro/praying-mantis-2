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

### Not in the frames yet

States and choices the code has that Figma doesn't show yet. Add them to Figma when someone next edits the frame.

- **04 Approvals (FE-5.1):**
  - The reject comment opens in a narrow `BaseDialog` (460px, no title row, 16px bold heading) instead of a popover anchored to the row: the native modal gives focus trap, Esc and an inert page without positioning code. Same copy and buttons (ghost "Cancel", dark "Reject request").
  - The person cell shows avatar and name only. "Level · Client" needs the requester's level and client, which `TeamAbsenceRequest.requester` (a `UserRef`) doesn't have yet (asked on #39).
  - Tabs are real ARIA tabs (arrow keys, Home, End) and the selected one is in the URL: `?tab=timesheets` (the TIMESHEET_SUBMITTED notification links there). The inactive tab's count is grey on muted, the active one white on primary, as in the frame.
  - Balance: "–" for types without a balance (only VACATION has one); the "after" number turns danger red when the request no longer fits (approving it answers a 409).
  - States: loading ("Loading requests…"), empty ("Nothing to approve right now.", also what non-team-leads see), load error with "Try again", and a danger banner above the table when an approval fails. Reject errors show inside the dialog (banner, or under the field for a 400 on `comment`).
  - Avatars cycle through primary, training, info and success-ink by user id.

- **04 Approvals, Timesheets tab (FE-7.1):** the frame only shows the Absences tab, so this tab reuses its table, states and reject dialog:
  - Columns: a 32px round chevron button (`aria-expanded`, turns 180° when open), person (avatar and name), week ("Week 38 · 14–20 Sept"), total ("40 h", semibold), projects ("DKB-CORE 32 · TRAINING 8", muted, cut with an ellipsis), then small Reject (secondary) and Approve (primary), oldest week first.
  - The chevron opens a row under it, on surface-alt, with the read-only week grid of "05 Timesheets" (`TimesheetGrid` with `read-only`: hours as text, absence and holiday chips, descriptions as a list). Several rows can be open.
  - The reject dialog says "Reject Diogo’s week 38?" and "Reject week"; the comment is required.
  - States: "Loading timesheets…", empty "No timesheets to approve right now.", load error with "Try again", the danger banner "Couldn’t approve Diogo Pereira’s week 38. …" above the table. Footnote: "The approver is the person’s team lead. A rejected week goes back to them to correct."

- **10 Approvals – Team calendar (FE-5.3):**
  - A third ARIA tab, "Team calendar" (no count), in the URL as `?tab=team-calendar`. The month starts on the current one; ‹ › and "Today" move it (not in the URL).
  - Pending blocks are dashed in the **type's** colour on its soft shade (the frame only shows vacation). A person with two half days on one date gets two half blocks.
  - The footnote drops "click a name to open their requests": there's no page for someone else's requests yet. It reads "Pending requests are dashed. Hover a day to see who is away." Hovering a day header or its Away count shows "Away: Ana Silva, Carla Mendes" (a `title`); hovering a block shows its description ("Carla Mendes, vacation, pending, 16–18 Nov").
  - The Away row counts people, a half day included; weekends and public holidays get no blocks and no count.
  - States inside the card, under the toolbar (so the month stays navigable): "Loading the team calendar…", a load error with "Try again", and for someone who leads nobody (only their own row, decision 36) a muted note "Nobody has you as their team lead, so the calendar only shows your own absences." above the grid.
  - Under ~1360px the grid scrolls sideways inside the card, the member column stays sticky.

- **11 Admin – Users and 12 Admin – Edit user (FE-9.1):**
  - The admin layout is built once for FE-9.1 to 9.4 (`AdminView`, nested routes): `/admin` opens `/admin/users`; Entitlements, Projects and Public holidays show a placeholder section until their stories. Under 800px the sub-nav wraps above the section.
  - The **Admin** column shows the flag only (a read-only `BaseToggle`, "Yes"/"No" for screen readers). It's changed in the edit dialog, where the last-admin 409 can be explained next to the switch.
  - Search filters on the client (the contract returns everyone); no match says "No user matches “x”.". The subtitle counts everyone ("9 people · …").
  - **Add user** uses the edit dialog titled "Add user", with an extra "Initial password (8 to 72 characters)" field under Email, no "Set new password", and a primary "Add user". New users start as DKB · Junior, no team lead.
  - **Team lead** select: "No team lead (an admin approves)" first, then everyone but the user themself, by name.
  - **Errors** under their field: "This email is already used by Ana Silva." (409), "Ana Silva already leads Bruno Costa, directly or through others. Pick another team lead." (409 cycle), "This is the only admin. Make someone else an admin first." under the Admin card, whose border turns danger (409 last admin), and 400 messages from the backend under the field they name. Anything else is a danger banner above the footer.
  - **Set new password** opens a narrow (460px) dialog on top: "Set new password", "For Inês Rocha. They can log in with it straight away; sessions already open stay valid until they expire.", a password field (8 to 72 characters), secondary "Cancel", primary "Set password". After it closes, the edit dialog shows a success-soft note "New password set for Inês Rocha.".
  - **States**: "Loading users…", load error with "Try again", and for a non-admin (403) a card with a lock icon, "You need an admin account" and "Only admins can manage users. Ask an admin if you need access." (no search or Add user).

- **05 Timesheets (FE-6.1):**
  - **Descriptions per entry** (the contract's `TimeEntry.description`): a message icon button in each row (between the project and Monday, in its own cell) opens a `BaseDialog` (520px) "Descriptions · DKB-CORE" with one text field per day that has hours ("Monday 19 Oct · 6 h", 500 characters max). Secondary "Cancel", primary "Apply"; Apply updates the grid and the week's Save sends it. The icon turns primary with a count when the row has descriptions. Read-only weeks show them as a list with "Close".
  - **Remove a row**: a trash icon button next to the descriptions button (editable weeks only).
  - **"+ Add project"** turns into a pill select of the active projects not on the grid yet, with "Add" (primary, small) and "Cancel" (ghost). The new row's Monday gets the focus.
  - **Header chips**: approved absences in the type's soft colour (a half day adds "½"), public holidays in grey with the holiday's name (cut with an ellipsis at 88px, full name on hover and for screen readers). Weekends get no absence chip.
  - **Errors**: an invalid cell gets the 2px danger border; a day over 24 h turns its daily total danger red. All messages are listed in a danger-soft strip under the totals ("DKB-CORE, Thursday 22 Oct: Use quarter hours, e.g. 7.25"), each linked to its input with `aria-describedby`. Save is disabled while any is shown. Server errors (400 per entry, 409) use the same list plus a danger banner above the grid.
  - **Status banners** above the grid: rejected = danger-soft with "Rejected by X on date", the approver's comment in quotes and "Correct the hours, save, and submit the week again."; submitted and approved = info-soft one-liners ending in "The week is read-only.". Read-only weeks show the hours as plain text, with no inputs, add row, remove buttons or Save.
  - **Unsaved changes guard**: leaving the week or the page with unsaved changes opens a 460px confirmation ("Discard unsaved changes?", primary-soft warning icon, secondary "Keep editing", danger-fill "Discard changes"). Closing the tab uses the browser's own prompt. After a save the toolbar shows a muted "Saved" where "● Unsaved changes" was.
  - States: "Loading the week…", and a load error with "Try again". An empty week says "No hours yet this week." (read-only: "No hours were recorded this week.").
  - **Submit confirmation (FE-6.2)**: "Submit week" opens a 460px confirmation like "09 Absences – Cancel confirmation": a primary-soft send icon, "Submit this week?", "Week 43 · 19–25 Oct 2026, 38 hours. Your team lead (or an admin, if you have none) is notified to approve it. Until then, the week is read-only.", then secondary "Cancel" and primary "Submit week". A 409 shows a danger banner inside it (e.g. "Nobody can approve this week yet. Please ask an admin."). "Submit week" is disabled (40%) while there are unsaved changes, and it's linked to "Save before submitting" with `aria-describedby`.
  - The footer note adds "in quarter hours". The **Export month** button (secondary, download icon) sits right of the page title and opens frame "06" (FE-8.1).

- **06 Timesheets – Export dialog (FE-8.1):**
  - **Month** is a `BaseSelect` (field variant) with the calendar icon instead of the chevron, offering next month down to a year ago, newest first; it starts on the current month. A native `<input type="month">` has no picker in Safari or Firefox desktop.
  - **Template cards** show the client's name (or "Generic") as the title and the template's `name` from `GET /export-templates` as the description; the contract has no separate description. The generic card comes first, then the server's order (by name), in a 2-column grid (1 column under 520px). They are native radios in a `<fieldset>` with the legend "Template": one tab stop, arrow keys move the choice. The frame's 12px card radius isn't a token, so cards and the warning use `--radius-input` (10px).
  - **Warning** (primary-soft, as in the frame) comes from `GET /me/timesheet-months/{month}`: it lists only the weeks that aren't `APPROVED` **and** have hours in the month ("Week 38 in September is not approved yet. It will be included as it is." / "2 weeks in October are not approved yet (weeks 41 and 43). They will be included as they are."). A week without hours adds nothing to the file, so it isn't named. A month with no hours at all says "You have no hours in October yet, so the file will have no entries." If the month can't be loaded: "Couldn’t check which weeks of this month are approved."
  - **States**: "Loading templates…", a load error with "Try again" (Download disabled), "Preparing…" on the disabled Download button while the file is on its way, and a danger banner above the footer for errors ("This template isn’t available any more. Pick another one." for a 400 on `template` or a 404, "This month can’t be exported. Pick another one." for a 400 on `month`, else "Something went wrong. Please try again."). The dialog closes once the download has started.

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

Absence type colours: Vacation = primary, Training = training, Sick = info, Parental leave = success, Unpaid leave = muted (on grey). The last two aren't in the Figma legend yet; add them there when a frame shows them. **Pending** = the type's soft colour with a dashed primary border. Rejected and cancelled are hidden by default (FE-2.2).

Notification type colours (FE-4.1, frame "07 Notifications dropdown"): each item has a 32px round icon in the soft colour with a 10px dot in the full colour. Absence requested = primary · Timesheet submitted = info · Absence or timesheet approved = success · Absence or timesheet rejected = danger · Absence cancelled = muted on grey. Unread items have the surface-alt background, semibold ink text and an 8px primary dot on the right. Read items are white with regular muted text.

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
- `BaseInput` (error state: 2px danger border + danger helper text), `BaseSelect`, `SegmentedControl` (full day / morning / afternoon), `BaseToggle` (38×22 switch, line track with a white 16px knob when off, primary track when on; a `<button role="switch">`, Space and Enter flip it; `readonly` shows the state only).
- `BaseDialog`: overlay, 20px radius, title plus close icon, actions right-aligned (secondary, then primary). Widths 580px (forms), 520px (details) and 460px (confirmations, which have no title row: an icon, a heading and the actions).
- `BalanceCard`, `AbsenceCalendar` (month grid, chips, half days), `TeamCalendar` (people × days, conflict days highlighted), `TimesheetGrid`, `NotificationBell` (a disclosure: the 40px bell with an 18px primary badge, capped at "9+", opens a 420px panel below it, right-aligned; the frame's "See all notifications" footer is left out until a story adds that page), `DataTable` (uppercase muted header on surface-alt, 1px row dividers).
- Admin pages share a layout (`AdminView`, FE-9.1): page title "Admin" and "Users, entitlements, projects and public holidays", a 220px sub-nav on the left (items 14/500 muted; active item white with a line border, 14/600 ink and a 3px × 20px primary bar on its left edge), and the section on the right with its own 20/700 title, 13px muted subtitle and actions.

Icons are [Lucide](https://lucide.dev) at stroke width 2 (bell, chevrons, plus, x, check, calendar, download, alert-triangle, info, external-link, log-out, trash, pencil, search, copy, key).
