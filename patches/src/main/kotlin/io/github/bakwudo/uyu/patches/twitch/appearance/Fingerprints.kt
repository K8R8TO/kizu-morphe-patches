package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
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

/**
 * Exact Twitch 31.3.1 player-overlay constructor.
 *
 * The supplied APKM contains exactly one create_clip_button_compose_view resource
 * lookup in this constructor. That lookup is stored into Lout.j, whose field type
 * is ComposeView.
 */
private const val CREATE_CLIP_BUTTON_RESOURCE_ID = 0x7f0b05f0

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

        if (constants.size != 1) return@Fingerprint false
        val constantIndex = constants.single().index

        instructions.drop(constantIndex + 1).take(16).indices.any { relative ->
            val index = constantIndex + 1 + relative
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
