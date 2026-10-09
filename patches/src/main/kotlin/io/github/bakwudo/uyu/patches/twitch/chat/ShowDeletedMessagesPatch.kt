package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"
private const val SPANNED_STRING = "Landroid/text/SpannedString;"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)
        val spanType = spanClass.type
        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException("Twitch deleted messages: access flag field was not found uniquely.")
        val originalMessageField = spanClass.fields.singleOrNull { field ->
            field.type == SPANNED_STRING
        } ?: throw PatchException(
            "Twitch deleted messages: original-message field was not found uniquely.",
        )

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

        val arrayLengthIndex = formatterInstructions.indices.firstOrNull { index ->
            index > getSpansIndex && formatterInstructions[index].opcode == Opcode.ARRAY_LENGTH
        } ?: throw PatchException(
            "Twitch deleted messages: existing-span array check was not found.",
        )
        val spanClassRegister = formatterInstructions.take(arrayLengthIndex)
            .lastOrNull { instruction ->
                instruction.opcode == Opcode.CONST_CLASS &&
                    ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == spanType
            } as? OneRegisterInstruction
            ?: throw PatchException(
                "Twitch deleted messages: span class register was not found.",
            )

        val arrayLength = formatter.getInstruction<TwoRegisterInstruction>(arrayLengthIndex)
        val arrayRegister = arrayLength.registerB
        val scratchRegister = spanClassRegister.registerA
        if (arrayRegister == scratchRegister) {
            throw PatchException("Twitch deleted messages: formatter scratch registers now overlap.")
        }

        // Style Twitch's stored original before handing it back to Twitch's own formatter.
        formatter.addInstructionsWithLabels(
            arrayLengthIndex,
            """
                invoke-static {}, $SUPPORT->shouldApplyVisualStyle()Z
                move-result v$scratchRegister
                if-eqz v$scratchRegister, :kizu_deleted_style_stock
                array-length v$scratchRegister, v$arrayRegister
                if-eqz v$scratchRegister, :kizu_deleted_style_stock
                const/4 v$scratchRegister, 0x0
                aget-object v$scratchRegister, v$arrayRegister, v$scratchRegister
                iget-object p1, v$scratchRegister, $originalMessageField
                invoke-static {}, $SUPPORT->markVisualStylePending()V
                const/4 p4, 0x1
                const/4 v$scratchRegister, 0x0
                new-array v$arrayRegister, v$scratchRegister, [$spanType
            """,
            ExternalLabel("kizu_deleted_style_stock", formatter.getInstruction(arrayLengthIndex)),
        )

        // Style the final returned Spanned, after Twitch has finished rebuilding the row.
        val returnObjectIndices = formatter.instructions.indices.filter { index ->
            formatter.instructions[index].opcode == Opcode.RETURN_OBJECT
        }
        if (returnObjectIndices.isEmpty()) {
            throw PatchException("Twitch deleted messages: formatter return-object was not found.")
        }
        returnObjectIndices.sortedDescending().forEach { index ->
            val returnRegister = formatter.getInstruction<OneRegisterInstruction>(index).registerA
            if (returnRegister > 0xf) {
                throw PatchException("Twitch deleted messages: formatter return register does not fit invoke-static.")
            }
            formatter.addInstructions(
                index,
                """
                    invoke-static {v$returnRegister}, $SUPPORT->applyVisualStyleToResult(Landroid/text/Spanned;)Landroid/text/Spanned;
                    move-result-object v$returnRegister
                """,
            )
        }

        formatter.addInstructions(
            0,
            "invoke-static {}, $SUPPORT->clearVisualStylePending()V",
        )

        // Twitch can reset this flag after construction; filter both later reads as well.
        val accessReads = spanClass.methods.flatMap { method ->
            method.instructions.withIndex().mapNotNull { (index, instruction) ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                if (instruction.opcode == Opcode.IGET_BOOLEAN && reference == accessField) {
                    method to index
                } else {
                    null
                }
            }
        }
        if (accessReads.size != 2) {
            throw PatchException(
                "Twitch deleted messages: expected two access-flag reads, found ${accessReads.size}.",
            )
        }
        accessReads.groupBy({ it.first }, { it.second }).forEach { (method, indexes) ->
            indexes.sortedDescending().forEach { index ->
                val register = method.getInstruction<TwoRegisterInstruction>(index).registerA
                method.addInstructions(
                    index + 1,
                    "invoke-static {v" + register + "}, " + SUPPORT +
                        "->resolveAccess(Z)Z\nmove-result v" + register,
                )
            }
        }
    }
}
