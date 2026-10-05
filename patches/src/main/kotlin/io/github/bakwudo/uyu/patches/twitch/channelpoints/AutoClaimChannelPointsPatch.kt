package io.github.bakwudo.uyu.patches.twitch.channelpoints

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val MODEL = "Ltv/twitch/android/models/communitypoints/CommunityPointsModel;"
private const val IVS_PLAYER = "Lcom/amazonaws/ivs/player/MediaPlayer;"
private const val METADATA = "Ltv/twitch/android/shared/one/chat/pub/ChatModeMetadata;"
private const val CHANNEL_POINTS = "Lapp/morphe/extension/channelpoints/ChannelPoints;"

private const val ON_MODEL_UPDATED =
    "$CHANNEL_POINTS->onModelUpdated(Ljava/lang/Object;Ljava/lang/Object;)V"
private const val ON_PLAYBACK_CONFIGURED =
    "$CHANNEL_POINTS->onPlaybackConfigured(Ljava/lang/Object;Ljava/lang/Object;)V"
private const val ON_PLAYBACK_STATE =
    "$CHANNEL_POINTS->onPlaybackStateChanged(Ljava/lang/Object;Ljava/lang/Object;)V"
private const val ON_PLAYBACK_RELEASED =
    "$CHANNEL_POINTS->onPlaybackReleased(Ljava/lang/Object;)V"

internal val autoClaimChannelPointsPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val classes = mutableListOf<ClassDef>().apply { classDefForEach { add(it) } }

        val provider = classes.filter { type ->
            type.fields.any {
                it.type == MODEL &&
                    !AccessFlags.STATIC.isSet(it.accessFlags) &&
                    !AccessFlags.FINAL.isSet(it.accessFlags)
            } && type.methods.any {
                it.parameterTypes.map { parameter -> parameter.toString() } ==
                    listOf("Ljava/lang/String;", METADATA) &&
                    it.returnType == "V" &&
                    !AccessFlags.STATIC.isSet(it.accessFlags)
            }
        }.singleOrNull() ?: throw PatchException(
            "Kizu Channel Points: could not uniquely resolve the CommunityPoints provider.",
        )

        val modelField = provider.fields.singleOrNull {
            it.type == MODEL && !AccessFlags.STATIC.isSet(it.accessFlags)
        } ?: throw PatchException(
            "Kizu Channel Points: could not uniquely resolve the CommunityPoints model field.",
        )

        val modelStores = classes.flatMap { type ->
            type.methods.flatMap { method ->
                method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    if (instruction.opcode == Opcode.IPUT_OBJECT &&
                        field?.definingClass == provider.type &&
                        field.name == modelField.name &&
                        field.type == MODEL &&
                        method.name != "<init>") {
                        Triple(type, method, index)
                    } else null
                } ?: emptyList()
            }
        }

        if (modelStores.size != 1) {
            throw PatchException(
                "Kizu Channel Points: expected one CommunityPointsModel update store, found ${modelStores.size}.",
            )
        }

        val (updateClass, originalUpdateMethod, updateIndex) = modelStores.single()
        val updateMethod = mutableClassDefBy(updateClass.type).methods.singleOrNull {
            it.name == originalUpdateMethod.name &&
                it.parameterTypes == originalUpdateMethod.parameterTypes &&
                it.returnType == originalUpdateMethod.returnType
        } ?: throw PatchException(
            "Kizu Channel Points: selected CommunityPoints model update method disappeared.",
        )

        val store = updateMethod.implementation?.instructions?.elementAtOrNull(updateIndex)
            as? TwoRegisterInstruction
            ?: throw PatchException(
                "Kizu Channel Points: CommunityPoints model store is not a two-register instruction.",
            )

        if (store.registerA !in 0..15 || store.registerB !in 0..15) {
            throw PatchException("Kizu Channel Points: unexpected model/provider registers.")
        }

        updateMethod.addInstruction(
            updateIndex + 1,
            BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2, 0, store.registerB, store.registerA, 0, 0,
                ImmutableMethodReference(
                    CHANNEL_POINTS,
                    "onModelUpdated",
                    listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                    "V",
                ),
            ),
        )

        val playerMatches = mutableListOf<Pair<ClassDef, Method>>()
        classes.forEach { type ->
            if (type.fields.any { it.type == IVS_PLAYER }) {
                type.methods.filter { method ->
                    method.parameterTypes.size == 2 &&
                        method.returnType == "V" &&
                        !AccessFlags.STATIC.isSet(method.accessFlags) &&
                        hasStrings(method, "player", "channel_id")
                }.forEach { playerMatches.add(type to it) }
            }
        }

        if (playerMatches.size != 1) {
            throw PatchException(
                "Kizu Channel Points: expected one IVS playback configuration method, found ${playerMatches.size}.",
            )
        }

        val (player, configure) = playerMatches.single()
        mutableClassDefBy(player.type).methods.single {
            it.name == configure.name && it.parameterTypes == configure.parameterTypes
        }.addInstruction(
            0,
            BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2, 0, 2, 0, 0, 0,
                ImmutableMethodReference(
                    CHANNEL_POINTS,
                    "onPlaybackConfigured",
                    listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                    "V",
                ),
            ),
        )

        val stateMatches = player.methods.filter { method ->
            method.parameterTypes.size == 1 &&
                method.returnType == "V" &&
                method.implementation?.instructions?.any { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    reference is MethodReference && reference.name == "setValue"
                } == true
        }

        if (stateMatches.size != 1) {
            throw PatchException(
                "Kizu Channel Points: expected one IVS player state publication method, found ${stateMatches.size}.",
            )
        }

        val stateMethod = stateMatches.single()
        mutableClassDefBy(player.type).methods.single {
            it.name == stateMethod.name && it.parameterTypes == stateMethod.parameterTypes
        }.addInstruction(
            0,
            BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                2, 0, 1, 0, 0, 0,
                ImmutableMethodReference(
                    CHANNEL_POINTS,
                    "onPlaybackStateChanged",
                    listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                    "V",
                ),
            ),
        )

        val releaseMatches = player.methods.filter { method ->
            method.parameterTypes.isEmpty() &&
                method.returnType == "V" &&
                method.implementation?.instructions?.any { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.definingClass == "Lcom/amazonaws/ivs/player/Player;" &&
                        reference.name == "removeListener" &&
                        reference.parameterTypes.map { parameter -> parameter.toString() } ==
                            listOf("Lcom/amazonaws/ivs/player/Player\$Listener;") &&
                        reference.returnType == "V"
                } == true
        }

        if (releaseMatches.size != 1) {
            throw PatchException(
                "Kizu Channel Points: expected one IVS player release method, found ${releaseMatches.size}.",
            )
        }

        val releaseMethod = releaseMatches.single()
        mutableClassDefBy(player.type).methods.single {
            it.name == releaseMethod.name && it.parameterTypes == releaseMethod.parameterTypes
        }.addInstruction(
            0,
            BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                ImmutableMethodReference(
                    CHANNEL_POINTS,
                    "onPlaybackReleased",
                    listOf("Ljava/lang/Object;"),
                    "V",
                ),
            ),
        )
    }
}

private fun hasStrings(method: Method, vararg expected: String): Boolean {
    val found = mutableSetOf<String>()
    method.implementation?.instructions?.forEach { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        if (reference is StringReference) found += reference.string
    }
    return expected.all(found::contains)
}
