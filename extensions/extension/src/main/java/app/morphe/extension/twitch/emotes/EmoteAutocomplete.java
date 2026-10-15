package app.morphe.extension.twitch.emotes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.bakwudo.uyu.extension.settings.Settings;

/** Adds third-party emotes to Twitch 31.3.1's native chat autocomplete results. */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class EmoteAutocomplete {
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_THIRD_PARTY_SUGGESTIONS = 5;

    private EmoteAutocomplete() { }

    /** Results are rebuilt by Twitch for each query; no separate popup needs refreshing. */
    static void refreshAll() { }

    /** Twitch calls this immediately before returning the native suggestion rows. */
    public static List augmentNativeSuggestions(List nativeSuggestions, String query) {
        try {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() ||
                    query == null || query.isEmpty() || query.charAt(0) == '@') return nativeSuggestions;

            int tokenStart = Math.max(query.lastIndexOf(' '), query.lastIndexOf('\n')) + 1;
            String prefix = query.substring(tokenStart);
            if (prefix.startsWith(":")) prefix = prefix.substring(1);
            if (prefix.length() < 2) return nativeSuggestions;

            EmoteSupport.ensureAutocompleteCatalog(io.github.bakwudo.uyu.extension.Utils.getContext());
            List<Emote> matches = EmoteSupport.autocomplete(
                    EmoteSupport.getCurrentChannelId(), prefix, MAX_THIRD_PARTY_SUGGESTIONS);
            if (matches.isEmpty()) return nativeSuggestions;

            ArrayList result = new ArrayList(MAX_SUGGESTIONS);
            Set<String> names = new HashSet<>();
            int nativeLimit = MAX_SUGGESTIONS - Math.min(MAX_THIRD_PARTY_SUGGESTIONS, matches.size());
            if (nativeSuggestions != null) {
                for (Object suggestion : nativeSuggestions) {
                    String nativeName = suggestionName(suggestion);
                    if (nativeName == null || names.add(nativeName.toLowerCase(Locale.ROOT))) {
                        result.add(suggestion);
                    }
                    if (result.size() >= nativeLimit) break;
                }
            }

            ClassLoader loader = EmoteAutocomplete.class.getClassLoader();
            Class<?> assetType = Class.forName("defpackage.xof", false, loader);
            Class<?> emoteType = Class.forName("defpackage.wof", false, loader);
            Class<?> genericType = Class.forName("defpackage.uof", false, loader);
            Class<?> suggestionType = Class.forName("defpackage.wu1", false, loader);
            Class<?> emoteKind = Class.forName("defpackage.zof", false, loader);
            Method assetValue = assetType.getMethod("valueOf", String.class);
            Object otherKind = emoteKind.getMethod("valueOf", String.class).invoke(null, "OTHER");
            Constructor<?> genericConstructor = genericType.getConstructor(
                    String.class, String.class, assetType, emoteKind);
            Constructor<?> suggestionConstructor = suggestionType.getConstructor(emoteType, String.class);

            for (Emote emote : matches) {
                if (!names.add(emote.name.toLowerCase(Locale.ROOT))) continue;
                Object asset = assetValue.invoke(null, emote.animated ? "ANIMATED" : "STATIC");
                Object model = genericConstructor.newInstance(
                        "KIZU-" + emote.provider + "-" + emote.name,
                        emote.name,
                        asset,
                        otherKind);
                result.add(suggestionConstructor.newInstance(model, emote.url));
                if (result.size() >= MAX_SUGGESTIONS) break;
            }
            return result.isEmpty() ? nativeSuggestions : result;
        } catch (Throwable ignored) {
            // Never break Twitch's original suggestion flow if its model changes.
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
