# UX / UI Design

This is the written companion to `03-ui-ux-mockup.html` (the visual mockup) —
covering the screen inventory, component list, states, accessibility, and a
mobile-optimization review. **Updated for the Flutter migration**: the UI
described below is now `lib/` (Dart), not the archived Compose version in
`legacy_compose_ui/app-compose-reference/` — screen behavior is equivalent,
but the file references below point at the current Dart implementation.

## Screen inventory

| # | Screen | File | Matches mockup |
|---|---|---|---|
| 1 | Unlock / create vault | `lib/screens/unlock_screen.dart` | SCR.01 |
| 2 | Vault list + search + detail dialog | `lib/screens/vault_list_screen.dart` | SCR.02 |
| 3 | Suggestion popup (in-keyboard, not a screen) | `lib/keyboard/keyboard_app.dart` (Flutter-rendered as of Phase 6 — was native Kotlin) | SCR.03 — matched directly against the reference screenshot (dark suggestion bar, blue pill chip with leading dot, white/gray key styling) rather than approximated |
| 4 | Add login | `lib/screens/add_edit_screen.dart` | SCR.04 — same divergence as before: two explicit optional match fields (website domain, app package) rather than guessing from one field. |
| 5 | Settings | `lib/screens/settings_screen.dart` | SCR.05 |

`VaultListScreen` now includes a working **view-credential dialog**
(tap a row → username + masked/revealable password + notes) — this closes
the "tapping a row does nothing" gap noted in the pre-Flutter version of
this document. There's still no separate **edit** flow (only add + view).

## Keyboard: two restyle passes this project went through

First pass (native Kotlin, `SimpleVaultIME.kt`, kept unregistered for
reference): rounded keys, real pressed-state colors, 48dp height, haptics —
a Gboard-*inspired* look, in response to "looked worst, not like Google
keyboard."

Second pass (this session, Flutter-rendered, `lib/keyboard/keyboard_app.dart`,
the active version): per a follow-up request to match a specific reference
screenshot exactly, the keyboard was rebuilt pixel-for-pixel against that
image — a near-black suggestion bar, a blue pill-shaped chip with a small
leading white dot and bold white label, white letter keys, gray special keys
(shift/backspace/123/go), the same staggered middle row and 48dp key height.

Still a proof-of-concept for the suggestion mechanism, not production typing
(no autocorrect/gestures/other languages) — see `PHASES.md`'s Phase 6 notes
on what "production typing" even means now that the keyboard is
Flutter-rendered, since the original `keyboard/FORK_NOTES.md` plan assumed a
fully-native keyboard shell.

## Navigation flow

```mermaid
flowchart LR
    A[Unlock / Create vault] -->|unlock success| B[Vault list]
    B -->|tap +| C[Add login]
    C -->|save / cancel| B
    B -->|tap settings icon| D[Settings]
    D -->|back| B
```

## Component inventory

Reused across screens, all Material 3 out of the box (no custom component
library was built — deliberate, given the small screen count):

- `OutlinedTextField` — every text input (master password, label, domain,
  package, username, password, notes)
- `Button` (filled) — primary actions: Unlock/Create vault, Save to Vault
- `TextButton` — secondary action: "Use Face/Fingerprint instead"
- `ListItem` — vault rows, all three Settings rows
- `Switch` — the biometric-unlock toggle
- `FloatingActionButton` — add-credential entry point
- `TopAppBar` — every screen's header

Two components exist **only** inside the keyboard's own Flutter engine
(`lib/keyboard/keyboard_app.dart`), a separate rendering world from the
vault app's Flutter engine even though both are Dart:

- The QWERTY key grid (`_KeyButton` + `_keyRow`)
- The credential suggestion chip (`_SuggestionPill`) — matches the blue
  accent color and pill shape from the reference screenshot directly

## Design tokens

Centralized in `app/src/main/java/com/vaultkey/app/ui/theme/Theme.kt` so the
built app and `03-ui-ux-mockup.html` can't silently drift apart:

| Token | Value | Used for |
|---|---|---|
| Ink | `#14151A` | Headers, dark surfaces, keyboard background accents |
| Paper | `#ECEAE6` | Light background |
| Paper2 | `#E2E0DB` | Secondary light surface |
| Accent Blue | `#2F4EEA` | Primary actions, suggestion chips, focused-field outline |
| Muted | `#87868C` | Secondary text |
| Line | `#D3D1CB` | Hairline dividers |

