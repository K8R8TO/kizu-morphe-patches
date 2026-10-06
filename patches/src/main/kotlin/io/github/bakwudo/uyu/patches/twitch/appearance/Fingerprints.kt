package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

/**
 * The base class of every view delegate (a part of a screen with its root view). The class
 * name is not obfuscated.
 */
internal object BaseViewDelegateConstructorFingerprint : Fingerprint(
    definingClass = "Ltv/twitch/android/core/mvp/viewdelegate/BaseViewDelegate;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/view/View;"),
)

/**
 * Twitch's presenter of community highlights, the banners above chat (predictions, hype trains,
 * pinned messages, promotions). Its Kotlin method signature strings are not obfuscated.
 */
internal object CommunityHighlightPresenterFingerprint : Fingerprint(
    strings = listOf("CommunityHighlightPresenter\$UpdateEvent"),
)

/** toString of the event that adds a community highlight. */
internal object AddCommunityHighlightToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("AddCommunityHighlight(model="),
)

/**
 * The type of the SUBtember community highlight: a singleton created with the id "subtember".
 * Its superclass is the base class of all highlight types, which holds the id.
 */
internal object SubtemberHighlightTypeFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("subtember"),
    custom = { _, classDef ->
        classDef.superclass != "Ljava/lang/Object;" &&
            classDef.fields.any { AccessFlags.STATIC.isSet(it.accessFlags) && it.type == classDef.type }
    },
)

/**
 * Exact Twitch 31.3.1 player overlay constructor. Its fields are populated from:
 * create_clip_button_compose_view -> j, share_button -> k, media_route_button -> q.
 */
internal object PlayerOverlayConstructorFingerprint : Fingerprint(
    definingClass = "Lout;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Landroid/view/View;",
        "Lo57;",
        "Lylg;",
        "Lxks;",
        "Lh7a;",
    ),
)

/**
 * Exact external-link disclaimer method verified in Twitch 31.3.1.
 * Loy3.d(Fragment, Uri, boolean, callback, boolean) constructs the Twitch warning.
 */
internal object BrowserRouterDisclaimerFingerprint : Fingerprint(
    definingClass = "Loy3;",
    name = "d",
    returnType = "V",
    parameters = listOf(
        "Landroidx/fragment/app/n;",
        "Landroid/net/Uri;",
        "Z",
        "Lsii;",
        "Z",
    ),
    strings = listOf("twitch.tv", "twitch.a2z.com", "targetUrl"),
)


/**
 * Exact Twitch 31.3.1 Following-feed collection binder. The method is identified by the
 * unique unsupported-item diagnostic emitted by DiscoveryFeedFollowingPageListAdapter and
 * its List,Boolean,Boolean signature. It receives the complete Following section collection
 * before the adapter renders it, which lets the extension remove OfflineChannels and ResumeWatching
 * without touching individual channel cards.
 */
internal object FollowingContentCollectionsBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Z",
        "Z",
    ),
    strings = listOf("Unsupported item javaClass in DiscoveryFeedFollowingPageListAdapter"),
)
/**
 * Exact Twitch 31.3.1 Following-tab header binder. The supplied APKM contains one occurrence
 * of the Go Ad-Free button resource id (0x7f0b0942 = following_tab_turbo_button)
 * in Lmx5.a(View):Lr4;. The method is public final (access flags 0x11).
 */
internal object FollowingGoAdFreeButtonFingerprint : Fingerprint(
    definingClass = "Lmx5;",
    name = "a",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lr4;",
    parameters = listOf("Landroid/view/View;"),
    custom = { method, _ ->
        method.instructionsOrNull?.any {
            it.opcode == Opcode.CONST &&
                it is NarrowLiteralInstruction &&
                it.narrowLiteral == 0x7f0b0942
        } == true
    },
)
