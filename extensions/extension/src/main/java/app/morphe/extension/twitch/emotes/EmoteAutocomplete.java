package app.morphe.extension.twitch.emotes;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * Adds third-party emotes to Twitch 31.3.1's native autocomplete return list.
 * Native model classes are resolved through Twitch's class loader supplied by Lr6f.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class EmoteAutocomplete {
    private static final String TAG = "KizuEmoteAutocomplete";
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_THIRD_PARTY_SUGGESTIONS = 5;
    private static final ThreadLocal<String> CURRENT_QUERY = new ThreadLocal<>();

    private EmoteAutocomplete() { }

    /** Capture the untouched composer text before Twitch rewrites its input registers. */
    public static void captureQuery(String query) {
        CURRENT_QUERY.set(query);
    }

    /** No separate popup/provider cache exists; each native query rebuilds its own result rows. */
    static void refreshAll() { }

    /**
     * Augments the exact native list returned by Lr6f.H(String). This intentionally does not
     * depend on the internal autocomplete map; Twitch renders and selects these native suggestion
     * models exactly as it does its own suggestions.
     */
    public static List augmentNativeSuggestions(List nativeSuggestions, Object provider) {
        String query = CURRENT_QUERY.get();
        CURRENT_QUERY.remove();
        try {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() ||
                    query == null || query.isEmpty() || query.charAt(0) == '@' || provider == null) {
                return nativeSuggestions;
            }

            int tokenStart = Math.max(query.lastIndexOf(' '), query.lastIndexOf('\n')) + 1;
            String prefix = query.substring(tokenStart);
            if (prefix.startsWith(":")) prefix = prefix.substring(1);
            if (prefix.length() < 2) return nativeSuggestions;

            EmoteSupport.ensureAutocompleteCatalog(
                    io.github.bakwudo.uyu.extension.Utils.getContext());
            List<Emote> matches = EmoteSupport.autocomplete(
                    EmoteSupport.getCurrentChannelId(), prefix, MAX_THIRD_PARTY_SUGGESTIONS);
            if (matches.isEmpty()) return nativeSuggestions;

            ArrayList result = new ArrayList(MAX_SUGGESTIONS);
            Set<String> names = new HashSet<>();
            int thirdPartyLimit = Math.min(MAX_THIRD_PARTY_SUGGESTIONS, matches.size());
            int nativeLimit = MAX_SUGGESTIONS - thirdPartyLimit;
            if (nativeSuggestions != null) {
                for (Object suggestion : nativeSuggestions) {
                    String nativeName = suggestionName(suggestion);
                    if (nativeName == null || names.add(nativeName.toLowerCase(Locale.ROOT))) {
                        result.add(suggestion);
                    }
                    if (result.size() >= nativeLimit) break;
                }
            }

            // These descriptors and constructors were checked against Twitch 31.3.1's DEX.
            // Use the Twitch provider's class loader, not the Morphe extension's class loader.
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> assetType = Class.forName("xof", false, loader);
            Class<?> emoteType = Class.forName("wof", false, loader);
            Class<?> modelType = Class.forName("uof", false, loader);
            Class<?> suggestionType = Class.forName("wu1", false, loader);
            Class<?> emoteKind = Class.forName("zof", false, loader);
            Constructor<?> modelConstructor = modelType.getConstructor(
                    String.class, String.class, assetType, emoteKind);
            Constructor<?> suggestionConstructor = suggestionType.getConstructor(
                    emoteType, String.class);
            Object otherKind = emoteKind.getMethod("valueOf", String.class).invoke(null, "OTHER");
            Object staticAsset = assetType.getMethod("valueOf", String.class).invoke(null, "STATIC");
            Object animatedAsset = assetType.getMethod("valueOf", String.class).invoke(null, "ANIMATED");

            int added = 0;
            for (Emote emote : matches) {
                if (emote == null || emote.name == null || emote.url == null ||
                        !names.add(emote.name.toLowerCase(Locale.ROOT)) ||
                        !EmoteSupport.providerEnabled(emote.provider)) {
                    continue;
                }
                Object asset = emote.animated ? animatedAsset : staticAsset;
                Object model = modelConstructor.newInstance(
                        "KIZU-" + emote.provider + "-" + emote.name,
                        emote.name,
                        asset,
                        otherKind);
                result.add(suggestionConstructor.newInstance(model, emote.url));
                added++;
                if (result.size() >= MAX_SUGGESTIONS) break;
            }

            if (added > 0) {
                Log.d(TAG, "Added " + added + " third-party suggestions for prefix '" + prefix + "'.");
            }
            return result.isEmpty() ? nativeSuggestions : result;
        } catch (Throwable error) {
            Log.w(TAG, "Could not augment Twitch's native autocomplete results; preserving original rows.", error);
            return nativeSuggestions;
        }
    }

    private static String suggestionName(Object suggestion) {
        if (suggestion == null) return null;
        try {
            Object value = suggestion.getClass().getMethod("a").invoke(suggestion);
            if (!(value instanceof String)) return null;
            String name = ((String) value).trim();
            return name.startsWith("@") ? null : name;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
