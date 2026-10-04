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

internal val showDeletedMessagesPatch = bytecodePatch(
    name = "Show deleted messages",
    description = "Keeps deleted or moderated chat messages readable instead of replacing them with " +
        "\"<message deleted>\". The message remains a tappable spoiler that reveals the original text.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)
        val spanType = spanClass.type

        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException(
                "Twitch deleted messages: access flag field was not found uniquely.",
            )

        val originalMessageField = spanClass.fields.singleOrNull { field ->
            field.type == "Landroid/text/SpannedString;"
        } ?: throw PatchException(
            "Twitch deleted messages: original-message field was not found uniquely.",
        )

        val constructor = DeletedMessageSpanCtorFingerprint.method
        constructor.addInstructions(
            constructor.instructions.lastIndex,
            """
                const/4 p3, 0x1
                iput-boolean p3, p0, $accessField
            """,
        )

        val formatter = DeletedMessageFormatterFingerprint.method
        val formatterInstructions = formatter.instructions

        val getSpansIndex = formatterInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Landroid/text/SpannedString;" &&
                reference.name == "getSpans" &&
                reference.returnType == "[Ljava/lang/Object;"
        }

        if (getSpansIndex < 0) {
            throw PatchException(
                "Twitch deleted messages: formatter getSpans call was not found.",
            )
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
            throw PatchException(
                "Twitch deleted messages: formatter scratch registers now overlap.",
            )
        }

        formatter.addInstructionsWithLabels(
            arrayLengthIndex,
            """
                array-length v$scratchRegister, v$arrayRegister
                if-eqz v$scratchRegister, :use_stock_array
                const/4 v$scratchRegister, 0x0
                aget-object v$scratchRegister, v$arrayRegister, v$scratchRegister
                iget-object p1, v$scratchRegister, $originalMessageField
                const/4 p4, 0x1
                const/4 v$scratchRegister, 0x0
                new-array v$arrayRegister, v$scratchRegister, [$spanType
            """,
            ExternalLabel("use_stock_array", formatter.getInstruction(arrayLengthIndex)),
        )

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
                if (register > 0xf) {
                    throw PatchException(
                        "Twitch deleted messages: access-flag register no longer fits const/4.",
                    )
                }
                method.addInstructions(index + 1, "const/4 v$register, 0x1")
            }
        }
    }
}
