package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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

/** Exact APK-derived resource -> Lout field verification. */
private const val CREATE_CLIP_BUTTON_RESOURCE_ID = 0x7f0b05f0
private const val PLAYER_SHARE_RESOURCE_ID = 0x7f0b128a
private const val PLAYER_CAST_RESOURCE_ID = 0x7f0b0c0f

private fun hasResourceBackedField(
    instructions: List<Instruction>,
    resourceId: Int,
    fieldName: String,
    fieldType: String,
): Boolean {
    val constants = instructions.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.CONST &&
            instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
            instruction.narrowLiteral == resourceId
    }
    if (constants.size != 1) return false

    val constantIndex = constants.single().index
    var sawFindViewById = false
    val end = minOf(constantIndex + 24, instructions.size)
    for (index in constantIndex + 1 until end) {
        val instruction = instructions[index]
        val reference = (instruction as? ReferenceInstruction)?.reference
        val methodReference = reference as? MethodReference
        if (instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            methodReference?.definingClass == "Landroid/view/View;" &&
            methodReference.name == "findViewById" &&
            methodReference.parameterTypes.map { it.toString() } == listOf("I") &&
            methodReference.returnType == "Landroid/view/View;"
        ) {
            sawFindViewById = true
        }

        val fieldReference = reference as? FieldReference
        if (sawFindViewById && instruction.opcode == Opcode.IPUT_OBJECT &&
            fieldReference?.definingClass == "Lout;" &&
            fieldReference.name == fieldName &&
            fieldReference.type == fieldType
        ) {
            return true
        }
    }
    return false
}

/**
 * Exact Twitch 31.3.1 Lout player-overlay constructor.
 *
 * The supplied APKM was checked for all three player-control resources and their
 * exact obfuscated field destinations before this fingerprint was accepted.
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
    custom = { method, classDef ->
        val maybeInstructions = method.instructionsOrNull?.toList()
        if (maybeInstructions == null) {
            false
        } else {
            val fieldsVerified = listOf(
                "j" to "Landroidx/compose/ui/platform/ComposeView;",
                "k" to "Landroid/widget/ImageView;",
                "q" to "Landroidx/mediarouter/app/MediaRouteButton;",
            ).all { (name, type) ->
                classDef.fields.count { it.name == name && it.type == type } == 1
            }

            fieldsVerified &&
                hasResourceBackedField(maybeInstructions, CREATE_CLIP_BUTTON_RESOURCE_ID, "j", "Landroidx/compose/ui/platform/ComposeView;") &&
                hasResourceBackedField(maybeInstructions, PLAYER_SHARE_RESOURCE_ID, "k", "Landroid/widget/ImageView;") &&
                hasResourceBackedField(maybeInstructions, PLAYER_CAST_RESOURCE_ID, "q", "Landroidx/mediarouter/app/MediaRouteButton;")
        }
    },
)

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
        val instructions = method.instructionsOrNull?.toList()
        if (instructions == null) {
            false
        } else {
            instructions.count { instruction ->
                instruction.opcode == Opcode.CONST &&
                    instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                    instruction.narrowLiteral == CREATE_CLIP_BUTTON_RESOURCE_ID
            } == 1
        }
    },
)

/** 
 * Exact Twitch 31.3.1 constructor that resolves create_clip_text_button.
 *
 * The supplied APKM contains this resource exactly once, in Ld040.<init>.
 * The resolved view is cast to ComposeView and immediately tested for null.
 */
private const val CREATE_CLIP_TEXT_BUTTON_RESOURCE_ID = 0x7f0b05f2

/**
 * Exact Twitch 31.3.1 player-header state method.
 *
 * In the supplied APKM, Llrx.v(ViewDelegateState) is the state/visibility path for the
 * generated player-control binding. It references Lqot.r (ImageView, resource 0x7f0b128a,
 * Share/Live Share) and Lqot.e (MediaRouteButton, resource 0x7f0b0394, Chromecast control)
 * while calling View.setVisibility().
 */
