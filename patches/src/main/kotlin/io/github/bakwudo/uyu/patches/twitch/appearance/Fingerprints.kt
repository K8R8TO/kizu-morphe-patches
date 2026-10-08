package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object BaseViewDelegateConstructorFingerprint : Fingerprint(
    definingClass = "Ltv/twitch/android/core/mvp/viewdelegate/BaseViewDelegate;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/view/View;"),
)

internal object CommunityHighlightPresenterFingerprint : Fingerprint(
    strings = listOf("CommunityHighlightPresenter\$UpdateEvent"),
)

internal object AddCommunityHighlightToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("AddCommunityHighlight(model="),
)

internal object SubtemberHighlightTypeFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("subtember"),
    custom = { _, classDef ->
        classDef.superclass != "Ljava/lang/Object;" &&
            classDef.fields.any { AccessFlags.STATIC.isSet(it.accessFlags) && it.type == classDef.type }
    },
)

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

private const val CREATE_CLIP_BUTTON_RESOURCE_ID = 0x7f0b05f0

/**
 * Exact Twitch 31.3.1 player-overlay constructor, with the Create Clip resource lookup
 * verified against the supplied Twitch 31.3.1 APKM.
 */
internal object PlayerOverlayCreateClipFingerprint : Fingerprint(
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
    custom = { method, _ ->
        val instructions = method.instructionsOrNull ?: return@Fingerprint false

        val resourceConstants = instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.CONST &&
                instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                instruction.narrowLiteral == CREATE_CLIP_BUTTON_RESOURCE_ID
        }
        if (resourceConstants.size != 1) return@Fingerprint false

        val resourceRegister = (resourceConstants.single().value
            as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction)?.registerA
            ?: return@Fingerprint false

        val findViewCalls = instructions.withIndex().filter { (index, instruction) ->
            index > resourceConstants.single().index &&
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                instruction is FiveRegisterInstruction &&
                instruction.registerCount == 2 &&
                listOf(instruction.registerC, instruction.registerD).contains(resourceRegister) &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let { reference ->
                    reference.definingClass == "Landroid/view/View;" &&
                        reference.name == "findViewById" &&
                        reference.returnType == "Landroid/view/View;" &&
                        reference.parameterTypes.map { it.toString() } == listOf("I")
                } == true
        }

        findViewCalls.size == 1 && findViewCalls.single().index + 1 < instructions.size &&
            instructions[findViewCalls.single().index + 1].opcode == Opcode.MOVE_RESULT_OBJECT
    },
)

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
 * Exact Twitch 31.3.1 Following-feed builder method found in the supplied APKM.
 *
 * The verified method is Lq1e.l2(Lm2i;Z)V. Its bytecode directly constructs:
 * - Ll2i.<init>(List) -> ResumeWatching
 * - Lj2i.<init>(List) -> OfflineChannels
 *
 * The custom check requires both constructor calls, in addition to the exact method signature.
 */
internal object FollowingContentBuilderFingerprint : Fingerprint(
    definingClass = "Lq1e;",
    name = "l2",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lm2i;", "Z"),
    custom = { method, _ ->
        fun hasConstructor(type: String) =
            method.instructionsOrNull?.any { instruction ->
                if (instruction.opcode != Opcode.INVOKE_DIRECT) return@any false
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == type &&
                    reference.name == "<init>" &&
                    reference.returnType == "V" &&
                    reference.parameterTypes.map { it.toString() } == listOf("Ljava/util/List;")
            } == true

        hasConstructor("Ll2i;") && hasConstructor("Lj2i;")
    },
)

internal object FollowingGoAdFreeButtonFingerprint : Fingerprint(
    definingClass = "Lmx5;",
    name = "a",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lr4;",
    parameters = listOf("Landroid/view/View;"),
    custom = { method, _ ->
        method.instructionsOrNull?.any {
            it.opcode == Opcode.CONST &&
                it is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                it.narrowLiteral == 0x7f0b0942
        } == true
    },
)
