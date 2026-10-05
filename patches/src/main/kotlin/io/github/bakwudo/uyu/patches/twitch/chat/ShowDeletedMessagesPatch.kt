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

        val originalRegister = findKizuFreeRegister(
            formatter,
            injectionIndex,
            spanArrayRegister,
            deletedSpanRegister,
        )

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


private fun findKizuFreeRegister(
    method: com.android.tools.smali.dexlib2.iface.Method,
    startIndex: Int,
    vararg registersToExclude: Int,
): Int {
    val excluded = registersToExclude.toSet()
    val used = excluded.toMutableSet()

    for (index in startIndex until method.implementation!!.instructions.count()) {
        val instruction = method.getInstruction(index)
        val registers = kizuRegistersUsed(instruction)
        val writeRegister = kizuWriteRegister(instruction)

        if (writeRegister != null &&
            writeRegister < 16 &&
            writeRegister !in used &&
            registers.count { it == writeRegister } == 1
        ) {
            return writeRegister
        }

        used.addAll(registers)

        if (instruction.opcode in kizuReturnOpcodes) {
            return (0 until method.implementation!!.registerCount)
                .firstOrNull { it < 16 && it !in used }
                ?: throw PatchException(
                    "Twitch deleted messages: no free 4-bit register was found."
                )
        }

        if (instruction.opcode in kizuConditionalBranchOpcodes ||
            instruction.opcode in kizuUnconditionalBranchOpcodes
        ) {
            throw PatchException(
                "Twitch deleted messages: could not allocate a safe temporary register before a branch."
            )
        }
    }

    throw PatchException("Twitch deleted messages: no safe temporary register was found.")
}

private fun kizuRegistersUsed(
    instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction,
): List<Int> = when (instruction) {
    is com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction -> when (instruction.registerCount) {
        0 -> emptyList()
        1 -> listOf(instruction.registerC)
        2 -> listOf(instruction.registerC, instruction.registerD)
        3 -> listOf(instruction.registerC, instruction.registerD, instruction.registerE)
        4 -> listOf(
            instruction.registerC,
            instruction.registerD,
            instruction.registerE,
            instruction.registerF,
        )
        else -> listOf(
            instruction.registerC,
            instruction.registerD,
            instruction.registerE,
            instruction.registerF,
            instruction.registerG,
        )
    }
    is com.android.tools.smali.dexlib2.iface.instruction.FourRegisterInstruction -> listOf(
        instruction.registerA,
        instruction.registerB,
        instruction.registerC,
        instruction.registerD,
    )
    is com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction -> listOf(
        instruction.registerA,
        instruction.registerB,
        instruction.registerC,
    )
    is com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction -> listOf(
        instruction.registerA,
        instruction.registerB,
    )
    is com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction -> listOf(instruction.registerA)
    is com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction ->
        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
    else -> emptyList()
}

private fun kizuWriteRegister(
    instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction,
): Int? = if (instruction.opcode in kizuWriteOpcodes &&
    instruction is com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
) {
    instruction.registerA
} else {
    null
}

private val kizuReturnOpcodes = setOf(
    Opcode.RETURN_VOID,
    Opcode.RETURN,
    Opcode.RETURN_WIDE,
    Opcode.RETURN_OBJECT,
    Opcode.THROW,
)

private val kizuConditionalBranchOpcodes = setOf(
    Opcode.IF_EQ,
    Opcode.IF_NE,
    Opcode.IF_LT,
    Opcode.IF_GE,
    Opcode.IF_GT,
    Opcode.IF_LE,
    Opcode.IF_EQZ,
    Opcode.IF_NEZ,
    Opcode.IF_LTZ,
    Opcode.IF_GEZ,
    Opcode.IF_GTZ,
    Opcode.IF_LEZ,
)

private val kizuUnconditionalBranchOpcodes = setOf(
    Opcode.GOTO,
    Opcode.GOTO_16,
    Opcode.GOTO_32,
)

