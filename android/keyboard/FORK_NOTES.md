# Production-quality typing — status after the Flutter migration (Phase 6)

This file originally planned "fork HeliBoard, swap it in for the native
keyboard shell." That plan assumed the keyboard's typing surface was
native Kotlin. As of Phase 6, the active keyboard (`FlutterVaultIME` +
`lib/keyboard/keyboard_app.dart`) renders everything — including the keys
themselves — in Flutter/Dart. HeliBoard is a native Android/Kotlin codebase
with no Dart/Flutter port, so "fork it and drop it in" no longer applies
the way it used to.

## What "production typing" means now, and the options

The current keyboard has no autocorrect, no dictionaries, no gesture typing,
no non-English layouts — it's a functional QWERTY grid with a suggestion
strip, nothing more. Closing that gap now has two real paths, not one:

**Option A — build typing features in Dart.** Autocorrect and dictionary
suggestions are genuinely implementable in Dart (they're fundamentally
string-matching/scoring algorithms, not OS-level features) — but it's a
large, from-scratch undertaking with no existing open-source Dart keyboard
engine to fork the way HeliBoard let the native version fork one. Gesture
typing (swipe-to-type) is a harder, more specialized algorithm and would be
the most work to build from scratch.

**Option B — revert the typing surface to native, keep Flutter for the
suggestion strip only.** `SimpleVaultIME.kt` (kept, unregistered, in this
module) plus a real HeliBoard fork could still handle the actual QWERTY
grid/typing, while the credential-suggestion chip specifically renders via
a small embedded `FlutterView` just for that one strip. This gets HeliBoard's
production typing quality back while keeping *some* Flutter in the keyboard,
as a middle ground between the two "everything" extremes already tried.

**Neither of these is started.** This file is intentionally a decision
record, not a build — pick a direction before investing further here, given
how much the answer changes what gets built next.

## What every version keeps unchanged
`CredentialSuggestionInjector.kt` and `CredentialChip` never depend on which
of the above gets chosen — they only need whatever's currently the input
surface to call `onFieldFocused()`/`clearSuggestions()` and render whatever
chip list comes back. That boundary held through both the native-only and
Flutter-rendered versions already, and should hold through whichever of
Option A/B comes next too.
