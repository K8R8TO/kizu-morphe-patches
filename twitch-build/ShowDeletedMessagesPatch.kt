package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"

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
            field.type == "Landroid/text/SpannedString;"
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

        // Recover the original message from the deleted span before Twitch replaces it with the placeholder.\n        // Keep the control-flow injection in the compiler-compatible form used by 1.9.0.6.\n        formatter.addInstructionsWithLabels(\n            arrayLengthIndex,\n            \"\"\"\n                array-length v$scratchRegister, v$arrayRegister\n                if-eqz v$scratchRegister, :use_stock_array\n                const/4 v$scratchRegister, 0x0\n                aget-object v$scratchRegister, v$arrayRegister, v$scratchRegister\n                iget-object p1, v$scratchRegister, $originalMessageField\n                const/4 p4, 0x1\n                const/4 v$scratchRegister, 0x0\n                new-array v$arrayRegister, v$scratchRegister, [$spanType\n            \"\"\",\n            ExternalLabel(\"use_stock_array\", formatter.getInstruction(arrayLengthIndex)),\n        )

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
                "Twitch deleted messages: expected two access-flag reads, found \${accessReads.size}.",
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
}\n        // Apply the selected deleted-message presentation to every formatter result.\n        // This avoids the parser-sensitive direct-return smali used by 1.9.0.7.\n        val returnIndexes = formatter.instructions.withIndex()\n            .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_OBJECT }\n            .map { it.index }\n            .distinct()\n            .sortedDescending()\n\n        if (returnIndexes.isEmpty()) {\n            throw PatchException(\n                \"Twitch deleted messages: formatter has no return-object instruction.\",\n            )\n        }\n\n        for (index in returnIndexes) {\n            val instruction = formatter.getInstruction<OneRegisterInstruction>(index)\n            val register = instruction.registerA\n            formatter.addInstructions(\n                index,\n                \"invoke-static/range { v$register .. v$register }, $SUPPORT->format(Landroid/text/Spanned;)Landroid/text/Spanned;\\nmove-result-object v$register\",\n            )\n        }\n
