# Kizu Twitch Patches

Morphe-compatible patches for the **Twitch Android app**, maintained for Kizu's Twitch enhancements.

This repository contains **Twitch patches only**.

**Current stable:** [v1.9.2](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2)  
**Target:** Twitch Android **31.3.1** (tv.twitch.android.app)  
**Format:** Morphe .mpp patch bundle

## Included patch

Morphe exposes one user-facing patch:

- **Twitch Enhancement**

The patch internally combines the feature implementations below. Individual internal patches are intentionally hidden from Morphe's user-facing patch list.

---

# Features

The list below describes the feature set provided by the current Kizu Twitch patch. Some settings are enabled by default and can be changed from the Kizu settings screen.

## General

### Automatic Channel Points
- Automatically claims available Channel Points bonus chests while watching a channel.
- Uses Twitch's own GraphQL claim operation and the Twitch 31.3.1 Channel Points data exposed by the target application.

### Default Home Tab
- Choose which Twitch tab opens by default:
  - Following
  - Live
  - Clips

### Hide Stories
- Hide the Stories shelf from the Following/Home feed.

## Appearance and promotion controls

- Hide the row containing **Subscribe / Gift Sub / Bits** controls above chat.
- Hide the **Bits** button beside the chat emote button.
- Hide the **gift leaderboard** above chat.
- Hide **subscription/promotion banners**, including Twitch promotion and Turbo-related presentation.
- Hide **Go Ad-Free / Turbo** from the Following feed.
- Hide the **Continue Watching** section from the Following feed.
- Hide the **Offline Channels** section from the Following feed.
- Disable Twitch's **link disclaimer** before opening supported external links.

## Ads and playback

- Device-side Twitch **ad blocking** for live/VOD/display advertising.
- Optional stream proxy support for ad-removal paths that require a proxy.
- Built-in proxy choices plus a custom proxy URL.
- Proxy fallback behavior when a configured proxy fails.
- Stream-side ad presentation can be covered/muted while the ad period is active where the selected playback path supports it.

## Third-party emotes

- **7TV emotes**
- **BTTV emotes**
- **FFZ / FrankerFaceZ emotes**
- One master **3rd party emotes** setting controlling the supported third-party providers.
- Animated third-party emotes.
- Third-party emote picker/menu integrated alongside Twitch's native emote picker.
- Third-party emote autocomplete while typing.
- Zero-width / overlay emotes.
- Third-party emote caching and image loading.
- Twitch-version-specific emote URL compatibility.

### Native Twitch picker compatibility

Kizu's third-party picker is kept separate from Twitch's native picker. Twitch's own picker and its native three-button menu are not replaced by the third-party implementation.

## Chat

### Deleted messages
- Keep deleted chat messages visible locally.
- Deleted-message presentation modes:
  - Mod
  - Strikethrough
  - Grey

### Timestamps
- Show timestamps on chat messages.
- Configurable timestamp formatting.

### Mention highlighting
- Highlight chat messages that directly mention the logged-in account.
- Custom mention highlight color.
- Optional sound notification when mentioned.
- Configurable mention-sound cooldown.

### Landscape chat
- Enable/disable custom landscape chat sizing.
- Configurable landscape chat width.
- Enable/disable custom landscape chat opacity.
- Configurable landscape chat opacity.

## Danmaku

Optional Niconico-style scrolling chat over the video:

- Danmaku comments on live streams.
- Portrait mode.
- Mini-player mode.
- Picture-in-picture mode.
- Option to hide landscape chat while using Danmaku.
- Configurable number of rows.
- Configurable occupied video area.
- Configurable message lifetime.
- Configurable maximum messages on screen.
- Custom font selection.
- Configurable font weight.
- Custom text color.
- Custom outline color.
- Configurable outline width.
- Configurable opacity.
- In-player Danmaku toggle/control.

## Player controls and gestures

The Kizu Twitch baseline also contains the following player-side functionality:

