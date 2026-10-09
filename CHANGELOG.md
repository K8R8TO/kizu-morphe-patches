# [1.9.3-beta.4.4](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.3-beta.4.4) (2026-10-09)

### Player Controls

* fix Reload Stream no-op clicks by retaining the native reload host for the button binding lifetime
* keep one-tap reload behavior and the existing Player Controls toggle
* preserve the button beside Mute and keep Reload Stream bundled inside Twitch Enhancement

# [1.9.3-beta.4.3](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.3-beta.4.3) (2026-10-09)

### Player Controls

* fix reload button binding to the live player instead of relying on a mismatched Compose callback owner
* keep the XML player control synchronized with Mute visibility as controls appear and disappear
* refresh button visibility on player layout changes
* preserve single-tap reload and the single Twitch Enhancement bundle

# [1.9.3-beta.4.2](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.3-beta.4.2) (2026-10-09)

### Player Controls

* bundle Reload Stream inside Twitch Enhancement instead of exposing a separate universal patch
* make one tap reload the current live stream immediately
* keep the reload button in player controls immediately to the left of Mute
* retain the existing Player Controls setting and Twitch 31.3.1 compatibility

# [1.9.3-beta.3.3](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.3-beta.3.3) (2026-10-09)

### Player Controls

* enforce Hide Live Share and Hide Cast through the APK-verified player-header state path
* re-apply Hide Cast through the APK-verified Chromecast setup path
* retain the exact Twitch 31.3.1 resource and obfuscated-field verification

# [1.9.2-rc.1](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-rc.1) (2026-10-06)

### Release candidate

* promote the verified Beta25 build to the 1.9.2 release candidate
* keep Hide Go Ad-Free, Hide Continue Watching, and Hide Offline Channels reversible and working
* preserve the stable Twitch 31.3.1 emote, Channel Points, chat, deleted-message, and other existing features
* no new unverified Twitch fingerprints introduced

