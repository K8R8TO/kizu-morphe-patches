package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
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
        // Restore Twitch's original formatted payload at the row factory call.
        val factory = DeletedMessageFormatterFingerprint.method
        val factoryOwner = DeletedMessageFormatterFingerprint.classDef.type
        val factoryParameters = factory.parameterTypes.map { it.toString() }
        fun isFactory(ref: MethodReference) = ref.definingClass == factoryOwner && ref.name == factory.name &&
            ref.parameterTypes.map { it.toString() } == factoryParameters && ref.returnType == factory.returnType
        fun signature(method: com.android.tools.smali.dexlib2.iface.Method) = method.name +
            "(" + method.parameterTypes.joinToString("") { it.toString() } + ")" + method.returnType
        fun collect(instruction: Instruction, out: MutableSet<Int>) {
            when (instruction) {
                is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).forEach(out::add)
                is FiveRegisterInstruction -> out.addAll(listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount))
                is ThreeRegisterInstruction -> { out.add(instruction.registerA); out.add(instruction.registerB); out.add(instruction.registerC) }
                is TwoRegisterInstruction -> { out.add(instruction.registerA); out.add(instruction.registerB) }
                is OneRegisterInstruction -> out.add(instruction.registerA)
            }
        }
        val parents = mutableMapOf<String, String?>()
        val roots = mutableSetOf<String>()
        classDefForEach { def ->
            parents[def.type] = def.superclass
            val fields = def.fields.map { it.type }.toSet()
            if (def.superclass == "Ljava/lang/Object;" && "Landroid/view/View;" in fields && "Ljava/lang/ref/WeakReference;" in fields && "Landroidx/recyclerview/widget/RecyclerView;" in fields) roots += def.type
        }
        if (roots.size != 1) throw PatchException("Could not uniquely identify Twitch's RecyclerView holder base.")
        fun holder(type: String): Boolean {
            var current = type
            val seen = mutableSetOf<String>()
            while (seen.add(current)) { if (current in roots) return true; current = parents[current] ?: return false }
            return false
        }
        data class Site(val owner: String, val method: String, val index: Int, val source: Int, val result: Int, val scratch: Int)
        val sites = mutableListOf<Site>()
        classDefForEach { def ->
            if (!holder(def.type)) return@classDefForEach
            for (method in def.methods) {
                val insns = method.implementation?.instructions?.toList() ?: continue
                for ((index, insn) in insns.withIndex()) {
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (!isFactory(ref)) continue
                    val invoke = insn as? FiveRegisterInstruction ?: throw PatchException("Unexpected deleted factory call shape.")
                    if (invoke.registerCount != 5 || insns.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) throw PatchException("Deleted factory result layout changed.")
                    val result = (insns[index + 1] as OneRegisterInstruction).registerA
                    val used = mutableSetOf<Int>(); collect(insn, used)
                    val live = mutableSetOf<Int>(); insns.drop(index + 2).forEach { collect(it, live) }
                    val scratch = (0 until method.implementation!!.registerCount).firstOrNull { it <= 255 && it != invoke.registerD && it != result && it !in used && it !in live }
                        ?: throw PatchException("No safe temporary register for deleted factory.")
                    sites += Site(def.type, signature(method), index, invoke.registerD, result, scratch)
                }
            }
        }
        if (sites.size != 1) throw PatchException("Expected one deleted-message row factory call, found " + sites.size + ".")
        val site = sites.single()
        val caller = mutableClassDefBy(site.owner).methods.single { signature(it) == site.method }.toMutable()
        val continuation = caller.instructions.getOrNull(site.index + 2) ?: throw PatchException("Deleted-message factory has no continuation.")
        caller.addInstructionsWithLabels(site.index, """
            invoke-static/range {v${site.source} .. v${site.source}}, $SUPPORT->styleDeletedMessageFromFactory(Landroid/text/Spanned;)Landroid/text/Spanned;
            move-result-object v${site.scratch}
            if-eqz v${site.scratch}, :kizu_keep_native_deleted
            move-object/16 v${site.result}, v${site.scratch}
            goto :kizu_after_deleted_restore
            :kizu_keep_native_deleted
            nop
        """, ExternalLabel("kizu_after_deleted_restore", continuation))

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


    }
}
