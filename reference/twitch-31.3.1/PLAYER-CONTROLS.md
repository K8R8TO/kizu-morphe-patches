# Twitch Android 31.3.1 Verified Player Controls Reference

This file records the exact fingerprints and lessons used for Kizu 1.9.3 player-control patches. **Do not replace these with guessed fingerprints from another Twitch build or donor project.**

## Target artifact

- Package: `tv.twitch.android.app`
- Version: `31.3.1`
- Build code: `3103016`
- Exact artifact: supplied Twitch 31.3.1 APKM
- Device used for runtime validation: Samsung SM-G990B2
- Android: 16 / API 36
- Morphe Manager: 1.34.0
- Morphe Patcher: 1.15.1

The supplied APK/APKM is authoritative for Twitch 31.3.1 bytecode. PurpleTV is a behavioral donor/reference only.

## Verified Create Clip fingerprints

### Primary player overlay constructor

Class: `Lout;`

Constructor:
`Lout;-><init>(Landroid/content/Context;Landroid/view/View;Lo57;Lylg;Lxks;Lh7a;)V`

Location:
- classes5.dex
- code offset: `0x4e837c`
- registers: 23
- ins: 7
- outs: 5

Exact fields:
- `Lout.j : Landroidx/compose/ui/platform/ComposeView;`
  - resource `0x7f0b05f0` = player Create Clip
- `Lout.k : Landroid/widget/ImageView;`
  - resource `0x7f0b128a` = player Share / Live Share
- `Lout.q : Landroidx/mediarouter/app/MediaRouteButton;`
  - resource `0x7f0b0c0f` = Cast

Critical detail:
- The player overlay View is **p2**, not p1.
- Earlier p1-based implementations were wrong and silently did nothing.

## Verified second Create Clip path

Class: `Ld040;`

Constructor:
`Ld040;-><init>(Llp30;Lew0;Lylg;Lql40;Lwvl;Lo57;Lqi70;)V`

Finding:
- resource `0x7f0b05f2` occurs exactly once in the target;
- resolved through `Lrms.k(I,View)` into `v21`;
- immediately null-tested.

Patch rule:
- `v21` requires the range form:
  `invoke-static/range { v21 .. v21 }, ...`
- Do not emit `invoke-static { v21 }`; Morphe previously failed with `NoSuchElementException: Collection is empty`.

## Verified final visible player-header controls

Twitch's `Lout` constructor fields are not necessarily the final visible header controls. Twitch later creates/controls a player-header binding and can reapply visibility.

Binding class: `Lqot;` (classes2.dex)

Relevant fields:
- `Lqot.e : Landroidx/mediarouter/app/MediaRouteButton;`
  - resource `0x7f0b0394` = visible Cast
- `Lqot.r : Landroid/widget/ImageView;`
  - resource `0x7f0b128a` = visible Share / Live Share
- `Lqot.i : Landroidx/compose/ui/platform/ComposeView;`
  - resource `0x7f0b05f2` = visible Create Clip text/Compose control

## Critical state controller

Class: `Llrx;`

Field: `d : Lqot`

Method:
`Llrx;->v(Ltv/twitch/android/core/mvp/viewdelegate/ViewDelegateState;)V`

This method directly loads `Lqot.r` and `Lqot.e` and calls `View.setVisibility(I)V`. It contains multiple early returns.

### Correct strategy

Bind the exact visible views **immediately after every verified `iget-object` load** of:
- `Lqot.r` -> Share
- `Lqot.e` -> Cast

This survives Twitch's later visibility updates better than constructor-only hooks.

Do not use:
- constructor-only Lout.k/Lout.q hiding;
- recursive root hierarchy scanning;
- state-model forcing;
- a single final-return hook in `Llrx.v()`;
- `Lout.z()` return hooks without fresh verification.

## Cast setup warning

Method: `Lout;->z()V`

It reads `Lout.q` and calls `MediaRouteButton.setVisibility(I)V`, with multiple returns.

A direct return hook was unsafe in the tested combinations and caused stream-opening crashes. Do not revive it without re-verifying against a new APK.

## Extension support

Class:
`app.morphe.extension.appearance.PlayerOverlaySupport`

Methods:
- `bind(View)` -> Create Clip
- `bindLiveShareButton(View)` -> Share / Live Share
- `bindCastButton(View)` -> Cast
- `bindPlayerControls(View, View)` -> compatibility helper
- `bindTextClipButton(View)` -> second Create Clip path

Visibility is implemented with `HiddenView.attach(...)`, using attach-state and pre-draw handling so Twitch cannot simply restore the control on the next render.

## Settings

Player Controls:
- `hide_player_create_clip_button` — default ON
- `hide_player_live_share_button` — default ON
- `hide_cast_button` — default ON

Expected:
- ON = hidden
- OFF = restored
- default installation = Create Clip, Share/Live Share and Cast hidden

Beta 3 added Cast + Share together. Create Clip originated in beta 1.

## Verified landscape swipe-to-portrait handler

Target artifact: the supplied Twitch 31.3.1 APKM (build code 3103016). These identifiers and offsets were inspected from its actual `base.apk`, not inferred from PurpleTV.