- **Forward seek gesture** with configurable duration.
- **Rewind seek gesture** with configurable duration.
- **Volume swipe gesture**.
- **Brightness swipe gesture**.
- Optional **gesture OSD** showing adjustment feedback.
- **Refresh/reload stream** button.
- **Video/player statistics** button.

These player features are part of the Kizu feature baseline and are kept separate from newer 1.9.3 player-hook work.

## Privacy and compatibility

- Disable **Comscore** measurement.
- Disable Twitch **crash reporting / Bugsnag** integration.
- Twitch login compatibility fixes for patched builds.
- Twitch notification compatibility fixes.

## Settings

Kizu exposes its functionality through the custom settings UI with sections for:

- General
- Appearance
- Danmaku
- Ads
- Emotes
- Chat
- Home & navigation
- Privacy

The **Home & navigation** section specifically contains:

- Hide Go Ad-Free
- Hide Continue Watching
- Hide Offline Channels

---

# Sources, attribution and reused work

Kizu is a derivative/combined project. This section deliberately distinguishes **directly adapted code**, **technical/reference sources**, **the target application used for reverse engineering**, and **external services**.

## Directly adapted source repositories

### UYU

**Repository:** https://github.com/bakwudo/uyu

Major upstream for the original Twitch patch architecture and a substantial portion of the base implementation.

Used/adapted for:

- Twitch patch organization and architecture.
- Twitch-specific patch structure and compatibility handling.
- Settings integration and the Kizu/UYU settings framework.
- Login compatibility.
- Notification compatibility.
- Ad and promotion handling.
- Danmaku implementation and supporting UI.
- Channel Points patch structure.
- Shared patch/extension utilities.
- Twitch compatibility plumbing.

Kizu substantially modifies and extends this codebase for the current feature set and Twitch 31.3.1.

### Hooman's Morphe Patches

**Repository:** https://github.com/arandomhooman/hoomans-morphe-patches

Primary upstream for the third-party Twitch emote system.

Used/adapted for:

- 7TV / BTTV emote loading architecture.
- Emote catalog/data structures.
- Emote image loading and caching.
- Chat emote rendering.
- Third-party emote data handling.
- Emote-related patch patterns and fingerprints where applicable.
- Login and notification fixes where the Kizu source explicitly identifies Hooman's implementation as the basis.

The original Hooman implementation targets older Twitch builds, so Kizu's implementation contains substantial version-specific adaptation for Twitch 31.3.1.

---

# Framework and build sources

### Morphe

**Organization:** https://github.com/MorpheApp

### Morphe Patches

**Repository:** https://github.com/MorpheApp/morphe-patches

### Morphe Patches Template

**Repository:** https://github.com/MorpheApp/morphe-patches-template

### Morphe Patcher

**Repository:** https://github.com/MorpheApp/morphe-patcher

### Morphe Documentation

**Repository:** https://github.com/MorpheApp/morphe-documentation

Used for:

- Patch DSL and bytecode patching APIs.
- Fingerprints and compatibility mechanisms.
- Patch project/template structure.
- Extension and patch packaging.
- .mpp bundle generation.
- Morphe build tooling and release metadata.
- Morphe licensing and NOTICE requirements.

Morphe provides the patching framework; it is not the source of Kizu-specific Twitch features.

---

# Additional reference repositories

These repositories were used as technical/reference material or as part of the upstream lineage. They are **not** presented as direct copied source unless a specific source file says otherwise.

### ReVanced

**Repository:** https://github.com/ReVanced/revanced-patches

Upstream/reference lineage identified by UYU and Hooman's work. Kizu preserves that lineage for inherited patching concepts and patterns.

### niconico-yt-morphe-patches

**Repository:** https://github.com/david419kr/niconico-yt-morphe-patches

Reference for the Niconico-style Danmaku overlay concept credited through the UYU lineage.

### morphe-androidtv-patches

**Repository:** https://github.com/ajstrick81/morphe-androidtv-patches

Reference for client-side Twitch ad-blocking concepts credited through UYU.

### bttv-android

**Repository:** https://github.com/bttv-android/bttv

Independent technical reference used while investigating:

- Android Twitch third-party emotes.
- 7TV / BTTV / FFZ behavior.
- Automatic Channel Points claiming approaches.

