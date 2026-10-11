package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
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

        // Bind to the resolved factory method fingerprint, not a source class name that
        // PurpleTV injects into its own fork and which is absent from Twitch's original DEX.
        val factoryMethod = DeletedMessageFormatterFingerprint.method
        val factoryOwner = DeletedMessageFormatterFingerprint.classDef.type
        val factoryName = factoryMethod.name
        val factoryParameters = factoryMethod.parameterTypes.map { it.toString() }
        val factoryReturn = factoryMethod.returnType

        fun isDeletedFactory(reference: MethodReference): Boolean =
            reference.definingClass == factoryOwner &&
                reference.name == factoryName &&
                reference.parameterTypes.map { it.toString() } == factoryParameters &&
                reference.returnType == factoryReturn &&
                factoryReturn == SPANNED &&
                factoryParameters == listOf(
                    "Ljava/lang/String;",
                    SPANNED_STRING,
                    "Landroid/content/Context;",
                    "Ltv/twitch/android/core/mvp/viewdelegate/EventDispatcher;",
                    "Z",
                )

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

        // Identify RecyclerView.ViewHolder from the APK's own class structure, not a
        // guessed PurpleTV source name or Twitch's obfuscated holder class name.
        val superclassByType = mutableMapOf<String, String?>()
        val recyclerViewHolderRoots = mutableSetOf<String>()
        classDefForEach { classDef ->
            superclassByType[classDef.type] = classDef.superclass
            val fieldTypes = classDef.fields.map { it.type }.toSet()
            if (classDef.superclass == "Ljava/lang/Object;" &&
                "Landroid/view/View;" in fieldTypes &&
                "Ljava/lang/ref/WeakReference;" in fieldTypes &&
                "Landroidx/recyclerview/widget/RecyclerView;" in fieldTypes
            ) {
                recyclerViewHolderRoots += classDef.type
            }
        }

        if (recyclerViewHolderRoots.size != 1) {
            throw PatchException(
                "Kizu deleted messages: could not uniquely identify the RecyclerView.ViewHolder base from APK structure.",
            )
        }

        fun isRecyclerViewHolder(classType: String): Boolean {
            var current = classType
            val visited = mutableSetOf<String>()
            while (visited.add(current)) {
                if (current in recyclerViewHolderRoots) return true
                current = superclassByType[current] ?: return false
            }
            return false
        }

        // PurpleTV hooks the factory at the row binder. In the Twitch APK, resolve that
        // caller by its inheritance from RecyclerView.ViewHolder, then require one match.
        classDefForEach { classDef ->
            if (!isRecyclerViewHolder(classDef.type)) return@classDefForEach
            for (method in classDef.methods) {
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
                "Kizu deleted messages: expected exactly one native factory call under a RecyclerView.ViewHolder, found ${callSites.size}.",
            )
        }

        callSites.sortedByDescending { it.callIndex }.forEachIndexed { id, site ->
            val callerClass = mutableClassDefBy(site.classType)
            val caller = callerClass.methods.singleOrNull {
                methodSignature(it) == site.methodSignature
            } ?: throw PatchException("Kizu deleted messages: fingerprinted factory caller disappeared.")

            // Anchor the skip target to the actual existing instruction immediately after the
            // original invoke/move-result pair. ExternalLabel binds the new branch to that exact
            // instruction, avoiding unresolved labels across separately inserted code blocks.
            val originalInstructions = caller.implementation!!.instructions
            val afterFactory = originalInstructions.getOrNull(site.callIndex + 2)
                ?: throw PatchException("Kizu deleted messages: factory call has no following instruction.")
            val afterFactoryLabel = "kizu_after_deleted_factory_$id"
            val nativeFactoryLabel = "kizu_native_deleted_factory_$id"

            caller.addInstructionsWithLabels(
                site.callIndex,
                """
                    invoke-static/range {v${site.sourceRegister} .. v${site.sourceRegister}}, $SUPPORT->styleDeletedMessageFromRow(Landroid/text/Spanned;)Landroid/text/Spanned;
                    move-result-object v${site.scratchRegister}
                    if-eqz v${site.scratchRegister}, :${nativeFactoryLabel}
                    move-object/16 v${site.resultRegister}, v${site.scratchRegister}
                    goto :${afterFactoryLabel}
                    :${nativeFactoryLabel}
                    nop
                """,
                ExternalLabel(afterFactoryLabel, afterFactory),
            )
        }
    }
}