- DEX: `classes2.dex`
- Parent container: `Ltv/twitch/android/shared/ui/elements/draggable/ConstraintTheatreContainerView;`
- Parent interception method: `Ltv/twitch/android/shared/ui/elements/draggable/DraggableConstraintLayout;->onInterceptTouchEvent(Landroid/view/MotionEvent;)Z`, code-item offset `0x651b70`; 3 registers and 28 code units
- The parent interception method calls its virtual `p(MotionEvent)Z` decision at code-unit offset `0x0a`, so the early guard is inserted before the parent asks whether it should take the gesture.
- Subclass interception decision: `p(Landroid/view/MotionEvent;)Z`, code-item offset `0x65105c`; 7 registers and 86 code units
- Fallback touch handler: `onTouchEvent(Landroid/view/MotionEvent;)Z`, code-item offset `0x650fdc`; 5 registers and 55 code units
- The decision method references the container's `k(MotionEvent)Z`, `MotionEvent.getRawY()F`, and `Lv880;.s(MotionEvent)Z` drag-helper path.
- The fallback handler calls both `DraggableConstraintLayout.onTouchEvent(MotionEvent)` and `ScaleGestureDetector.onTouchEvent(MotionEvent)`.
- Layout resources `twitch_rn_theatre_fragment.xml` and `theatre_container_layout.xml` instantiate this custom draggable container. It can intercept before the nested `theatre_root_container`; changing only that inner layout cannot prevent the parent from taking the gesture first.

### Guard strategy

Beta.13 adds an earlier guard directly to the verified superclass `DraggableConstraintLayout.onInterceptTouchEvent(MotionEvent)`. It only short-circuits when the runtime object is a `ConstraintTheatreContainerView`, the user toggle is ON, and the device is in landscape; this is before Twitch invokes its drag decision. Beta.12's subclass decision and fallback-touch guards remain as defense-in-depth.

The patch checks the existing `disable_landscape_swipe_to_portrait` setting at runtime. When the setting is ON and the container is in landscape, it makes the parent's interception decision return false and prevents any fallback touch events handled by the outer container from reaching Twitch's drag helper. When the setting is OFF or orientation is not landscape, the original methods continue unchanged. Kizu's child-level brightness and volume handling remains in place.

## PurpleTV behavioral reference

PurpleTV was used as a behavioral reference, not as proof of Twitch 31.3.1 fingerprints.

Relevant observations:
- `PlayerOverlayViewDelegate` hides Create Clip in its constructor and again in `setClipButtonState`.
- `setupChromecast()` hides the cast button.
- `setShouldShowChromecast(boolean)` controls Chromecast state.
- `UIHook.maybeHideOverlayHeaderButtons(createClipButton, shareButton)` hides header Create Clip + Share.
- `UIHook.maybeHideCastButton(MediaRouteButton)` hides Cast.

The useful lesson is that Twitch can reapply visibility after construction.

## Failed approaches — do not repeat

1. Wrong `Lout` constructor register: p1 instead of p2 -> no-op.
2. Constructor-only `Lout.k/q`: not final visible controls.
3. Root hierarchy scanning: unreliable.
4. Create Clip state-level hook: broke stream loading/chat.
5. Single final-return hook in `Llrx.v()`: misses early returns; some combinations crashed streams.
6. Direct `Lout.z()` return hooks: unsafe in tested combinations.
7. Guessed donor fingerprints: never acceptable.
8. Non-range invoke on `v21): caused Morphe `Collection is empty`.
9. Literal Kotlin interpolation such as `$register`, `$SUPPORT_CLASS`, `$methodName`, `$targetRegister`: produces invalid smali/parser failures.
10. Trailing semicolon after invoke instructions: caused a historical beta 3 parser/lexer failure.

## Runtime verification history

### 1.9.3-beta.1.10
- Create Clip hide runtime-confirmed by the user.
- MPP SHA256: `25a291e75a8b2419e1e0e42479ce562dc3e2204fb7e4bac1960ba7ac079db7ed`

### 1.9.3-beta.3.2
- Build/release succeeded.
- User installed it; Share + Cast remained visible.
- Proved constructor-level Lout.k/Lout.q binding was insufficient.

### beta.3.3 / beta.3.4
- Semantic visibility hooks attempted.
- User reported stream-opening crashes.
- These approaches were removed/reworked.

### beta.3.6 / beta.3.7
- Unsafe semantic hooks removed.
- 3.7 encountered generated-smali parser/lexer failure.
- Register/range generation and interpolation were corrected.

### 1.9.3-beta.3.9 — current known-good
- Final strategy binds exact visible `Lqot.r` Share and `Lqot.e` Cast views inside `Llrx.v()` after every verified field load.
- Create Clip exact binding retained.
- User runtime-tested the installed release and confirmed it works: Cast and Share are hidden by default and appear when their toggles are turned OFF.

## Maintenance rule

For any future Twitch version:
1. Obtain the exact APK/APKM.
2. Verify class, method, field, descriptor, resource ID and register details directly from that artifact.
3. Record the mapping under `reference/twitch-VERSION/` before implementing the patch.
4. Prefer final visible-view bindings when Twitch re-applies state.
5. Build and verify the MPP.
6. Verify Morphe metadata and release asset.
7. Runtime-test before calling the release known-good.
8. Keep failed approaches documented to prevent regressions.

Build/release success is never a substitute for runtime verification.
