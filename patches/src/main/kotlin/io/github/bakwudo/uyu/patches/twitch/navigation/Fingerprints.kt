package io.github.bakwudo.uyu.patches.twitch.navigation

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

internal const val DISCOVERY_FEED_PAGE_TYPE =
    "Ltv/twitch/android/models/feed/DiscoveryFeedPage;"

internal const val FOLLOWING_PAGE_TYPE =
    "Ltv/twitch/android/models/feed/DiscoveryFeedPage\$FollowingPage;"

/**
 * Twitch 31.3.1 home-page resolver. It returns DiscoveryFeedPage and references
 * FollowingPage.INSTANCE, which makes it the same native decision point used by
 * aeongdesu's patch on Twitch 30.7.2.
 */
internal object DefaultHomePageFingerprint : Fingerprint(
    returnType = DISCOVERY_FEED_PAGE_TYPE,
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            smali = "$FOLLOWING_PAGE_TYPE->INSTANCE:$FOLLOWING_PAGE_TYPE",
        ),
    ),
)
