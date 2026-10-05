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

        if (getSpansIndex < 0) throw PatchException("Twitch deleted messages: formatter getSpans call was not found.")

        val getSpans = formatterInstructions[getSpansIndex] as? FiveRegisterInstruction
            ?: throw PatchException("Twitch deleted messages: formatter getSpans invocation is not five-register form.")

        val spanArrayRegister = formatter.getInstruction<OneRegisterInstruction>(getSpansIndex + 1).registerA
        val checkCastIndex = getSpansIndex + 2
        if (formatterInstructions[checkCastIndex].opcode != Opcode.CHECK_CAST) {
            throw PatchException("Twitch deleted messages: formatter deleted-span array check-cast was not found.")
        }

        val injectionIndex = formatterInstructions.indices.firstOrNull { index ->
            index > checkCastIndex && formatterInstructions[index].opcode == Opcode.ARRAY_LENGTH
        } ?: throw PatchException("Twitch deleted messages: formatter array-length check was not found.")

        if (spanArrayRegister != 0 || getSpans.registerC != 2) {
            throw PatchException("Twitch deleted messages: unexpected 31.3.1 formatter register layout.")
        }

        formatter.addInstructions(
            injectionIndex,
            """
                array-length v3, v0
                if-eqz v3, :kizu_deleted_messages_original
                aget-object v1, v0, v2
                iget-object v5, v1, $originalMessageField
                invoke-virtual {p1, v1}, $SPANNED_STRING->getSpanStart(Ljava/lang/Object;)I
                move-result v3
                invoke-virtual {p1, v1}, $SPANNED_STRING->getSpanEnd(Ljava/lang/Object;)I
                move-result v4
                invoke-static {p1, v1, v5, v3, v4}, $SUPPORT->recoverDeletedMessage(Landroid/text/SpannedString;Landroid/text/style/ClickableSpan;Landroid/text/SpannedString;II)Landroid/text/SpannedString;
                move-result-object v5
                if-eqz v5, :kizu_deleted_messages_original
                return-object v5
                :kizu_deleted_messages_original
            """,
        )
    }
}