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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT = "Lapp/morphe/extension/twitch/chat/DeletedMessagesSupport;"
private const val SPANNED = "Landroid/text/Spanned;"
private const val SPANNED_STRING = "Landroid/text/SpannedString;"
private const val CONTEXT = "Landroid/content/Context;"
private const val EVENT_DISPATCHER = "Ltv/twitch/android/core/mvp/viewdelegate/EventDispatcher;"
private const val COMPANION_SUFFIX = "\$Companion;"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)

        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException("Twitch deleted messages: access flag field was not found uniquely.")

        spanClass.fields.singleOrNull { field ->
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

        val factory = DeletedMessageFormatterFingerprint.method
        val factoryOwner = factory.definingClass
        val factoryParams = factory.parameterTypes.map { it.toString() }
        val outerOwner = if (factoryOwner.endsWith(COMPANION_SUFFIX)) {
            factoryOwner.removeSuffix(COMPANION_SUFFIX) + ";"
        } else {
            factoryOwner
        }
        val companionPrefix = outerOwner.removeSuffix(";") + "$"

        fun isFactorySignature(parameters: List<String>, returnType: String): Boolean =
            returnType == "Landroid/text/Spanned;" &&
                parameters.size == 5 &&
                parameters[0] == "Ljava/lang/String;" &&
                (parameters[1] == SPANNED || parameters[1] == SPANNED_STRING) &&
                parameters[2] == CONTEXT &&
                parameters[3] == EVENT_DISPATCHER &&
                parameters[4] == "Z"

        // R8 may rename Kotlin's $Companion class (for example, to Lr93$a;).
        // Discover it from the actual owner family and signature rather than guessing its name.
        val companionNamesByOwner = mutableMapOf<String, Set<String>>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith(companionPrefix) && classDef.type != outerOwner) {
                val names = classDef.methods
                    .filter { method ->
                        isFactorySignature(method.parameterTypes.map { it.toString() }, method.returnType)
                    }
                    .map { it.name }
                    .toSet()
                if (names.isNotEmpty()) companionNamesByOwner[classDef.type] = names
            }
        }
        val companionOwners = companionNamesByOwner.keys
        val excludedOwners = companionOwners + setOf(factoryOwner, outerOwner)

        fun isFactoryCall(reference: MethodReference): Boolean {
            val parameters = reference.parameterTypes.map { it.toString() }
            if (!isFactorySignature(parameters, reference.returnType)) return false

            val isDirectFactory =
                reference.definingClass == factoryOwner &&
                    reference.name == factory.name &&
                    parameters == factoryParams &&
                    reference.returnType == factory.returnType

            val isCompanionFactory =
                reference.definingClass in companionOwners &&
                    (reference.name == factory.name ||
                        reference.name in companionNamesByOwner[reference.definingClass].orEmpty())

            return isDirectFactory || isCompanionFactory
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
                    throw PatchException(
                        "Kizu deleted messages: unexpected non-range factory invocation register layout.",
                    )
                }
                // Static factory arguments: messageId, original Spanned, context, dispatcher, hasModAccess.
                return instruction.registerD
            }

            if (instruction is RegisterRangeInstruction) {
                val expectedCount = if (isStatic) 5 else 6
                if (instruction.registerCount != expectedCount) {
                    throw PatchException(
                        "Kizu deleted messages: unexpected factory argument count \${instruction.registerCount}.",
                    )
                }
                // Instance companion calls have a receiver first; static calls do not.
                return instruction.startRegister + if (isStatic) 1 else 2
            }

            throw PatchException("Kizu deleted messages: unsupported factory invocation format.")
        }

        data class CallSite(
            val classType: String,
            val methodSignature: String,
            val callIndex: Int,
            val messageRegister: Int,
            val resultRegister: Int,
            val scratchRegister: Int,
        )

        val callSites = mutableListOf<CallSite>()

        classDefForEach { classDef ->
            if (classDef.type !in excludedOwners) {
                for (method in classDef.methods) {
                    val implementation = method.implementation ?: continue
                    val instructions = implementation.instructions.toList()
                    for ((index, instruction) in instructions.withIndex()) {
                        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: continue
                        if (!isFactoryCall(reference)) continue

                        val moveResultIndex = index + 1
                        val moveResult = instructions.getOrNull(moveResultIndex)
                        if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                            throw PatchException(
                                "Kizu deleted messages: factory call has no immediate move-result-object.",
                            )
                        }
                        val resultRegister = (moveResult as? OneRegisterInstruction)?.registerA
                            ?: throw PatchException(
                                "Kizu deleted messages: factory result register could not be resolved.",
                            )
                        val sourceRegister = messageRegister(instruction)
                        val liveAfter = mutableSetOf<Int>()
                        for (suffixIndex in moveResultIndex + 1 until instructions.size) {
                            collectRegisters(instructions[suffixIndex], liveAfter)
                        }
                        val scratchRegister = (0 until implementation.registerCount).firstOrNull { register ->
                            register != resultRegister && register !in liveAfter
                        } ?: throw PatchException(
                            "Kizu deleted messages: no dead register available for the styling result.",
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
        }

        if (callSites.isEmpty()) {
            throw PatchException(
                "Kizu deleted messages: could not locate the original-message factory call site.",
            )
        }

        // PurpleTV's strategy applies the selected style at the call site that still has
        // the original Spanned message, rather than reconstructing it from spans later.
        // Patch from the end of each method so earlier call indexes remain stable.
        var callSiteId = 0
        callSites
            .sortedWith(compareBy<CallSite>({ it.classType }, { it.methodSignature }, { -it.callIndex }))
            .forEach { site ->
                val callerClass = mutableClassDefBy(site.classType)
                val caller = callerClass.methods.singleOrNull {
                    methodSignature(it) == site.methodSignature
                } ?: throw PatchException("Kizu deleted messages: factory caller method disappeared.")

                val label = ":kizu_deleted_messages_keep_native_$callSiteId"
                caller.addInstructionsWithLabels(
                    site.callIndex + 2,
                    """
                        invoke-static/range {v\${site.messageRegister} .. v\${site.messageRegister}}, $SUPPORT->styleDeletedMessage(Landroid/text/Spanned;)Landroid/text/Spanned;
                        move-result-object v\${site.scratchRegister}
                        if-eqz v\${site.scratchRegister}, $label
                        move-object v\${site.resultRegister}, v\${site.scratchRegister}
                        $label
                        nop
                    """,
                )
                callSiteId++
            }
    }
}
