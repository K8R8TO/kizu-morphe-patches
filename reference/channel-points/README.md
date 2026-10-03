# Channel Points / APK Reference Archive

This directory is the permanent reference for the Channel Points auto-claim investigation. It records the exact uploaded Twitch APKM and PurpleTV APK used in the October 2026 investigation, together with the relevant reverse-engineering findings and the external PurpleTV ReVive implementation that demonstrated a working background auto-claim design.

## Important rule

**Do not treat PurpleTV's or another donor's obfuscated Twitch class/method names as authoritative for Twitch 31.3.1.** The uploaded Twitch 31.3.1 package is authoritative for the target build.

The safe architectural conclusion from the investigation is:

- UI chest scanning/clicking is fragile and should not be the primary claim mechanism.
- PurpleTV ReVive's working implementation claims at the Twitch GraphQL layer.
- The GraphQL path uses the logged-in user's existing Twitch OAuth token, the current channel ID/login, an available claim ID, and Twitch's `ClaimCommunityPoints` mutation.
- For Kizu, this GraphQL mechanism should be implemented separately from Twitch's `CommunityPointsModel` lifecycle. Previous experiments that modified that provider/model lifecycle caused severe regressions where only the stream loaded and chat/UI stopped working.
- A UI popup can be added after a confirmed GraphQL result; it should never be used as the claim mechanism or as a prerequisite for claiming.
- The current Kizu setting is intentionally defaulted ON. See `Settings.java`.

## Reference files

- `twitch-31.3.1.md` — exact uploaded Twitch APKM metadata, hashes, split layout, Channel Points classes, methods, GraphQL strings and reverse-engineering notes.
- `purpletv-2.4-r2.md` — exact uploaded PurpleTV 2.4_r2 metadata, hashes, inventory and Channel Points findings.
- `purpletv-revive-channelpoints.kt` — attributed copy of the current PurpleTV ReVive ChannelPoints implementation used as the architectural reference. This is reference code, not Kizu code.
- `file-hashes.sha256` — cryptographic hashes for the uploaded container/APKM and important extracted components.
- `copilot-assessment.md` — assessment of the Copilot diagnosis against the actual current Kizu tree and the uploaded artifacts.

## Source lineage

PurpleTV ReVive source:
https://github.com/alienware377/purpletv-revive

ChannelPoints implementation:
https://github.com/alienware377/purpletv-revive/blob/main/xposed/app/src/main/java/tv/purple/xp/ChannelPoints.kt

The PurpleTV ReVive project is Apache-2.0 licensed. The copied reference file is explicitly marked as external reference material and should not be mistaken for Kizu-original code.

## Why the APKs are not copied into Git

The uploaded Twitch APKM is about 110 MiB and the uploaded PurpleTV APK is about 188 MiB. GitHub's normal Git object/file limits make committing the raw APK/APKM inappropriate, and the Twitch application is proprietary. The repository therefore stores identifying hashes and a detailed technical inventory instead. The hashes allow a future uploaded artifact to be verified as the same artifact without depending on filename alone.
