package app.morphe.extension.videostats;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Kizu's custom Video Stats button and forwards its click to Twitch's built-in
 * video_debug_info_button. Twitch owns the native panel and live statistics model, so the
 * displayed metrics stay authoritative and follow the current playback session.
 */
public final class NativeVideoStatsViews {
    private static final String TAG = "KizuVideoStats";
    private static final String NATIVE_STATS_BUTTON = "video_debug_info_button";
    private static final Map<View, Binding> BINDINGS = new WeakHashMap<>();

    private NativeVideoStatsViews() {}

    public static synchronized void install(View root, View button, View volume) {
        if (root == null || button == null || volume == null) return;
        Binding binding = BINDINGS.get(button);
        if (binding == null) {
            binding = new Binding(root, button, volume);
            BINDINGS.put(button, binding);
            button.setOnClickListener(binding);
            button.addOnAttachStateChangeListener(binding);
            if (button.isAttachedToWindow()) binding.onViewAttachedToWindow(button);
        } else {
            binding.refresh();
        }
    }

    public static synchronized void refreshPreferences() {
        for (Binding binding : new ArrayList<>(BINDINGS.values())) {
            if (binding != null) binding.refresh();
        }
    }

    private static final class Binding implements View.OnClickListener,
            View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<View> root;
        private final WeakReference<View> button;
        private final WeakReference<View> volume;

        Binding(View root, View button, View volume) {
            this.root = new WeakReference<>(root);
            this.button = new WeakReference<>(button);
            this.volume = new WeakReference<>(volume);
        }

        synchronized void refresh() {
            View target = button.get();
            View original = volume.get();
            if (target == null || original == null) return;
            boolean show = VideoStatsRuntime.enabled()
                    && original.getVisibility() == View.VISIBLE;
            int next = show ? View.VISIBLE : View.GONE;
            if (target.getVisibility() != next) target.setVisibility(next);
            target.setEnabled(show && original.isEnabled());
        }

        @Override public void onClick(View view) {
            if (!view.isShown() || !view.isEnabled()) return;

            View base = root.get();
            if (base == null) {
                Log.w(TAG, "Cannot open native video stats: player view is unavailable.");
                return;
            }

            int id = base.getResources().getIdentifier(
                    NATIVE_STATS_BUTTON, "id", base.getContext().getPackageName());
            if (id == 0) {
                Log.w(TAG, "Twitch's native video stats button resource was not found.");
                return;
            }

            // Prefer the same live player-control subtree; the decor-root fallback handles
            // variants where the detached delegate root is not the inflated overlay itself.
            View nativeButton = base.findViewById(id);
            if (nativeButton == null) {
                View tree = base.getRootView();
                if (tree != null && tree != base) nativeButton = tree.findViewById(id);
            }
            if (nativeButton == null) {
                Log.w(TAG, "Twitch's native video stats button is not attached to this player.");
                return;
            }

            // The built-in control is normally GONE in production Twitch. performClick invokes
            // its existing presenter event without showing a duplicate info icon; Twitch then
            // renders its own in-player panel and supplies its real, live metrics.
            if (!nativeButton.performClick()) {
                Log.w(TAG, "Twitch's native video stats click handler was not ready.");
            } else {
                Log.i(TAG, "Opened Twitch's native video stats panel.");
            }
        }

        @Override public void onGlobalLayout() { refresh(); }

        @Override public void onViewAttachedToWindow(View view) {
            View current = root.get();
            if (current != null) {
                ViewTreeObserver observer = current.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(this);
            }
            refresh();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            View current = root.get();
            if (current != null) {
                ViewTreeObserver observer = current.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            }
        }
    }
}
