package io.github.bakwudo.uyu.patches.twitch.playergestures

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

private const val SUPPORT_CLASS = "Lapp/morphe/extension/player/GestureSettingsSupport;"
private const val CONTAINER_CLASS =
    "Ltv/twitch/android/shared/ui/elements/draggable/ConstraintTheatreContainerView;"

/**
 * Verified from the supplied Twitch 31.3.1 APKM (classes2.dex).
 *
 * ConstraintTheatreContainerView is an ancestor of the player view. Its inherited
 * DraggableConstraintLayout.onInterceptTouchEvent() calls p(MotionEvent) before the
 * nested theatre_root_container can intercept a swipe. Guarding only the nested layout
 * therefore cannot disable Twitch's swipe-to-portrait handler.
 */
internal object NativeLandscapeSwipeDecisionFingerprint : Fingerprint(
    definingClass = CONTAINER_CLASS,
    name = "p",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
)

internal object NativeLandscapeSwipeTouchFingerprint : Fingerprint(
    definingClass = CONTAINER_CLASS,
    name = "onTouchEvent",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
)

internal val preventSwipeToPortraitPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        val decision = NativeLandscapeSwipeDecisionFingerprint.method
        val decisionCalls = decision.instructions.mapNotNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? MethodReference
        }
        fun decisionHasCall(owner: String, name: String, parameters: List<String>, result: String) =
            decisionCalls.any { reference ->
                reference.definingClass == owner &&
                    reference.name == name &&
                    reference.parameterTypes.map { it.toString() } == parameters &&
                    reference.returnType == result
            }

        // Verify the real drag-area and raw-Y handling from Twitch 31.3.1 before modifying it.
        if (
            !decisionHasCall(CONTAINER_CLASS, "k", listOf("Landroid/view/MotionEvent;"), "Z") ||
            !decisionHasCall("Landroid/view/MotionEvent;", "getRawY", emptyList(), "F") ||
            !decisionHasCall("Lv880;", "s", listOf("Landroid/view/MotionEvent;"), "Z")
        ) {
            throw PatchException(
                "Twitch swipe guard: ConstraintTheatreContainerView.p() no longer matches the verified 31.3.1 drag handler.",
            )
        }

        // Return false from the parent's interception decision only while Kizu's
        // Prevent Swipe-to-Portrait Collapse setting is ON and the app is landscape.
        // This lets the child player receive swipes for Kizu's brightness/volume controls.
        decision.addInstructionsWithLabels(
            0,
            """
                invoke-static/range { p0 .. p0 }, $SUPPORT_CLASS->shouldSuppressNativeLandscapeSwipe(Landroid/view/View;)Z
                move-result v0
                if-eqz v0, :kizu_continue_native_landscape_swipe
                const/4 v0, 0x0
                return v0
                :kizu_continue_native_landscape_swipe
            """,
        )

        val touch = NativeLandscapeSwipeTouchFingerprint.method
        val touchCalls = touch.instructions.mapNotNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? MethodReference
        }
        fun touchHasCall(owner: String, name: String, parameters: List<String>, result: String) =
            touchCalls.any { reference ->
                reference.definingClass == owner &&
                    reference.name == name &&
                    reference.parameterTypes.map { it.toString() } == parameters &&
                    reference.returnType == result
            }

        if (
            !touchHasCall(
                "Ltv/twitch/android/shared/ui/elements/draggable/DraggableConstraintLayout;",
                "onTouchEvent",
                listOf("Landroid/view/MotionEvent;"),
                "Z",
            ) ||
            !touchHasCall(
                "Landroid/view/ScaleGestureDetector;",
                "onTouchEvent",
                listOf("Landroid/view/MotionEvent;"),
                "Z",
            )
        ) {
            throw PatchException(
                "Twitch swipe guard: ConstraintTheatreContainerView.onTouchEvent() no longer matches the verified 31.3.1 handler.",
            )
        }

        // If the outer container itself becomes the touch target (rather than a player child),
        // consume those events without forwarding them into Twitch's native drag helper.
        touch.addInstructionsWithLabels(
            0,
            """
                invoke-static/range { p0 .. p0 }, $SUPPORT_CLASS->shouldSuppressNativeLandscapeSwipe(Landroid/view/View;)Z
                move-result v0
                if-eqz v0, :kizu_continue_native_landscape_touch
                const/4 v0, 0x1
                return v0
                :kizu_continue_native_landscape_touch
            """,
        )
    }
}