internal object PlayerOverlayHeaderControlsFingerprint : Fingerprint(
    definingClass = "Llrx;",
    name = "v",
    returnType = "V",
    parameters = listOf("Ltv/twitch/android/core/mvp/viewdelegate/ViewDelegateState;"),
    custom = { method, classDef ->
        val instructions = method.instructionsOrNull?.toList() ?: return@Fingerprint false
        val hasDelegateBindingField = classDef.fields.count {
            it.name == "d" && it.type == "Lqot;"
        } == 1

        fun hasQotField(name: String, type: String) = instructions.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            reference?.definingClass == "Lqot;" &&
                reference.name == name &&
                reference.type == type
        }

        fun hasSetVisibility(type: String) = instructions.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference?.name == "setVisibility" &&
                reference?.definingClass == type &&
                reference.parameterTypes.map { it.toString() } == listOf("I") &&
                reference.returnType == "V"
        }

        hasDelegateBindingField &&
            hasQotField("r", "Landroid/widget/ImageView;") &&
            hasQotField("e", "Landroidx/mediarouter/app/MediaRouteButton;") &&
            hasSetVisibility("Landroid/widget/ImageView;") &&
            hasSetVisibility("Landroidx/mediarouter/app/MediaRouteButton;")
    },
)

/**
 * Exact Twitch 31.3.1 Chromecast setup method.
 *
 * The supplied APKM's Lout.z() reads Lout.q (MediaRouteButton) and drives its visibility.
 * It has multiple return paths, so the patch must enforce the setting before every return.
 */
internal object PlayerOverlayChromecastSetupFingerprint : Fingerprint(
    definingClass = "Lout;",
    name = "z",
    returnType = "V",
    parameters = listOf(),
    custom = { method, classDef ->
        val instructions = method.instructionsOrNull?.toList() ?: return@Fingerprint false
        val qField = classDef.fields.count {
            it.name == "q" && it.type == "Landroidx/mediarouter/app/MediaRouteButton;"
        } == 1
        val qLoad = instructions.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            instruction.opcode == Opcode.IGET_OBJECT &&
                reference?.definingClass == "Lout;" &&
                reference.name == "q" &&
                reference.type == "Landroidx/mediarouter/app/MediaRouteButton;"
        }
        val qVisibility = instructions.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference?.definingClass == "Landroidx/mediarouter/app/MediaRouteButton;" &&
                reference.name == "setVisibility" &&
                reference.parameterTypes.map { it.toString() } == listOf("I") &&
                reference.returnType == "V"
        }
        qField && qLoad && qVisibility
    },
)

internal object PlayerClipTextButtonFingerprint : Fingerprint(
    definingClass = "Ld040;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Llp30;",
        "Lew0;",
        "Lylg;",
        "Lql40;",
        "Lwvl;",
        "Lo57;",
        "Lqi70;",
    ),
    custom = { method, _ ->
        val instructions = method.instructionsOrNull?.toList()
        if (instructions == null) {
            false
        } else {
            val constants = instructions.withIndex().filter { (_, instruction) ->
                instruction.opcode == Opcode.CONST &&
                    instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                    instruction.narrowLiteral == CREATE_CLIP_TEXT_BUTTON_RESOURCE_ID
            }

            if (constants.size != 1) {
                false
            } else {
                val constantIndex = constants.single().index
                (constantIndex + 1 until minOf(constantIndex + 17, instructions.size)).any { index ->
                    val instruction = instructions[index]
                    if (instruction.opcode != Opcode.CHECK_CAST ||
                        instruction !is ReferenceInstruction ||
                        (instruction.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference)?.type !=
                            "Landroidx/compose/ui/platform/ComposeView;" ||
                        instruction !is com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
                    ) {
                        return@any false
                    }

                    val register = instruction.registerA
                    instructions.drop(index + 1).take(4).any { next ->
                        next.opcode == Opcode.IF_EQZ &&
                            next is com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction &&
                            next.registerA == register
                    }
                }
            }
        }
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
