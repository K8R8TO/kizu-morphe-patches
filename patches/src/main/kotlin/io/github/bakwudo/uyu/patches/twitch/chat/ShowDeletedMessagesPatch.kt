package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"
private const val SPANNED_STRING = "Landroid/text/SpannedString;"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)

        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException("Twitch deleted messages: access flag field was not found uniquely.")

        val originalMessageField = spanClass.fields.singleOrNull { field ->
            field.type == SPANNED_STRING
        } ?: throw PatchException("Twitch deleted messages: original-message field was not found uniquely.")

        val constructor = DeletedMessageSpanCtorFingerprint.method
        constructor.addInstructions(
            constructor.instructions.lastIndex,
            """
                invoke-static {p3}, $SUPPORT->resolveAccess(Z)Z
                move-result p3
                iput-boolean p3, p0, $accessField
            """,
        )

        val formatter = DeletedMessageFormatterFingerprint.method
        val formatterInstructions = formatter.instructions

        val getSpansIndex = formatterInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == SPANNED_STRING &&
                reference.name == "getSpans" &&
                reference.returnType == "[Ljava/lang/Object;"
        }

        if (getSpansIndex < 0) {
            throw PatchException("Twitch deleted messages: formatter getSpans call was not found.")
        }

        val getSpans = formatterInstructions[getSpansIndex] as? FiveRegisterInstruction
            ?: throw PatchException("Twitch deleted messages: formatter getSpans invocation is not five-register form.")

        if (getSpans.registerCount != 4) {
            throw PatchException(
                "Twitch deleted messages: formatter getSpans expected 4 arguments, found ${getSpans.registerCount}.",
            )
        }

        val moveResultIndex = formatterInstructions.indices.firstOrNull { index ->
            index > getSpansIndex && formatterInstructions[index].opcode == Opcode.MOVE_RESULT_OBJECT
        } ?: throw PatchException(
            "Twitch deleted messages: formatter getSpans move-result-object was not found.",
        )

        val spanArrayRegister =
            formatter.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

        val checkCastIndex = formatterInstructions.indices.firstOrNull { index ->
            index > moveResultIndex && formatterInstructions[index].opcode == Opcode.CHECK_CAST
        } ?: throw PatchException(
            "Twitch deleted messages: formatter deleted-span array check-cast was not found.",
        )

        val injectionIndex = formatterInstructions.indices.firstOrNull { index ->
            index > checkCastIndex && formatterInstructions[index].opcode == Opcode.ARRAY_LENGTH
        } ?: throw PatchException(
            "Twitch deleted messages: formatter array-length check was not found.",
        )

        val deletedSpanRegister = getSpans.registerF

        val originalRegister = formatter
            .getFreeRegisterProvider(
                injectionIndex,
                1,
                listOf(spanArrayRegister, deletedSpanRegister),
            )
            .getFreeRegister()

        if (deletedSpanRegister > 15 || originalRegister > 15) {
            throw PatchException(
                "Twitch deleted messages: formatter temporary register is outside 35c range " +
                    "(deletedSpan=v${deletedSpanRegister}, original=v${originalRegister}).",
            )
        }

        formatter.addInstructions(
            injectionIndex,
            """
                const/4 v$deletedSpanRegister, 0x0
                aget-object v$deletedSpanRegister, v$spanArrayRegister, v$deletedSpanRegister
                iget-object v$originalRegister, v$deletedSpanRegister, $originalMessageField
                invoke-static {p1, v$deletedSpanRegister, v$originalRegister}, $SUPPORT->recoverDeletedMessage(Landroid/text/SpannedString;Landroid/text/style/ClickableSpan;Landroid/text/SpannedString;)Landroid/text/SpannedString;
                move-result-object v$originalRegister
                if-eqz v$originalRegister, :kizu_deleted_messages_original
                return-object v$originalRegister
                :kizu_deleted_messages_original
            """,
        )
    }
}
