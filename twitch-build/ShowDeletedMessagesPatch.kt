package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"
private const val SPANNED = "Landroid/text/Spanned;"
private const val SPANNED_STRING = "Landroid/text/SpannedString;"
private const val ROW_BINDER = "Ltv/twitch/android/shared/chat/messages/ui/MessageRecyclerItem\$ViewHolder;"
private const val ROW_BIND_METHOD = "onBindDataItem"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)
        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException("Twitch deleted messages: access flag field was not found uniquely.")

        spanClass.fields.singleOrNull { field -> field.type == SPANNED_STRING }
            ?: throw PatchException("Twitch deleted messages: original-message field was not found uniquely.")

        // Preserve the existing behaviour that lets non-moderators see deleted messages.
        val constructor = DeletedMessageSpanCtorFingerprint.method
        constructor.addInstructions(
            constructor.instructions.lastIndex,
            """
                invoke-static {p3}, $SUPPORT->resolveAccess(Z)Z
                move-result p3
                iput-boolean p3, p0, $accessField
            """,
        )

        fun isDeletedFactory(reference: MethodReference): Boolean {
            val parameters = reference.parameterTypes.map { it.toString() }
            return reference.returnType == SPANNED &&
                parameters.size == 5 &&
                parameters[0] == "Ljava/lang/String;" &&
                (parameters[1] == SPANNED || parameters[1] == SPANNED_STRING) &&
                parameters[2] == "Landroid/content/Context;" &&
                parameters[3] == "Ltv/twitch/android/core/mvp/viewdelegate/EventDispatcher;" &&
                parameters[4] == "Z"
        }

        fun methodSignature(method: Method): String =
            method.name + "(" + method.parameterTypes.joinToString("") { it.toString() } + ")" + method.returnType

        fun collectRegisters(instruction: Instruction, target: MutableSet<Int>) {
            when (instruction) {
                is RegisterRangeInstruction -> {
                    for (register in instruction.startRegister until instruction.startRegister + instruction.registerCount) {
                        target.add(register)
                    }
                }
                is FiveRegisterInstruction -> {
                    val registers = listOf(
                        instruction.registerC,
                        instruction.registerD,
                        instruction.registerE,
                        instruction.registerF,
                        instruction.registerG,
                    )
                    target.addAll(registers.take(instruction.registerCount))
                }
                is ThreeRegisterInstruction -> {
                    target.add(instruction.registerA)
                    target.add(instruction.registerB)
                    target.add(instruction.registerC)
                }
                is TwoRegisterInstruction -> {
                    target.add(instruction.registerA)
                    target.add(instruction.registerB)
                }
                is OneRegisterInstruction -> target.add(instruction.registerA)
            }
        }

        fun messageRegister(instruction: Instruction): Int {
            val isStatic = instruction.opcode == Opcode.INVOKE_STATIC ||
                instruction.opcode == Opcode.INVOKE_STATIC_RANGE

            if (instruction is FiveRegisterInstruction) {
                if (!isStatic || instruction.registerCount != 5) {
                    throw PatchException("Kizu deleted messages: unexpected five-register factory call layout.")
                }
                // Static arguments: message ID, original Spanned, Context, dispatcher, mod-access.
                return instruction.registerD
            }

            if (instruction is RegisterRangeInstruction) {
                val expectedCount = if (isStatic) 5 else 6
                if (instruction.registerCount != expectedCount) {
                    throw PatchException(
                        "Kizu deleted messages: unexpected factory argument count ${instruction.registerCount}.",
                    )
                }
                // An instance call has its receiver before the five declared parameters.
                return instruction.startRegister + if (isStatic) 1 else 2
            }

            throw PatchException("Kizu deleted messages: unsupported deleted-message factory invoke format.")
        }

        data class CallSite(
            val classType: String,
            val methodSignature: String,
            val callIndex: Int,
            val sourceRegister: Int,
            val resultRegister: Int,
            val scratchRegister: Int,
        )

        val callSites = mutableListOf<CallSite>()

        // PurpleTV handles the message in MessageRecyclerItem.ViewHolder.onBindDataItem,
        // where the original Spanned object is an argument to the native deleted-message factory.
        classDefForEach { classDef ->
            if (classDef.type != ROW_BINDER) return@classDefForEach

            for (method in classDef.methods) {
                if (method.name != ROW_BIND_METHOD || method.returnType != "V") continue
                val implementation = method.implementation ?: continue
                val instructions = implementation.instructions.toList()

                for ((index, instruction) in instructions.withIndex()) {
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: continue
                    if (!isDeletedFactory(reference)) continue

                    val moveResultIndex = index + 1
                    val moveResult = instructions.getOrNull(moveResultIndex)
                    if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                        throw PatchException("Kizu deleted messages: row factory call has no immediate move-result-object.")
                    }
                    val resultRegister = (moveResult as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("Kizu deleted messages: row factory result register was not found.")
                    val sourceRegister = messageRegister(instruction)

                    val callRegisters = mutableSetOf<Int>()
                    collectRegisters(instruction, callRegisters)
                    val liveAfter = mutableSetOf<Int>()
                    for (suffixIndex in moveResultIndex + 1 until instructions.size) {
                        collectRegisters(instructions[suffixIndex], liveAfter)
                    }

                    // The native call may reuse the message register for its result. Save the
                    // original input before that call, in a dead non-argument register.
                    val scratchRegister = (0 until implementation.registerCount).firstOrNull { register ->
                        register <= 255 &&
                            register != sourceRegister &&
                            register != resultRegister &&
                            register !in callRegisters &&
                            register !in liveAfter
                    } ?: throw PatchException(
                        "Kizu deleted messages: no safe scratch register is available at the row factory call.",
                    )

                    callSites += CallSite(
                        classDef.type,
                        methodSignature(method),
                        index,
                        sourceRegister,
                        resultRegister,
                        scratchRegister,
                    )
                }
            }
        }

        if (callSites.size != 1) {
            throw PatchException(
                "Kizu deleted messages: expected exactly one native factory call in MessageRecyclerItem row binding, found ${callSites.size}.",
            )
        }

        callSites.sortedByDescending { it.callIndex }.forEachIndexed { id, site ->
            val callerClass = mutableClassDefBy(site.classType)
            val caller = callerClass.methods.singleOrNull {
                methodSignature(it) == site.methodSignature
            } ?: throw PatchException("Kizu deleted messages: row-binding caller method disappeared.")

            // Use the styled original message when a custom appearance is selected. A null
            // helper result preserves Twitch's own formatter output (Mod style / feature off).
            caller.addInstructionsWithLabels(
                site.callIndex + 2,
                """
                    invoke-static/range {v${site.scratchRegister} .. v${site.scratchRegister}}, $SUPPORT->styleDeletedMessageFromRow(Landroid/text/Spanned;)Landroid/text/Spanned;
                    move-result-object v${site.scratchRegister}
                    if-eqz v${site.scratchRegister}, :kizu_keep_native_deleted_result_${id}
                    move-object/16 v${site.resultRegister}, v${site.scratchRegister}
                    :kizu_keep_native_deleted_result_${id}
                    nop
                """,
            )

            // Must be inserted before the factory call: its move-result may overwrite
            // the original message's register (the bug in beta.38's call-site hook).
            caller.addInstructions(
                site.callIndex,
                "move-object/16 v${site.scratchRegister}, v${site.sourceRegister}",
            )
        }
    }
}
