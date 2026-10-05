package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"
private const val SPANNED_STRING = "Landroid/text/SpannedString;"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)
        val spanType = spanClass.type

        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException(
                "Twitch deleted messages: access flag field was not found uniquely.",
            )

        val originalMessageField = spanClass.fields.singleOrNull { field ->
            field.type == SPANNED_STRING
        } ?: throw PatchException(
            "Twitch deleted messages: original-message field was not found uniquely.",
        )

        // Keep Twitch's deleted span accessible to the formatter. The original
        // access value is preserved whenever this setting is disabled.
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

        // Twitch's formatter first calls SpannedString.getSpans() to locate its
        // deleted-message ClickableSpan. Find the result register dynamically;
        // do not assume v0/v2 because R8 register allocation can differ.
        val getSpansIndex = formatterInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == SPANNED_STRING &&
                reference.name == "getSpans" &&
                reference.returnType == "[Ljava/lang/Object;"
        }

        if (getSpansIndex < 0) {
            throw PatchException(
                "Twitch deleted messages: formatter getSpans call was not found.",
            )
        }

        val getSpans = formatterInstructions[getSpansIndex] as? FiveRegisterInstruction
            ?: throw PatchException(
                "Twitch deleted messages: formatter getSpans invocation is not five-register form.",
            )

        // For an instance getSpans(receiver, start, end, class), C is receiver.
        // We only need the result array and intentionally reset the scratch/index
        // register ourselves, so no fixed start-register assumption is needed.
        val moveResultIndex = formatterInstructions.indices.firstOrNull { index ->
            index > getSpansIndex &&
                formatterInstructions[index].opcode == Opcode.MOVE_RESULT_OBJECT
        } ?: throw PatchException(
            "Twitch deleted messages: getSpans move-result-object was not found.",
        )

        val arrayRegister =
            formatter.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

        val checkCastIndex = formatterInstructions.indices.firstOrNull { index ->
            index > moveResultIndex && formatterInstructions[index].opcode == Opcode.CHECK_CAST &&
                ((formatterInstructions[index] as? ReferenceInstruction)?.reference as? TypeReference)?.type ==
                    "[$spanType"
        } ?: throw PatchException(
            "Twitch deleted messages: deleted-span array check-cast was not found.",
        )

        val arrayLengthIndex = formatterInstructions.indices.firstOrNull { index ->
            index > checkCastIndex && formatterInstructions[index].opcode == Opcode.ARRAY_LENGTH
        } ?: throw PatchException(
            "Twitch deleted messages: existing-span array check was not found.",
        )

        // Twitch loads the deleted span class with const-class before the
        // array-length check. That register is intentionally reused by the
        // original proven recovery pattern as scratch.
        val scratchRegister =
            formatterInstructions.take(arrayLengthIndex).asReversed().firstOrNull { instruction ->
                instruction.opcode == Opcode.CONST_CLASS &&
                    ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == spanType
            }?.let { (it as OneRegisterInstruction).registerA }
                ?: throw PatchException(
                    "Twitch deleted messages: deleted-span class scratch register was not found.",
                )

        if (scratchRegister == arrayRegister) {
            throw PatchException(
                "Twitch deleted messages: formatter scratch register overlaps deleted-span array.",
            )
        }

        formatter.addInstructions(
            arrayLengthIndex,
            """
                invoke-static {}, $SUPPORT->shouldRecover()Z
                move-result v$scratchRegister
                if-eqz v$scratchRegister, :kizu_deleted_messages_original
                array-length v$scratchRegister, v$arrayRegister
                if-eqz v$scratchRegister, :kizu_deleted_messages_original
                const/4 v$scratchRegister, 0x0
                aget-object v$scratchRegister, v$arrayRegister, v$scratchRegister
                iget-object p1, v$scratchRegister, $originalMessageField
                invoke-static {p1}, $SUPPORT->prepareRecovered($SPANNED_STRING)$SPANNED_STRING
                move-result-object p1
                const/4 p4, 0x1
                const/4 v$scratchRegister, 0x0
                new-array v$arrayRegister, v$scratchRegister, [$spanType
                :kizu_deleted_messages_original
            """,
        )
    }
}