**Gap:** the mockup's display typeface (a geometric sans in the "CVAT
Digest"-style headline) is not yet bundled as an actual font resource — 
`Type.kt` uses the platform default font with matching weights/sizes, not
the real typeface. Add `Space Grotesk`/`Inter` as `res/font/` files and
reference them in `VaultKeyTypography` to close this gap.

## States

| State | Where handled | How |
|---|---|---|
| First run (no vault yet) | `UnlockScreen` | `VaultState.Uninitialized` branch — shows password + confirm-password fields |
| Wrong master password | `UnlockScreen` | Inline error text below the password field |
| Empty vault (no credentials saved) | `VaultListScreen` | "No saved logins yet. Tap + to add your first one." |
| No search results | `VaultListScreen` | "No matches for "{query}"" |
| Missing required fields on Add login | `AddEditCredentialScreen` | Inline error text above the Save button |
| Biometric hardware/enrollment unavailable | `UnlockScreen`, `SettingsScreen` | Checked via `BiometricPromptHelper.isAvailable()` before ever showing a biometric prompt — added this session after finding it was missing (see `PHASES.md`) |

**Gap:** there's no loading/spinner state anywhere — every repository call
is a quick local SQLCipher read, so this is a reasonable simplification for
now, but a very large vault (hundreds of entries) would benefit from a
loading indicator on `VaultListScreen`'s initial `LaunchedEffect` fetch.

## Accessibility

- All icon-only controls (`Icons.settings_outlined`, `Icons.add`,
  `Icons.visibility`) rely on Flutter's default semantics; Flutter's
  `IconButton` accepts a `tooltip` parameter that also feeds screen-reader
  labels, and none of the icon buttons in `lib/screens/` currently set one.
  **Should be explicitly audited** before shipping — flagging as a follow-up
  since the right copy is a product decision, not fixing silently.
- Password fields use `obscureText: true` — correct for privacy. Unlike the
  pre-Flutter version, `VaultListScreen`'s detail dialog now DOES have a
  reveal/hide toggle (the `Icons.visibility`/`visibility_off` button) — this
  closes the "no show-password toggle" gap noted previously, though only for
  viewing a saved credential, not for the add-login form's password field.
- Touch targets: the active keyboard's Dart `_KeyButton` widgets are `48dp`
  tall — at Android's recommended minimum, not just under it as in an
  earlier draft.

## Mobile-optimization review

What's already handled well:
- `FLAG_SECURE` was set on the old Compose `MainActivity` but was missed in
  the initial pass of this Flutter migration — caught during this same
  review and **re-added** to the new `MainActivity.onCreate()`. Worth
  mentioning here anyway since it's exactly the kind of thing worth
  double-checking after any activity-class rewrite.
- `ListView.builder` in `VaultListScreen` — correct choice for a potentially
  long list, avoids inflating every row up front (same reasoning as the old
  `LazyColumn`).

What's not yet addressed:
- **Keyboard first-show latency/memory** — the keyboard is now
  Flutter-rendered (`FlutterView` hosted inside `FlutterVaultIME`), which
  reverses the earlier native-`View`s choice on direct request. This is
  **not** a performance win: a second Flutter engine running inside the
  keyboard process is heavier and slower to first-show than lightweight
  native `View`s. Explicitly accepted as a tradeoff (see `PHASES.md`'s
  Phase 6), not an oversight — but genuinely "not yet addressed" in the
  sense that no actual latency measurement has been taken, since nothing
  in this project has run on a device yet.
- No landscape-orientation testing/consideration — the keyboard's key grid
  in particular would need different sizing in landscape (this is normal
  even for production keyboards, but worth flagging as untested here).
- No tablet/large-screen layout — `VaultListScreen`'s single-column list
  would look sparse on a tablet; not a priority for a phone-first password
  manager, but worth a conscious decision rather than a default.
- Dark mode: `theme.dart` only defines a light `ColorScheme` — the old
  Compose version at least had a `VaultKeyDarkColors` stub; that didn't
  carry over. Real gap, not a stylistic one, since Flutter defaults to
  following system dark mode unless a theme is explicit about it, so right
  (never adapts), so the in-app dark theme is really its own design pass
  that hasn't happened yet.

## Honest summary

Nothing in this document was verified by actually running the app — see the
compile-time caveat repeated throughout this project. This is a structural
and logical review (reading the Compose code against the mockup and against
Android UX conventions), not a QA pass on a running build. Treat the "Gap"
callouts above as the first punch list once the app actually runs on a
device or emulator.
