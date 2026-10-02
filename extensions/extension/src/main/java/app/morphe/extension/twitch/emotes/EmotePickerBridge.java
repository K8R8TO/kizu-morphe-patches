package app.morphe.extension.twitch.emotes;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class EmotePickerBridge {
    private static final String TAG = "KizuPicker";
    private static final ConcurrentHashMap<String, String> IMAGE_URLS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Context> URL_CONTEXT = new ThreadLocal<>();

    private static final String T_ASSET = "tv.twitch.android.models.emotes.EmoteModelAssetType";
    private static final String T_KIND = "tv.twitch.android.models.emotes.EmoteModelType";
    private static final String T_MODEL_GENERIC = "tv.twitch.android.models.emotes.EmoteModel$Generic";
    private static final String T_MESSAGE_INPUT = "tv.twitch.android.shared.emotes.models.EmoteMessageInput";
    private static final String T_CLICKED_UNLOCKED =
            "tv.twitch.android.shared.emotes.emotepicker.models.ClickedEmote$Unlocked";
    private static final String T_UI_MODEL =
            "tv.twitch.android.shared.emotes.emotepicker.models.EmoteUiModel";
    private static final String T_IMAGE_DESCRIPTOR =
            "tv.twitch.android.shared.emotes.emotepicker.models.EmoteImageDescriptor";

    private EmotePickerBridge() {}

    /**
     * URL resolver used only for synthetic KIZU-* emote IDs. Native Twitch IDs always
     * fall through to Twitch's original EmoteUrlUtil implementation.
     */
    public static String getEmoteUrl(String id) {
        if (id == null || !id.startsWith("KIZU-")) return null;
        return IMAGE_URLS.get(id);
    }

    /**
     * The Twitch 31.3.1 URL method has only p0/p1. Keep the Context out of p0 while
     * resolving a KIZU URL, then restore it before the original implementation runs.
     */
    public static void saveUrlContext(Context context) {
        URL_CONTEXT.set(context);
    }

    public static Context restoreUrlContext() {
        Context context = URL_CONTEXT.get();
        URL_CONTEXT.remove();
        return context;
    }

    /**
     * Kept as the picker-open observation hook. It deliberately does not create a dialog,
     * replace the native picker, or alter the native picker lifecycle.
     */
    public static void onPickerOpened(Object ignored) {
        try {
            if (Settings.EMOTES_PICKER.get()) {
                Log.d(TAG, "native picker opened; Kizu emotes enabled");
            }
        } catch (Throwable t) {
            Log.w(TAG, "onPickerOpened failed", t);
        }
    }

    /**
     * Adds Kizu emotes to Twitch's existing ALL picker model. The native Twitch picker
     * remains responsible for layout, scrolling, animation, click handling and insertion.
     * No separate dialog or alternate picker UI is created.
     */
    public static Object mergeGlobal(Object uiSet) {
        if (!Settings.EMOTES_PICKER.get() || uiSet == null) return uiSet;

        try {
            List<Entry> entries = loadForChannel(EmoteSupport.getCurrentChannelId());
            if (entries.isEmpty()) return uiSet;

            Method getHeader = findNoArgMethod(uiSet.getClass(), "c", "getHeader");
            if (getHeader == null) {
                getHeader = findObjectGetterByType(
                        uiSet.getClass(),
                        "tv.twitch.android.shared.emotes.emotepicker.models.EmoteHeaderUiModel");
            }

            if (getHeader != null) {
                Object header = getHeader.invoke(uiSet);
                if (header != null) {
                    Method getSection = findNoArgMethod(header, "getEmotePickerSection");
                    if (getSection != null) {
                        Object section = getSection.invoke(header);
                        if (section != null && !"ALL".equals(section.toString())) {
                            return uiSet;
                        }
                    }
                }
            }

            Method getEmotes = findNoArgMethod(uiSet.getClass(), "b", "getEmotes");
            if (getEmotes == null) getEmotes = findListReturningGetter(uiSet.getClass());
            if (getEmotes == null) throw new NoSuchMethodException("EmoteUiSet emote-list getter not found");

            Object raw = getEmotes.invoke(uiSet);
            if (!(raw instanceof List)) return uiSet;

            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) raw;
            Set<String> existingCodes = new HashSet<>();
            for (Object item : list) extractCode(item, existingCodes);

            ClassLoader cl = uiSet.getClass().getClassLoader();
            int added = 0;
            for (Entry entry : entries) {
                if (existingCodes.contains(entry.code)) continue;
                Object model = buildUiModel(cl, entry);
                if (model != null) {
                    list.add(model);
                    existingCodes.add(entry.code);
                    added++;
                }
            }

            Log.d(TAG, "native ALL picker added " + added + "/" + entries.size() + " Kizu emotes");
            return uiSet;
        } catch (Throwable t) {
            Log.e(TAG, "mergeGlobal failed; native picker is left unchanged", t);
            return uiSet;
        }
    }

    private static List<Entry> loadForChannel(String channelId) {
        try {
            List<Emote> source = EmoteSupport.getAllForChannelForPicker(channelId);
            if (source == null || source.isEmpty()) return java.util.Collections.emptyList();

            List<Entry> out = new ArrayList<>(source.size());
            for (Emote value : source) {
                if (value == null || value.name == null || value.name.isEmpty() ||
                        value.url == null || value.url.isEmpty()) continue;
                out.add(new Entry(value.name, pickerUrl(value.url, value.animated), value.animated));
            }
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "loadForChannel failed", t);
            return java.util.Collections.emptyList();
        }
    }

    private static String pickerUrl(String url, boolean animated) {
        if (!animated || url == null) return url;
        if (url.endsWith(".webp")) return url.substring(0, url.length() - 5) + ".gif";
        return url;
    }

    private static Object buildUiModel(ClassLoader cl, Entry entry) throws Exception {
        Class<?> assetType = Class.forName(T_ASSET, false, cl);
        Class<?> modelKind = Class.forName(T_KIND, false, cl);
        Class<?> modelGeneric = Class.forName(T_MODEL_GENERIC, false, cl);
        Class<?> messageInput = Class.forName(T_MESSAGE_INPUT, false, cl);
        Class<?> clickedUnlocked = Class.forName(T_CLICKED_UNLOCKED, false, cl);
        Class<?> uiModel = Class.forName(T_UI_MODEL, false, cl);
        Class<?> imageDescriptor = Class.forName(T_IMAGE_DESCRIPTOR, false, cl);

        Object asset = enumConstant(assetType, entry.animated ? "ANIMATED" : "STATIC");
        Object kind = enumConstant(modelKind, "OTHER");

        String syntheticId = "KIZU-" + Integer.toHexString(entry.code.hashCode()) + "-" +
                Integer.toHexString(entry.url.hashCode());

        IMAGE_URLS.put(syntheticId, entry.url);

        Object emoteModel = newInstanceMatching(
                modelGeneric, syntheticId, entry.code, asset, kind
        );
        Object input = newInstanceMatching(
                messageInput, entry.code, syntheticId, false
        );
        Object clicked = newInstanceMatching(
                clickedUnlocked, emoteModel, input, null, null, 12, null
        );

        Context context = null;
        Object descriptor = enumConstant(imageDescriptor, "NONE");
        int widthRes = 0;
        int paddingRes = 0;
        if (context != null) {
            widthRes = context.getResources().getIdentifier(
                    "emote_picker_emote_size", "dimen", context.getPackageName());
            paddingRes = context.getResources().getIdentifier(
                    "emote_picker_emote_padding", "dimen", context.getPackageName());
        }

        return newInstanceMatching(
                uiModel,
                syntheticId,
                clicked,
                asset,
                descriptor,
                widthRes,
                paddingRes == 0 ? null : Integer.valueOf(paddingRes)
        );
    }

    private static Method findNoArgMethod(Class<?> cls, String... names) {
        for (String name : names) {
            try {
                Method m = cls.getMethod(name);
                if (m.getParameterCount() == 0) return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findObjectGetterByType(Class<?> cls, String typeName) {
        for (Method method : cls.getMethods()) {
            if (method.getParameterCount() == 0 &&
                    method.getReturnType().getName().equals(typeName)) return method;
        }
        return null;
    }

    private static Method findListReturningGetter(Class<?> cls) {
        for (Method method : cls.getMethods()) {
            if (method.getParameterCount() == 0 &&
                    List.class.isAssignableFrom(method.getReturnType())) return method;
        }
        return null;
    }

    private static void extractCode(Object uiModel, Set<String> out) {
        if (uiModel == null) return;
        try {
            Object clicked = findObjectField(uiModel, "clickedEmote", "b");
            if (clicked == null) return;

            Object input = findObjectField(clicked, "emoteMessageInput");
            if (input == null) {
                for (Method m : clicked.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 &&
                            m.getReturnType().getName().endsWith("EmoteMessageInput")) {
                        input = m.invoke(clicked);
                        break;
                    }
                }
            }
            if (input == null) return;

            Object code = findObjectField(input, "code");
            if (code != null) out.add(String.valueOf(code));
        } catch (Throwable ignored) {}
    }

    private static Object findObjectField(Object owner, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object newInstanceMatching(Class<?> cls, Object... args) throws Exception {
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length != args.length) continue;

            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) {
                    if (types[i].isPrimitive()) {
                        compatible = false;
                        break;
                    }
                    continue;
                }
                if (!wrapPrimitive(types[i]).isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;

            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new NoSuchMethodException(cls.getName() + " has no compatible constructor");
    }

    private static Object enumConstant(Class<?> cls, String name) throws Exception {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) cls, name);
        return value;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static final class Entry {
        final String code;
        final String url;
        final boolean animated;

        Entry(String code, String url, boolean animated) {
            this.code = code;
            this.url = url;
            this.animated = animated;
        }
    }
}
