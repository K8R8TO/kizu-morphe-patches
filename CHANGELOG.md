# [1.9.2-beta.6](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.6) (2026-10-05)

### Features

* add exact Twitch 31.3.1 player-overlay hooks for Cast, Create Clip, and Live Share
* add the verified Twitch 31.3.1 external-link disclaimer bypass
* retain the existing Go Ad-Free/Turbo Following promo hook

### Fixes

* keep the unsafe global Home cleanup hook out of the Twitch Enhancement dependency graph
* leave Continue Watching and Offline Channels disabled until their exact feed hooks are verified

# [1.9.2-beta.5](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.5) (2026-10-05)

### Fixes

* remove the Tier-1 Home/navigation global launch hook that caused Twitch to crash during startup
* restore the launch-safe stable Twitch Enhancement dependency graph

# [1.9.2-beta.4](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.4) (2026-10-05)

### Features

* implement Tier-1 Home/navigation controls from the supplied Twitch 31.3.1 APKM
