# [1.9.2-beta.23](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.23) (2026-10-06)

### Fixes

* remove the destructive Following section-construction filter
* scan the actual Twitch activity and Following RecyclerView after the feed is attached
* hide Continue Watching and Offline Channels with live conditions that restore them when toggled off
* remove the settings-screen activity recreation that caused visual glitches
* leave Go Ad-Free and all other existing Twitch hooks unchanged

# [1.9.2-beta.22](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.22) (2026-10-06)

### Fixes

* make Home & navigation switches explicitly persist their Boolean values
* rebuild the Twitch activity after changing Continue Watching or Offline Channels so the feed is reconstructed with the new setting
* preserve the verified Twitch 31.3.1 section-construction hook
* keep Go Ad-Free behavior unchanged

# [1.9.2-beta.21](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.21) (2026-10-06)

### Fixes

* restore the verified Twitch 31.3.1 Following-feed builder hook
* apply Hide Continue Watching and Hide Offline Channels at their actual section-model construction point
* retain the reversible view-level settings so disabled toggles can show the sections again after a feed rebuild
* keep all other verified Twitch hooks unchanged

# [1.9.2-beta.20](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.20) (2026-10-06)

### Fixes

* handle the case where the BaseViewDelegate root is not attached when the patch callback runs
* discover the actual window root after attachment and probe the Following recycler at delayed intervals
* keep Continue Watching and Offline Channels reversible through live settings conditions
* keep the exact Twitch 31.3.1 verified bytecode fingerprints unchanged

# [1.9.2-beta.17](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.17) (2026-10-06)

### Fixes

* fix the active `app.morphe.extension` settings implementation used by the Twitch patch
* expose Kizu Home & navigation at the settings root
* add Hide Go Ad-Free, Hide Continue Watching, and Hide Offline Channels to that section
* bind those three controls to the same runtime settings used by the existing Home cleanup hooks
* keep the verified Twitch 31.3.1 bytecode hooks unchanged

# [1.9.2-beta.16](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.16) (2026-10-06)

### Fixes

* fix the actual beta build source used by apply_kizu.py
* expose Kizu Home & navigation in the settings root
* keep Hide Go Ad-Free, Hide Continue Watching, and Hide Offline Channels together
* no Twitch bytecode fingerprints or runtime hooks changed

# [1.9.2-beta.15](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.15) (2026-10-06)

### Fixes

* always expose the Kizu Home & navigation settings section
* keep Hide Go Ad-Free, Hide Continue Watching, and Hide Offline Channels together in that section
* no Twitch bytecode fingerprints or runtime hooks changed


# [1.9.2-beta.14](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.14) (2026-10-06)

### Changes

* add a visible Kizu Home & navigation settings section whenever the Twitch Enhancement patch is applied
* move Hide Go Ad-Free from Appearance into Home & navigation
* expose Hide Continue Watching and Hide Offline Channels in the same section


# [1.9.2-beta.13](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.13) (2026-10-06)

### Fixes

* remove the failed FollowingContentCollectionsBinderFingerprint from Beta12
* use the exact Twitch 31.3.1 Lq1e.l2(Lm2i;Z)V builder method from the supplied APKM
* anchor the builder fingerprint to its verified ResumeWatching and OfflineChannels constructor calls
* clear the exact fresh section lists immediately before those constructors run
* keep the existing Beta11 Go Ad-Free implementation unchanged


# [1.9.2-beta.12](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.12) (2026-10-06)

### Features

* filter Twitch 31.3.1 Following feed collections before rendering
* hide Offline Channels by default
* hide Continue Watching by default
* keep the filtering isolated from the existing Go Ad-Free implementation
* use the exact Following-feed binder diagnostic from the supplied Twitch 31.3.1 APKM rather than guessing an obfuscated class name


# [1.9.2-beta.11](https://github.com/K8R8TO/kizu-morphe-patches/releases/tag/v1.9.2-beta.11) (2026-10-05)

### Fixes

* correct the Twitch 31.3.1 Go Ad-Free binder fingerprint to its actual PUBLIC + FINAL access flags
* verify resource 0x7f0b0942 is exactly following_tab_turbo_button in the supplied Twitch 31.3.1 APKM
* bind and re-scan that exact Following-header root instead of globally scanning the feed
* keep Hide Go Ad-Free defaulted ON and preserve the existing Kizu settings surface


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

