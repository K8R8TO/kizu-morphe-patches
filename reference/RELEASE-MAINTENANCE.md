# Kizu Twitch Patches — Release & Maintenance Record

## Current known-good state

- Stable: `1.9.2`
- Current prerelease: `1.9.3-beta.22`
- Target Twitch: `31.3.1` / build `3103016`
- Package: `tv.twitch.android.app`
- Current Morphe Manager/Patcher baseline: `1.34.0 / 1.15.1`
- Prerelease feed branch: `dev`
- Stable feed branch: `main`

## 1.9.3 player-controls history

The 1.9.3 series is prerelease-only until explicitly promoted.

### beta.1 — Create Clip
- Goal: hide player Create Clip.
- beta.1.10 is the known-good Create Clip runtime baseline.
- MPP SHA256: `25a291e75a8b2419e1e0e42479ce562dc3e2204fb7e4bac1960ba7ac079db7ed`
- User runtime-confirmed working.

### beta.2
- Explicitly skipped.

### beta.3 — Cast + Live Share
- Goal: hide Cast and Live Share together.
- Both settings live under Player Controls.
- Defaults are ON (hidden).
- Turning a toggle OFF restores that individual control.

### beta.3.2
- Build/release succeeded.
- User runtime result: Share + Cast remained visible.
- Lesson: `Lout.k` / `Lout.q` constructor bindings are not the final visible controls.

### beta.3.3 / beta.3.4
- Semantic visibility hooks were attempted.
- User reported stream-opening crashes.
- Lesson: do not use unsafe return/state hooks merely to force final visibility.

### beta.3.5
- Release/metadata pipeline problems prevented reliable Morphe discovery.
- Lesson: prerelease metadata must remain structurally consistent with the `dev` feed.

### beta.3.6
- Unsafe semantic hooks removed.
- Build and metadata verification passed.
- User later encountered parser/patching problems in the following iteration.

### beta.3.7
- Generated-smali parser/lexer failure.
- Root causes included brittle register/range generation and incorrect Kotlin interpolation.
- Lesson: generated smali must be constructed from actual register values and valid syntax.

### beta.3.8
- Corrected release naming was intentionally `3.8` rather than reusing `3.7`.
- Stable register-generation work was incorporated, but it was not the final behavioral solution.

### beta.3.9 — current known-good
Release:
- Tag: `v1.9.3-beta.3.9`
- GitHub release ID: `407272735`
- Published: `2026-10-08T21:19:58Z`
- Asset: `patches-1.9.3-beta.3.9.mpp`
- Asset ID: `623039725`
- Asset size: `1,036,641` bytes
- MPP SHA256: `e51602ca37fbf0114ffc47be1267691cbcdfc986065a45f973a209068cd5052d`
- Workflow run: `37845793857`
- Job: `113546214819`

Verification:
- Metadata validation passed.
- Build passed.
- Post-build metadata validation passed.
- Artifact verification passed.
- Tag pinning passed.
- GitHub prerelease publication passed.
- Release asset verification passed.
- User installed and runtime-tested the release successfully.

Final implementation:
- Exact visible Twitch player Share control: `Lqot.r`
- Exact visible Twitch player Cast control: `Lqot.e`
- State controller: `Llrx.v(ViewDelegateState)`
- Kizu binds each exact visible view immediately after every verified field load in that state method.
- Create Clip exact binding remains in place.
- Visibility is enforced by `HiddenView.attach(...)`.

### beta.16 — high-register Smali invoke attempt
- Build and metadata checks passed, but the user reported that grey/strikethrough deleted-message styles regressed and the `Invalid register: v16` warning remained.
- The one-argument deleted-message recovery bridge was reverted in beta.17; the original two-argument recovery path is restored.

### beta.17 — actual high-register field access fixed; deleted-message recovery regressed
- The `Lout.<init>` Create Clip hook now moves `p0` (which aliases `v16` in the 23-register constructor) through `move-object/from16 v0, p0` before reading `Lout.j`. The user confirmed the `Invalid register: v16` warning disappeared.
- The formatter recovery still continued into Twitch's original placeholder logic after replacing the message, and the user reported deleted-message display/styles were broken. This is superseded by beta.18.

### beta.18 — restore early-return deleted-message recovery
- Reinstates the verified, earlier recovery control flow: inspect the exact deleted-span array, recover its stored original text, return the recovered message immediately when successful, and restore the original array/continue through Twitch's stock formatter only when recovery returns null.
- Restores the span-specific support overload, so style handling runs only on a successfully recovered deleted message. Normal chat messages continue through Twitch's unmodified formatter.
- The beta.18 build and metadata check status will be updated from the release workflow; runtime testing by the user is still required.

### beta.19 — fix terminal continuation label in recovery injection
- beta.18 could not be applied: Morphe's label assembler treated the final `:kizu_deleted_messages_continue` label as an external target, but no external-label mapping was supplied.
- The injection now places a real `nop` at that label, keeping the continuation target inside the injected instruction block and allowing Twitch's original formatter to continue immediately afterward.
- This addresses the reported apply-time `ArrayIndexOutOfBoundsException: length=0; index=0`.
- Build, artifact publication, and runtime deleted-message behavior must each be verified separately; not runtime-verified yet.

### beta.20 — array-wide deleted-message recovery and settings crash containment
- beta.19 inspected only index 0 of Twitch's returned span array. Another span can appear first, so recovery now tries every candidate, matching the verified stable 1.9.2 behavior.
- Successful recovery returns the styled original immediately. If no candidate can be recovered or the setting is off, the helper returns null; the injected hook restores the original class register and span-array result before continuing through Twitch's stock formatter.
- Added guarded failure handling and diagnostic logging around Kizu settings-screen construction and styling so a settings UI exception does not automatically take down the entire Activity. This is crash containment, not proof the underlying settings exception is eliminated.
- Build/release verification and on-device runtime behavior must be tracked separately. Not runtime-verified yet.

