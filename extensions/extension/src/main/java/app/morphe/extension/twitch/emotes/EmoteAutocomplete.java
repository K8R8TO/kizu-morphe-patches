package app.morphe.extension.twitch.emotes;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

/** Adapts Canic's native autocomplete-map technique to Twitch 31.3.1's verified DEX models. */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class EmoteAutocomplete {
    private static final String TAG = "KizuEmoteAutocomplete";
    private static final Object MISSING = new Object();
    private static final Object LOCK = new Object();
    private static final Map<String, Object> ORIGINALS = new LinkedHashMap<>();
    private static final Map<String, Emote> URL_EMOTES = new ConcurrentHashMap<>();

    private static WeakReference<Map> activeMap = new WeakReference<>(null);
    private static volatile boolean dirty = true;
    private static String lastSignature = "";

    private EmoteAutocomplete() { }

    /** Called at the start of Twitch 31.3.1's Lr6f;->H(String):List builder. */
    public static void updateNativeProvider(Object provider) {
        if (provider == null) return;
        try {
            Map map = findBackingMap(provider);
            if (map == null) {
                Log.w(TAG, "Autocomplete provider backing map h was not a LinkedHashMap.");
                return;
            }
            EmoteSupport.ensureAutocompleteCatalog(io.github.bakwudo.uyu.extension.Utils.getContext());
            String signature = settingsSignature() + "|" + String.valueOf(EmoteSupport.getCurrentChannelId());
            synchronized (LOCK) {
                Map previous = activeMap.get();
                if (previous != map) {
                    restore(previous);
                    ORIGINALS.clear();
                    URL_EMOTES.clear();
                    activeMap = new WeakReference<>(map);
                    dirty = true;
                }
                if (!dirty && signature.equals(lastSignature)) return;
                rebuild(map, provider.getClass().getClassLoader());
                lastSignature = signature;
                dirty = false;
            }
        } catch (Throwable error) {
            dirty = true;
            Log.w(TAG, "Could not update Twitch's native autocomplete map; preserving native behavior.", error);
        }
    }

    /** A catalog load/channel change invalidates the map; rebuild at the next native query. */
    static void refreshAll() {
        dirty = true;
    }

    /** URL resolver receives Lwof.b(), the synthetic ID supplied in the native model constructor. */
    public static String getAutocompleteUrl(String key) {
        try {
            if (key == null || !Settings.EMOTES_THIRD_PARTY.get() ||
                    !Settings.EMOTES_AUTOCOMPLETE.get()) return null;
            Emote emote = URL_EMOTES.get(key);
            if (emote == null || !EmoteSupport.providerEnabled(emote.provider)) return null;
            return emote.url;
        } catch (Throwable error) {
            Log.w(TAG, "Could not resolve a third-party autocomplete URL.", error);
            return null;
        }
    }

    /** Adapt Canic's bare-token opening hook to Twitch 31.3.1's Ldlf.i0(CharSequence). */
    public static boolean shouldOpenAutocomplete(CharSequence text) {
        if (!enabled() || text == null) return false;
        String value = text.toString();
        int start = Math.max(value.lastIndexOf(' '), value.lastIndexOf('\n')) + 1;
        String token = value.substring(start);
        return token.length() >= 2 && token.charAt(0) != '@';
    }

    /** Return the active token start, skipping Twitch's optional leading colon. */
    public static int getBareAutocompleteStart(CharSequence text) {
        if (!shouldOpenAutocomplete(text)) return -1;
        String value = text.toString();
        int start = Math.max(value.lastIndexOf(' '), value.lastIndexOf('\n')) + 1;
        return start < value.length() && value.charAt(start) == ':' ? start + 1 : start;
    }

    private static boolean enabled() {
        try {
            return Settings.EMOTES_THIRD_PARTY.get() && Settings.EMOTES_AUTOCOMPLETE.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String settingsSignature() {
        return enabled() + "|" + Settings.EMOTES_BTTV.get() + "|" +
                Settings.EMOTES_7TV.get() + "|" + Settings.EMOTES_FFZ.get();
    }

    private static Map findBackingMap(Object provider) throws Exception {
        // DEX-verified field: Lr6f;->h:Ljava/lang/Object;, consumed as LinkedHashMap by H(String).
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("h");
                field.setAccessible(true);
                Object value = field.get(provider);
                if (value instanceof LinkedHashMap) return (Map) value;
            } catch (NoSuchFieldException ignored) {
                // Try the next class in the hierarchy.
            }
        }
        // Defensive fallback if a future build renames the field but keeps the native-model map.
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(provider);
                    if (!(value instanceof LinkedHashMap)) continue;
                    Map candidate = (Map) value;
                    for (Object entryValue : candidate.values()) {
                        if (entryValue != null &&
                                Class.forName("wof", false, provider.getClass().getClassLoader())
                                        .isInstance(entryValue)) return candidate;
                    }
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static void rebuild(Map map, ClassLoader loader) {
        restore(map);
        ORIGINALS.clear();
        URL_EMOTES.clear();
        if (!enabled()) return;

        try {
            List<Emote> emotes = EmoteSupport.autocompleteAll();
            if (emotes.isEmpty()) return;

            // Verified directly against Twitch 31.3.1 DEX: Luof(String,String,xof,zof).
            Class<?> assetType = Class.forName("xof", false, loader);
            Class<?> modelType = Class.forName("uof", false, loader);
            Class<?> emoteKind = Class.forName("zof", false, loader);
            Constructor<?> modelConstructor = modelType.getConstructor(
                    String.class, String.class, assetType, emoteKind);
            Object otherKind = emoteKind.getMethod("valueOf", String.class).invoke(null, "OTHER");
            Object staticAsset = assetType.getMethod("valueOf", String.class).invoke(null, "STATIC");
            Object animatedAsset = assetType.getMethod("valueOf", String.class).invoke(null, "ANIMATED");

            for (Emote emote : emotes) {
                if (emote == null || emote.name == null || emote.name.isEmpty() ||
                        emote.url == null || emote.url.isEmpty() ||
                        !EmoteSupport.providerEnabled(emote.provider)) continue;

                String id = "KIZU-" + emote.provider + "-" + emote.name;
                ORIGINALS.put(emote.name, map.containsKey(emote.name) ? map.get(emote.name) : MISSING);
                Object model = modelConstructor.newInstance(
                        id, emote.name, emote.animated ? animatedAsset : staticAsset, otherKind);
                map.put(emote.name, model);
                URL_EMOTES.put(id, emote);
                URL_EMOTES.put(emote.name, emote);
            }
        } catch (Throwable error) {
            restore(map);
            ORIGINALS.clear();
            URL_EMOTES.clear();
            Log.w(TAG, "Twitch 31.3.1 native model construction failed; original map restored.", error);
        }
    }

    private static void restore(Map map) {
        if (map == null || ORIGINALS.isEmpty()) return;
        try {
            for (Map.Entry<String, Object> entry : ORIGINALS.entrySet()) {
                if (entry.getValue() == MISSING) map.remove(entry.getKey());
                else map.put(entry.getKey(), entry.getValue());
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not fully restore original native autocomplete entries.", error);
        }
    }
}
