package app.revanced.patches.reddit.boost.randnsfw

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

@Patch(
    name = "Random NSFW Fix",
    description = "Fixes the broken Random NSFW button in Boost by replacing the dead r/randnsfw URL with redditrand.com.",
    compatiblePackages = [CompatiblePackage("com.rubenmayayo.reddit")]
)
@Suppress("unused")
object RandomNsfwFixPatch : BytecodePatch(emptySet()) {

    private const val REPLACEMENT_URL = "https://redditrand.com?nsfw=1"

    override fun execute(context: BytecodeContext) {
        var replacedCount = 0

        context.classes.forEach { classDef ->
            classDef.mutableMethods.forEach { method ->
                method.implementation?.mutableInstructions?.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING) return@forEachIndexed
                    val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference
                        ?: return@forEachIndexed
                    if (!ref.string.contains("randnsfw", ignoreCase = true)) return@forEachIndexed
                    val register = (instruction as BuilderInstruction21c).registerA
                    method.replaceInstruction(
                        index,
                        BuilderInstruction21c(
                            Opcode.CONST_STRING,
                            register,
                            ImmutableStringReference(REPLACEMENT_URL)
                        )
                    )
                    replacedCount++
                }
            }
        }

        if (replacedCount == 0)
            throw Exception("Random NSFW Fix: Could not find 'randnsfw' string in Boost APK.")
    }
}