### beta.21 — restore stable chat hook and protect landscape settings scrolling
- Reverted the deleted-message bytecode hook to the stable 1.9.2 structure: it calls recovery and replaces the formatter's message register, without injected early returns, external labels, or manual re-invocation of Twitch getSpans. This avoids the beta.16–20 control-flow rewrite that was implicated in chat-menu instability.
- Recovery scans all span candidates and now returns the original formatter input when no deleted span can be recovered or the setting is disabled.
- Both landscape gesture roots now avoid intercepting touches on scrollable/settings/quality surfaces, sliders, and compact player controls, so quality-menu swipes should reach Twitch's scroll handler instead of changing volume.
- Wrapped the Kizu settings entry click handler in Throwable handling with diagnostic logging.
- Build/release checks are not a substitute for device runtime testing; no runtime behavior claimed as verified.

## Morphe prerelease feed rule

Morphe uses:
- `main` for stable.
- `dev` for prereleases.

The prerelease workflow therefore triggers on `dev`, checks out `dev`, validates that the Gradle version is prerelease, validates `patches-bundle.json` and `patches-list.json`, builds the MPP, verifies the asset, pins the release tag to the exact commit, and publishes a GitHub prerelease.

Do not move the prerelease workflow back to a `beta` branch.

A prerelease must have all of these aligned:
1. `dev/gradle.properties` version.
2. `dev/patches-bundle.json` version.
3. `dev/patches-list.json` version.
4. Bundle download URL.
5. Git tag `vVERSION`.
6. GitHub prerelease asset `patches-VERSION.mpp`.

## Exact-fingerprint policy

**Never guess Twitch fingerprints.**

For a new Twitch version:
1. Obtain the exact APK/APKM.
2. Extract the base APK and DEX files.
3. Verify class, method, field, descriptor, resource ID, register and invocation details directly from that artifact.
4. Record the verified mapping under `reference/twitch-VERSION/`.
5. Only then implement the patch.
6. Build the MPP.
7. Verify Morphe metadata and release asset.
8. Runtime-test before calling it known-good.

Donor projects such as PurpleTV, BTTV Android, UYU, and older Twitch patches are references only. They do not establish fingerprints for a new Twitch build.

## Important failure patterns

### Wrong constructor register
For `Lout.<init>`, the player overlay View is p2, not p1.

### High-register invokes
When a target register is `v16+`, use the correct `/range` form where required. The verified second Create Clip path uses `v21`.

### Twitch re-applies visibility
Constructor hooks may not survive. If a later state/delegate explicitly calls `setVisibility`, bind the final visible view after the verified field load.

### Multiple early returns
Do not assume a method's final return is the only execution path. `Llrx.v()` has multiple early returns.

### Generated smali syntax
Never leave literal Kotlin placeholders such as `$register`, `$SUPPORT_CLASS`, `$methodName`, or `$targetRegister` in emitted smali. Do not add a trailing semicolon to invoke instructions.

### Crash vs no-op
- A patch that builds but does nothing usually indicates the wrong object/fingerprint.
- A patch that crashes on stream open usually indicates an unsafe control-flow/state hook.
- A Morphe parser/lexer error means inspect the generated smali first; do not change fingerprints blindly.

## Runtime verification policy

Build success is not runtime success.

Use these labels:
- **Build verified** — Gradle/MPP build and automated artifact checks pass.
- **Morphe verified** — metadata/source discovery and patch application pass.
- **Runtime verified** — installed on Twitch target and observed working.
- **Known-good** — all required verification levels have been completed.

For 1.9.3-beta.3.9, player Cast/Share behavior is **runtime verified** by the user.

## Repository documentation policy

For every future feature/release, document:
- target Twitch version/build;
- exact verified fingerprints;
- source/donor attribution;
- implementation strategy;
- settings/default behavior;
- failed approaches and why they failed;
- build/workflow/release identifiers;
- artifact SHA256 when available;
- runtime verification status;
- migration notes for the next Twitch version.

Keep reverse-engineering records separate from user-facing README prose. Do not delete failed approaches: they are valuable regression-avoidance records.

## Files to consult first

- `reference/twitch-31.3.1/PLAYER-CONTROLS.md` — exact player-control fingerprints and failure history.
- `reference/twitch-31.3.1/README.md` — existing Twitch 31.3.1 reverse-engineering notes.
- `reference/channel-points/` — Channel Points artifact/reference archive.
- `README.md` — project purpose, attribution, sources and build notes.


### beta.22 — restore gestures and recover deleted messages without re-running getSpans
- beta.21 user test: settings and chat-menu crashes are gone and quality-menu scrolling works, but volume gestures disappeared and deleted messages still show Twitch's placeholder.
- Gesture handling no longer treats every descendant that reports `canScrollVertically()` or every small clickable view as a settings surface. Only named quality/settings/menu controls and known scroll-widget classes are excluded, restoring normal player-area gestures while protecting the quality menu.
- Deleted-message recovery again returns a recovered message directly from Twitch's formatter so the placeholder path is bypassed. The hook uses the exact receiver register from the verified `SpannedString.getSpans` invocation and preserves Twitch's original span-array register. It uses the existing Class-argument register as scratch space, restores it before falling through, and does not re-run `getSpans`.
- A null recovery result continues through Twitch's original formatter; a successful result returns immediately. Build verification and user runtime confirmation remain separate.
