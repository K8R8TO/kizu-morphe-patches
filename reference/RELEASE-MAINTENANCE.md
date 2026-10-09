# Kizu Twitch Patches — Release & Maintenance Record

## Current known-good state

- Stable: `1.9.2`
- Current prerelease: `1.9.3-beta.16`
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

### beta.16 — high-register Smali invoke fix
- Replaced dynamically targeted single-register invokes with `invoke-static/range` where the target register can be above `v15`.
- Changed deleted-message recovery to pass only the message into the extension bridge; the bridge retrieves spans itself, avoiding a two-register non-range invoke that could reject `v16+`.
- Validation: the beta.16 workflow and release asset establish build/metadata status; runtime behavior still requires user verification.

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
