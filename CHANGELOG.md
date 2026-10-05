# [1.9.2-beta.10](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.10) (2026-10-05)

### Fixes

* hook the exact Twitch 31.3.1 Following-header binder containing the unique Go Ad-Free resource constant 0x7f0b0942
* bind the Go Ad-Free button directly when Twitch inflates the Following-header item
* expose Hide Go Ad-Free at the Kizu settings root with the existing default-ON setting


# [1.9.2-beta.8](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.8) (2026-10-05)

### Fixes

* fix Hide Go Ad-Free/Turbo in Twitch's Following feed by re-scanning the verified Twitch 31.3.1 Turbo resource IDs after delayed feed inflation
* keep the beta8 change isolated to the existing promotion patch; no new player, link-disclaimer, Continue Watching, or Offline Channels hooks are added

# [1.9.2-beta.7](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.7) (2026-10-05)

### Fixes

* remove the newly introduced player-overlay and link-disclaimer hooks from the release path after isolating them as the remaining unverified beta6 changes
* restore the known-stable Twitch Enhancement dependency graph so the beta7 build does not carry the beta4/beta6 crash-prone hooks
* keep Continue Watching and Offline Channels disabled until exact Twitch 31.3.1 feed fingerprints are verified
* trigger the beta7 release workflow from the beta branch merge

# [1.9.2-beta.6](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.6) (2026-10-05)

### Features

* add exact Twitch 31.3.1 player-overlay hooks for Cast, Create Clip, and Live Share
* add the verified Twitch 31.3.1 external-link disclaimer bypass
* retain the existing Go Ad-Free/Turbo Following promo hook

### Fixes

* keep the unsafe global Home cleanup hook out of the Twitch Enhancement dependency graph
* leave Continue Watching and Offline Channels disabled until their exact feed hooks are verified

# [1.9.2-beta.5](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.5)

### Fixes

* remove the Tier-1 Home/navigation global launch hook that caused Twitch to crash during startup
* restore the launch-safe stable Twitch Enhancement dependency graph