Kizu's current Channel Points implementation is not represented as copied from bttv-android.

### PurpleTV ReVive

**Repository:** https://github.com/alienware377/purpletv-revive

Reference for the readable background **Channel Points auto-claim architecture** used to guide Kizu's implementation.

The exact Twitch 31.3.1 query/mutation shapes and obfuscated class/method signatures are derived from the supplied Twitch 31.3.1 APKM rather than assumed from PurpleTV's older Twitch version.

---

# PurpleTV 2.4_r2 reference

An exact **PurpleTV 2.4_r2 APK** was supplied and inspected during development.

It was used as an implementation/reference artifact for several player and UI investigations, including:

- Player Cast control handling.
- Player Clip control handling.
- Player Live Share control handling.
- Player reload/refresh behavior.
- Player statistics concepts.
- Volume gestures.
- Brightness gestures.
- Gesture OSD behavior.
- PurpleTV UI/adapter behavior used when investigating Twitch Following-feed sections.

This is a **binary reference artifact**, not a claim that PurpleTV source code was copied wholesale into Kizu.

For Channel Points specifically, the public PurpleTV ReVive repository above is the separately credited source.

---

# Twitch Android

**Target application:** Twitch Android 31.3.1  
**Package:** tv.twitch.android.app

A genuine Twitch 31.3.1 APKM was supplied and inspected during development.

Twitch is the **reverse-engineering target**, not an open-source dependency.

The APKM was used to establish the authoritative Twitch 31.3.1 implementation for:

- Obfuscated class and method signatures.
- Exact bytecode fingerprints.
- Channel Points provider/model structure.
- Channel Points claim operation.
- Native emote-picker structures.
- Emote animation-related behavior.
- Emote URL construction.
- Following-feed section construction.
- Home/navigation UI structures.
- Player overlay structures.
- Resource identifiers used for UI hooks.

Kizu does not distribute Twitch's proprietary source code.

### Important versioning rule

Donor projects are not treated as authoritative for Twitch 31.3.1 obfuscated names.

When implementing a Twitch hook, the actual **Twitch 31.3.1 APKM is authoritative**. PurpleTV, BTTV Android, older Twitch builds, or other donor projects are references only unless the target build independently confirms the same structure.

---

# External services

### 7TV

**Project:** https://7tv.app/

Used for:

- Public emote-set data.
- Emote IDs and names.
- Static and animated emote assets.
- Emote metadata used by the third-party emote loader and picker.

### BetterTTV

**Project:** https://betterttv.com/

Used for:

- Public BTTV emote-set data.
- Emote IDs and names.
- Static and animated emote assets.
- Emote metadata used by the third-party emote loader and picker.
- FFZ-compatible emote data exposed through public endpoints.

### FrankerFaceZ

**Project:** https://www.frankerfacez.com/

Used for:

- FFZ global/channel emote data.
- FFZ emote metadata and image assets.

These services are external dependencies of the third-party emote functionality. Their service code is not included in Kizu.

---

# Android / platform references

### Android / AOSP

**Android documentation:** https://developer.android.com/  
**AOSP:** https://android.googlesource.com/platform/frameworks/base/

Used for standard platform behavior including:

- Android View and lifecycle APIs.
- Image decoding.
- Animated image rendering.
- Popup and input-method behavior.
- Keyboard handling.
- Player gesture/UI behavior.
- Screen-brightness handling.
- Media-volume handling.

These are platform/API references, not copied application source.

---

# What is Kizu-specific

Kizu-specific development includes, among other things:

- Porting and combining the feature set for Twitch 31.3.1.
- Reverse-engineering Twitch 31.3.1 rather than relying on donor fingerprints.
- Kizu's third-party emote picker bridge.
- Integration between the adapted Hooman emote system and Twitch's native emote picker.
- Preservation of Twitch's native emote picker while adding the Kizu third-party picker.
- Kizu-specific search and keyboard behavior for the emote picker.
- Twitch 31.3.1 emote URL adaptation.
- Animated 7TV/BTTV/FFZ rendering fixes.
- Zero-width emote support.
- Kizu-specific Channel Points polling/retry logic using Twitch's exact claim operation.
- Kizu settings behavior and settings organization.
- Home & navigation controls and the reversible Following-feed section filtering introduced for 1.9.2.
- Twitch 31.3.1 compatibility fixes.
- Regression fixes found through actual Morphe patching and runtime testing.