private val kizuWriteOpcodes = setOf(
    Opcode.ARRAY_LENGTH,
    Opcode.INSTANCE_OF,
    Opcode.NEW_INSTANCE,
    Opcode.NEW_ARRAY,
    Opcode.MOVE,
    Opcode.MOVE_FROM16,
    Opcode.MOVE_16,
    Opcode.MOVE_WIDE,
    Opcode.MOVE_WIDE_FROM16,
    Opcode.MOVE_WIDE_16,
    Opcode.MOVE_OBJECT,
    Opcode.MOVE_OBJECT_FROM16,
    Opcode.MOVE_OBJECT_16,
    Opcode.MOVE_RESULT,
    Opcode.MOVE_RESULT_WIDE,
    Opcode.MOVE_RESULT_OBJECT,
    Opcode.MOVE_EXCEPTION,
    Opcode.CONST,
    Opcode.CONST_4,
    Opcode.CONST_16,
    Opcode.CONST_HIGH16,
    Opcode.CONST_WIDE_16,
    Opcode.CONST_WIDE_32,
    Opcode.CONST_WIDE,
    Opcode.CONST_WIDE_HIGH16,
    Opcode.CONST_STRING,
    Opcode.CONST_STRING_JUMBO,
    Opcode.CONST_CLASS,
    Opcode.IGET,
    Opcode.IGET_WIDE,
    Opcode.IGET_OBJECT,
    Opcode.IGET_BOOLEAN,
    Opcode.IGET_BYTE,
    Opcode.IGET_CHAR,
    Opcode.IGET_SHORT,
    Opcode.IGET_VOLATILE,
    Opcode.IGET_WIDE_VOLATILE,
    Opcode.IGET_OBJECT_VOLATILE,
    Opcode.SGET,
    Opcode.SGET_WIDE,
    Opcode.SGET_OBJECT,
    Opcode.SGET_BOOLEAN,
    Opcode.SGET_BYTE,
    Opcode.SGET_CHAR,
    Opcode.SGET_SHORT,
    Opcode.SGET_VOLATILE,
    Opcode.SGET_WIDE_VOLATILE,
    Opcode.SGET_OBJECT_VOLATILE,
    Opcode.AGET,
    Opcode.AGET_WIDE,
    Opcode.AGET_OBJECT,
    Opcode.AGET_BOOLEAN,
    Opcode.AGET_BYTE,
    Opcode.AGET_CHAR,
    Opcode.AGET_SHORT,
    Opcode.ADD_DOUBLE_2ADDR,
    Opcode.ADD_DOUBLE,
    Opcode.ADD_FLOAT_2ADDR,
    Opcode.ADD_FLOAT,
    Opcode.ADD_INT_2ADDR,
    Opcode.ADD_INT_LIT8,
    Opcode.ADD_INT,
    Opcode.ADD_LONG_2ADDR,
    Opcode.ADD_LONG,
    Opcode.ADD_INT_LIT16,
    Opcode.AND_INT_2ADDR,
    Opcode.AND_INT_LIT8,
    Opcode.AND_INT_LIT16,
    Opcode.AND_INT,
    Opcode.AND_LONG_2ADDR,
    Opcode.AND_LONG,
    Opcode.DIV_DOUBLE_2ADDR,
    Opcode.DIV_DOUBLE,
    Opcode.DIV_FLOAT_2ADDR,
    Opcode.DIV_FLOAT,
    Opcode.DIV_INT_2ADDR,
    Opcode.DIV_INT_LIT16,
    Opcode.DIV_INT_LIT8,
    Opcode.DIV_INT,
    Opcode.DIV_LONG_2ADDR,
    Opcode.DIV_LONG,
    Opcode.DOUBLE_TO_FLOAT,
    Opcode.DOUBLE_TO_INT,
    Opcode.DOUBLE_TO_LONG,
    Opcode.FLOAT_TO_DOUBLE,
    Opcode.FLOAT_TO_INT,
    Opcode.FLOAT_TO_LONG,
    Opcode.INT_TO_BYTE,
    Opcode.INT_TO_CHAR,
    Opcode.INT_TO_DOUBLE,
    Opcode.INT_TO_FLOAT,
    Opcode.INT_TO_LONG,
    Opcode.INT_TO_SHORT,
    Opcode.LONG_TO_DOUBLE,
    Opcode.LONG_TO_FLOAT,
    Opcode.LONG_TO_INT,
    Opcode.MUL_DOUBLE_2ADDR,
    Opcode.MUL_DOUBLE,
    Opcode.MUL_FLOAT_2ADDR,
    Opcode.MUL_FLOAT,
    Opcode.MUL_INT_2ADDR,
    Opcode.MUL_INT_LIT16,
    Opcode.MUL_INT_LIT8,
    Opcode.MUL_INT,
    Opcode.MUL_LONG_2ADDR,
    Opcode.MUL_LONG,
    Opcode.NEG_DOUBLE,
    Opcode.NEG_FLOAT,
    Opcode.NEG_INT,
    Opcode.NEG_LONG,
    Opcode.NOT_INT,
    Opcode.NOT_LONG,
    Opcode.OR_INT_2ADDR,
    Opcode.OR_INT_LIT16,
    Opcode.OR_INT_LIT8,
    Opcode.OR_INT,
    Opcode.OR_LONG_2ADDR,
    Opcode.OR_LONG,
    Opcode.REM_DOUBLE_2ADDR,
    Opcode.REM_DOUBLE,
    Opcode.REM_FLOAT_2ADDR,
    Opcode.REM_FLOAT,
    Opcode.REM_INT_2ADDR,
    Opcode.REM_INT_LIT16,
    Opcode.REM_INT_LIT8,
    Opcode.REM_INT,
    Opcode.REM_LONG_2ADDR,
    Opcode.REM_LONG,
    Opcode.RSUB_INT_LIT8,
    Opcode.RSUB_INT,
    Opcode.SHL_INT_2ADDR,
    Opcode.SHL_INT_LIT8,
    Opcode.SHL_INT,
    Opcode.SHL_LONG_2ADDR,
    Opcode.SHL_LONG,
    Opcode.SHR_INT_2ADDR,
    Opcode.SHR_INT_LIT8,
    Opcode.SHR_INT,
    Opcode.SHR_LONG_2ADDR,
    Opcode.SHR_LONG,
    Opcode.SUB_DOUBLE_2ADDR,
    Opcode.SUB_DOUBLE,
    Opcode.SUB_FLOAT_2ADDR,
    Opcode.SUB_FLOAT,
    Opcode.SUB_INT_2ADDR,
    Opcode.SUB_INT,
    Opcode.SUB_LONG_2ADDR,
    Opcode.SUB_LONG,
    Opcode.USHR_INT_2ADDR,
    Opcode.USHR_INT_LIT8,
    Opcode.USHR_INT,
    Opcode.USHR_LONG_2ADDR,
    Opcode.USHR_LONG,
    Opcode.XOR_INT_2ADDR,
    Opcode.XOR_INT_LIT16,
    Opcode.XOR_INT_LIT8,
    Opcode.XOR_INT,
    Opcode.XOR_LONG_2ADDR,
    Opcode.XOR_LONG,
)
