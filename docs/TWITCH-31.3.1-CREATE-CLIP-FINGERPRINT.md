# Twitch 31.3.1 Create Clip Fingerprint Reference

This is the authoritative reverse-engineering reference for Kizu Hide Create Clip on Twitch 31.3.1.

## Test environment

- App: Twitch
- Package: tv.twitch.android.app
- Version: 31.3.1
- Build code: 3103016
- Kizu source: Kizu Twitch Patches
- Current feature series: 1.9.3-beta.1
- Morphe Manager: 1.34.0
- Morphe Patcher: 1.15.1
- Libraries: kept
- Android: 16 / API 36
- Device: Samsung SM-G990B2
- Reported memory: 2.06 GB / 7.72 GB
- Reported storage: 179.93 GB / 240.67 GB

The exact Twitch 31.3.1 APKM supplied for this project is the source of truth for every obfuscated fingerprint below.

## Verified resources

| Resource | ID | Role |
| --- | --- | --- |
| create_clip_button_compose_view | 0x7f0b05f0 | Player Create Clip ComposeView |
| create_clip_text_button | 0x7f0b05f2 | Separate player Create Clip text ComposeView |

create_clip_button_compose_view is resolved in Lout.<init> and stored in Lout.j.
create_clip_text_button is resolved separately in Ld040.<init> as ComposeView register v21.

## Verified Lout constructor

Signature:

    Lout;-><init>(Landroid/content/Context;Landroid/view/View;Lo57;Lylg;Lxks;Lh7a;)V

Code offset in the supplied APKM: 0x4e837c.

Instance register mapping:

- p0 = Lout instance
- p1 = Context
- p2 = player overlay View

Never pass p1 when a player View is required. An earlier Kizu attempt did that and therefore could not operate on the overlay.

## Verified Lout fields

    Lout.j : Landroidx/compose/ui/platform/ComposeView;
    Lout.k : Landroid/widget/ImageView;
    Lout.q : Landroidx/mediarouter/app/MediaRouteButton;

Verified resource-backed construction:

    0x7f0b05f0 -> findViewById -> ComposeView -> Lout.j
    0x7f0b128a -> findViewById -> ImageView -> Lout.k
    0x7f0b0c0f -> findViewById -> MediaRouteButton -> Lout.q

Therefore on Twitch 31.3.1: j = create_clip_button_compose_view, k = share ImageView, q = cast button.

## Verified second Create Clip path

Exact constructor:

    Ld040;-><init>(Llp30;Lew0;Lylg;Lql40;Lwvl;Lo57;Lqi70;)V

Inside it:
- resource 0x7f0b05f2 is resolved
- the result is cast to ComposeView
- the ComposeView is held in register v21
- the next instruction checks v21 for null

Register rule:
- Correct: invoke-static/range { v21 .. v21 }, ...
- Incorrect: invoke-static {v21}, ...

The incorrect non-range form caused Morphe 1.9.3-beta.1.9 to fail during InlineSmaliCompiler with java.util.NoSuchElementException: Collection is empty.

## Required implementation

The Hide Create Clip patch must cover both verified player controls:
1. Lout.j / create_clip_button_compose_view
2. Ld040 v21 / create_clip_text_button

Use view-level hiding through HiddenView. Do not modify Twitch ClipButton UI state/model objects.

## Historical failures

1. State-level ClipButton mutation caused streams to reload indefinitely with chat failing to load. Do not reuse.
2. Passing Lout p1 instead of p2 passed Context instead of the player View.
3. Recursive overlay scanning failed to reliably hide the visible Create Clip control.
4. The 1.9.3-beta.1.9 v21 invoke used the wrong non-range register form and did not install.

## Release verification rules

Before another Create Clip release:
1. Verify fingerprints against the supplied Twitch 31.3.1 APKM.
2. Verify 0x7f0b05f0 -> Lout.j.
3. Verify 0x7f0b05f2 -> Ld040 ComposeView v21.
4. Use /range for registers above 15.
5. Build the complete MPP through GitHub Actions.
6. Verify prerelease asset and metadata.
7. Never call the feature runtime-verified without an actual device test.

## Reported 1.9.3-beta.1.9 failure

Environment: Twitch 31.3.1, Kizu 1.9.3-beta.1.9, Morphe Manager 1.34.0, Morphe Patcher 1.15.1, Android 16/API 36, Samsung SM-G990B2.

Failure:
app.morphe.patcher.patch.PatchException: The patch "Twitch Enhancement" depends on a BytecodePatch which raised an exception.
Caused by: java.util.NoSuchElementException: Collection is empty.
Failure occurred in InlineSmaliCompiler during PlayerOverlayUiPatch.kt.

Root cause confirmed from the exact APK: the target register is v21, so the normal 35c invoke form cannot encode it; invoke-static/range is required.

Last updated for 1.9.3-beta.1.10.

## Verified player Share and Cast controls

The same Twitch 31.3.1 player overlay constructor exposes the other two requested player controls:

- Lout.k : Landroid/widget/ImageView; = Share/Live Share control
- Lout.q : Landroidx/mediarouter/app/MediaRouteButton; = Cast control

Verified resource lookups from the supplied APKM:

- 0x7f0b128a -> Lout.k -> player Share/Live Share ImageView
- 0x7f0b0c0f -> Lout.q -> player Cast MediaRouteButton

The combined beta.3 hook uses the same already-proven Lout constructor and calls:

    invoke-static {v0, v1}, Lapp/morphe/extension/appearance/PlayerOverlaySupport;->bindPlayerControls(Landroid/view/View;Landroid/view/View;)V

The runtime controls are independently governed by:

    hide_player_live_share_button
    hide_cast_button

Both are exposed under Player Controls.

## Release-plan exception

1.9.3-beta.3 intentionally skips the previously planned Cast-only beta.2 and implements Hide Live Share + Hide Cast together. Hide Create Clip remains unchanged from the working beta.1.10 implementation.

Last updated for 1.9.3-beta.3.