Where implementation was directly adapted from another project, that upstream is identified above.

---

# Source relationship summary

| Source | Relationship |
|---|---|
| [UYU](https://github.com/bakwudo/uyu) | **Major directly adapted upstream codebase** |
| [Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches) | **Directly adapted for third-party emotes and identified compatibility fixes** |
| [Morphe](https://github.com/MorpheApp) | **Patching framework / ecosystem** |
| [Morphe Patches](https://github.com/MorpheApp/morphe-patches) | **Framework/reference implementation** |
| [Morphe Patches Template](https://github.com/MorpheApp/morphe-patches-template) | **Project/template reference** |
| [Morphe Patcher](https://github.com/MorpheApp/morphe-patcher) | **Bytecode patching/build framework** |
| [Morphe Documentation](https://github.com/MorpheApp/morphe-documentation) | **Framework documentation** |
| [ReVanced](https://github.com/ReVanced/revanced-patches) | **Indirect upstream lineage/reference** |
| [niconico-yt-morphe-patches](https://github.com/david419kr/niconico-yt-morphe-patches) | **Danmaku reference via UYU** |
| [morphe-androidtv-patches](https://github.com/ajstrick81/morphe-androidtv-patches) | **Ad-blocking reference via UYU** |
| [bttv-android](https://github.com/bttv-android/bttv) | **Independent technical reference** |
| [PurpleTV ReVive](https://github.com/alienware377/purpletv-revive) | **Channel Points architecture/reference** |
| PurpleTV 2.4_r2 APK | **Exact player/UI reference artifact** |
| Twitch Android 31.3.1 APKM | **Authoritative reverse-engineering target; proprietary** |
| 7TV | **External emote service/API** |
| BetterTTV | **External emote service/API** |
| FrankerFaceZ | **External emote service/API** |
| Android / AOSP | **Platform/API reference** |

The presence of a project in this table does not automatically mean its source code was copied into Kizu. The relationship column defines the role of each source.

---

# Reference archives

The repository contains permanent reverse-engineering/reference material under reference/.

## Twitch 31.3.1 reference

See:

- reference/twitch-31.3.1/

This records the target-build fingerprints and reverse-engineering notes used to avoid guessing obfuscated Twitch signatures.

## Channel Points reference

See:

- reference/channel-points/README.md

This archive records the exact Twitch 31.3.1 APKM findings, PurpleTV 2.4_r2 evidence, and the external PurpleTV ReVive implementation used to establish the current auto-claim design.

---

# Add to Morphe

Add this repository as a remote patch source in Morphe Manager:

github.com/K8R8TO/kizu-morphe-patches

Morphe can then fetch the published Kizu patch bundle and its release metadata.

---

# Building locally

Build the Android patch bundle with:

~~~bash
./gradlew buildAndroid
~~~

The generated .mpp bundle is written to:

patches/build/libs/

---

# Development and release policy

Kizu targets a specific Twitch build rather than chasing arbitrary versions.

For each Twitch version:

1. Reverse-engineer the actual target APK/APKM.
2. Derive and validate fingerprints from that target.
3. Patch one feature at a time where practical.
4. Build the Morphe bundle.
5. Verify release metadata.
6. Runtime-test the patched application before considering the feature complete.

Experimental changes are kept out of the stable release until they have been independently verified.

---

# License

This project follows the licenses and additional conditions included in the repository's LICENSE and NOTICE files.

Upstream licenses and attribution requirements remain applicable to the respective reused/adapted components.

Kizu does not claim ownership of Twitch, 7TV, BetterTTV, FrankerFaceZ, PurpleTV, Morphe, UYU, Hooman's Morphe Patches, or any other upstream/external project named above.
