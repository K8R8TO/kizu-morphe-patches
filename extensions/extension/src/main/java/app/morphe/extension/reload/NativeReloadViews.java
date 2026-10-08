package app.morphe.extension.reload;

import android.view.View;
import android.view.ViewGroup;
import java.util.WeakHashMap;

public final class NativeReloadViews {
    private static final String TAG = "twitchpatches_reload_button";
    private static final WeakHashMap<View, View> volumes = new WeakHashMap<>();
    private static final WeakHashMap<View, NativeReloadAction> actions = new WeakHashMap<>();
    private NativeReloadViews() {}

    public static synchronized void install(View root, Object owner, boolean live, int volumeId) {
        if (root == null || !live || !(owner instanceof NativeReloadOwner)) return;
        View volume = root.findViewById(volumeId);
        if (volume == null || !(volume.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) volume.getParent();
        int id = root.getResources().getIdentifier(TAG, "id", root.getContext().getPackageName());
        if (id == 0) return;
        View button = parent.findViewById(id);
        if (button == null) return;
        NativeReloadAction action = NativeReloadAction.forVolume(owner);
        if (action == null) return;
        if (!actions.containsKey(button)) {
            actions.put(button, action);
            volumes.put(button, volume);
            button.setOnClickListener(v -> {
                if (v.isShown() && v.isEnabled()) action.invoke();
            });
        }
        refresh(button);
    }

    private static void refresh(View button) {
        View volume = volumes.get(button);
        NativeReloadAction action = actions.get(button);
        if (volume == null || action == null) return;
        button.setVisibility(ReloadRuntime.enabled() ? volume.getVisibility() : View.GONE);
        button.setEnabled(volume.isEnabled() && action.available());
    }

    public static synchronized void refreshPreferences() {
        for (View button : actions.keySet()) refresh(button);
    }
}
