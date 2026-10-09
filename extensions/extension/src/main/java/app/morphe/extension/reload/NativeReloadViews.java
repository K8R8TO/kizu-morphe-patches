package app.morphe.extension.reload;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class NativeReloadViews {
    private static final String TAG = "twitchpatches_reload_button";
    private static final String LOG_TAG = "KizuReload";
    private static final WeakHashMap<View, Binding> bindings = new WeakHashMap<>();

    private NativeReloadViews() {}

    public static synchronized void install(View root, Object owner, boolean live, int volumeId) {
        if (root == null || !live || !(owner instanceof NativeReloadOwner)) return;

        View volume = root.findViewById(volumeId);
        ViewGroup parent = volume != null && volume.getParent() instanceof ViewGroup
                ? (ViewGroup) volume.getParent() : null;
        int id = root.getResources().getIdentifier(TAG, "id", root.getContext().getPackageName());
        View button = parent == null || id == 0 ? null : parent.findViewById(id);

        Log.i(LOG_TAG, "reload view install: root=" + (root != null)
                + " volume=" + (volume != null) + " button=" + (button != null));
        if (button == null || volume == null) return;

        Binding binding = bindings.get(button);
        if (binding == null) {
            binding = new Binding(root, button, volume, (NativeReloadOwner) owner);
            bindings.put(button, binding);
            button.setOnClickListener(binding);
            button.addOnAttachStateChangeListener(binding);
            if (button.isAttachedToWindow()) binding.onViewAttachedToWindow(button);
            Log.i(LOG_TAG, "reload view binding installed");
        } else {
            binding.refresh();
        }
    }

    public static synchronized void refreshPreferences() {
        for (Binding binding : bindings.values()) {
            if (binding != null) binding.refresh();
        }
    }

    private static final class Binding implements View.OnClickListener,
            View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<View> root;
        private final WeakReference<View> button;
        private final WeakReference<View> volume;
        // NativeReloadAction keeps its host weakly; retain this small adapter for the binding's lifetime.
        // ViewReloadHost itself only holds weak references to the view and owner, so this does not pin them.
        private final NativeReloadHost host;
        private final NativeReloadAction action;

        Binding(View root, View button, View volume, NativeReloadOwner owner) {
            this.root = new WeakReference<>(root);
            this.button = new WeakReference<>(button);
            this.volume = new WeakReference<>(volume);
            this.host = new ViewReloadHost(root, owner);
            this.action = NativeReloadAction.create(host);
        }

        synchronized void refresh() {
            View target = button.get();
            View original = volume.get();
            if (target == null || original == null) return;
            int visibility = ReloadRuntime.enabled() ? original.getVisibility() : View.GONE;
            if (target.getVisibility() != visibility) target.setVisibility(visibility);
            target.setEnabled(original.isEnabled() && action.available());
        }

        @Override public void onClick(View view) {
            if (view.isShown() && view.isEnabled()) {
                Log.i(LOG_TAG, "reload button tapped");
                action.invoke();
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
