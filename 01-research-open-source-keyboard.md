# Research: Open-Source Keyboard to Fork

Goal: a base IME (Input Method Editor) that is **actively maintained, 100% offline (no INTERNET permission), AOSP-based, and permissively forkable**, so the credential-suggestion layer can be added on top without inheriting spyware risk or a dead codebase.

## Candidates

| Project | Base | Language | License | Status (mid-2026) | Verdict |
|---|---|---|---|---|---|
| **HeliBoard** | Fork of OpenBoard (AOSP keyboard) | Java/Kotlin | GPL-3.0 | Actively maintained, the de-facto successor to OpenBoard | **Recommended base** |
| OpenBoard | AOSP keyboard | Java | Apache-2.0 (app is GPL-3.0) | Discontinued Dec 2022 — superseded by HeliBoard | Skip; use HeliBoard instead |
| FlorisBoard | Ground-up rewrite | Kotlin | Apache-2.0 | Still beta; word suggestion/spell-check historically incomplete | Good code quality, but less mature as a fork base |
| AnySoftKeyboard | Independent | Java | Apache-2.0 | Mature, very configurable, older UI patterns | Viable alternative if GPL-3.0 is a blocker |

## Why HeliBoard

- **No INTERNET permission at all** — the entire codebase is built around being unable to phone home, which is exactly the trust story this app needs.
- It's a maintained continuation of the AOSP keyboard lineage (OpenBoard → HeliBoard), so the input pipeline (`LatinIME`, `KeyboardView`, `Suggestions strip`) is battle-tested and well documented from years of AOSP/OpenBoard history.
- Already has the exact UI real estate needed: a **suggestion strip above the keys** — this is where credential chips get injected, no new UI surface has to be invented.
- Clean separation between the input-method service, the keyboard view, and the suggestion/dictionary logic — the credential layer can sit alongside the existing dictionary suggester as a second suggestion source.

## License consequence (important, non-negotiable)

HeliBoard is **GPL-3.0**. If the keyboard module (anything built on HeliBoard's code) is distributed, the combined keyboard app's source must be made available under GPL-3.0 too. Practical structuring:

- Keep the **vault engine** (encryption, credential storage, matching logic, Autofill service) in its own module with no HeliBoard code in it.
- The **keyboard module** (the fork + the suggestion-injection glue code) is the GPL-3.0 boundary.
- This is exactly the module split in the architecture doc — it's a licensing decision as much as a code-cleanliness one.

## Fallback option

If GPL-3.0 copyleft is unacceptable for the vault engine, **AnySoftKeyboard (Apache-2.0)** is the fallback fork base — more permissive, also offline-first and mature, at the cost of an older-feeling settings UI that would need a visual refresh to match the target design.

## Sources consulted
GitHub (Helium314/HeliBoard, florisboard/florisboard), F-Droid listings for HeliBoard and FlorisBoard, AlternativeTo comparison pages, and independent 2025–2026 reviews (How-To Geek, MakeUseOf) comparing open-source Gboard alternatives.
