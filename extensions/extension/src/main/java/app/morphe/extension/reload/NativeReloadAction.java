package app.morphe.extension.reload;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;

public final class NativeReloadAction {
    private static final WeakHashMap<Object, NativeReloadAction> actions = new WeakHashMap<>();
    private static final Set<String> observedBindings = new HashSet<>();
    private final WeakReference<NativeReloadHost> host;
    private NativeReloadAction(NativeReloadHost host) { this.host = new WeakReference<>(host); }
    static NativeReloadAction create(NativeReloadHost host) { return new NativeReloadAction(host); }
    public static synchronized void bind(Object owner, NativeReloadHost host) { actions.put(owner, new NativeReloadAction(host)); }
    public static synchronized NativeReloadAction forVolume(Object callback) {
        NativeReloadAction action = callback instanceof NativeReloadOwner ? actions.get(((NativeReloadOwner) callback).reloadControlsOwner()) : null;
        String category = callback instanceof NativeReloadOwner ? "mapped" : "unmapped";
        String binding = category + (action == null ? " unavailable" : action.available() ? " ready" : " not ready");
        if (observedBindings.size() < 16 && observedBindings.add(binding)) Log.i("KizuReload", binding);
        return action;
    }
    public boolean available() {
        NativeReloadHost current = host.get();
        return current != null && current.reloadIdentity() != null;
    }
    public Object invoke() {
        NativeReloadHost current = host.get();
        if (current != null && ReloadRuntime.enabled() && current.reloadIdentity() != null && current.reloadNativeStream())
            Log.i("KizuReload", "native reload requested");
        return null;
    }
}
