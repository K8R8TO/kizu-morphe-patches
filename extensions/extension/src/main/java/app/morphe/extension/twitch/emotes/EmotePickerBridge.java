package app.morphe.extension.twitch.emotes;

import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class EmotePickerBridge {
    private static final String TAG = "KizuPicker";
    private static final ConcurrentHashMap<String, String> IMAGE_URLS = new ConcurrentHashMap<>();

    private EmotePickerBridge() {}

    public static String getEmoteUrl(String id) {
        if (id == null || !id.startsWith("KIZU-")) return null;
        return IMAGE_URLS.get(id);
    }

    public static String getEmoteUrl(String id, String ignoredSize) {
        return getEmoteUrl(id);
    }

    /**
     * Called from Twitch's animated picker URL builder. The second argument is
     * intentionally treated as opaque so this hook survives enum obfuscation.
     */
    public static String getAnimatedPickerEmoteUrl(String id, Object ignoredAnimationSetting) {
        return getEmoteUrl(id);
    }

    public static void onPickerOpened(Object ignored) {
        try {
            Log.d(TAG, "picker channel=" + EmoteSupport.getCurrentChannelId());
        } catch (Throwable t) {
            Log.w(TAG, "onPickerOpened failed", t);
        }
    }

    /**
     * Twitch 31.3.1: the presenter returns an EmoteUiSet (obfuscated mtf).
     * We augment Twitch's existing model list so the normal native picker remains
     * responsible for layout, selection and insertion into the chat composer.
     */
    public static Object mergeGlobal(Object uiSet) {
        if (!Settings.EMOTES_PICKER.get() || uiSet == null) return uiSet;
        try {
            List<Entry> entries = loadForChannel(EmoteSupport.getCurrentChannelId());
            if (entries.isEmpty()) return uiSet;

            Method getEmotes = findMethod(uiSet.getClass(), "b");
            if (getEmotes == null) {
                Log.w(TAG, "picker: EmoteUiSet emote-list method not found");
                return uiSet;
            }
            getEmotes.setAccessible(true);

            Object raw = getEmotes.invoke(uiSet);
            if (!(raw instanceof List)) return uiSet;

            List<?> original = (List<?>) raw;
            ArrayList<Object> list = new ArrayList<>(original);

            Object template = findModelTemplate(original);
            if (template == null) {
                Log.w(TAG, "picker: no Twitch emote model template");
                return uiSet;
            }

            Class<?> uiModel = template.getClass();
            Field clickField = declaredField(uiModel, "b");
            Field sizeField = declaredField(uiModel, "e");
            Field paddingField = declaredField(uiModel, "f");

            ClassLoader cl = uiModel.getClassLoader();
            Class<?> assetType = Class.forName("xof", false, cl);
            Class<?> descriptorType = Class.forName("qof", false, cl);
            Object staticAsset = Enum.valueOf((Class<? extends Enum>) assetType, "STATIC");
            Object animatedAsset = Enum.valueOf((Class<? extends Enum>) assetType, "ANIMATED");
            Object staticDescriptor = Enum.valueOf((Class<? extends Enum>) descriptorType, "NONE");
            Object animatedDescriptor = Enum.valueOf((Class<? extends Enum>) descriptorType, "ANIMATED");

            Constructor<?> constructor = findModelConstructor(
                    uiModel, clickField.getType(), assetType, descriptorType
            );

            int added = 0;
            for (Entry entry : entries) {
                String id = syntheticId(entry);
                if (containsModelId(list, id)) continue;

                IMAGE_URLS.put(id, entry.url);
                Object asset = entry.animated ? animatedAsset : staticAsset;
                Object descriptor = entry.animated ? animatedDescriptor : staticDescriptor;
                Object model = constructor.newInstance(
                        id,
                        clickField.get(template),
                        asset,
                        descriptor,
                        sizeField.getInt(template),
                        paddingField.get(template)
                );
                list.add(model);
                added++;
            }

            if (added == 0) return uiSet;

            Method getHeader = findMethod(uiSet.getClass(), "c");
            if (getHeader == null) {
                Log.w(TAG, "picker: EmoteUiSet header method not found");
                return uiSet;
            }
            getHeader.setAccessible(true);

            Object header = getHeader.invoke(uiSet);
            Constructor<?> setConstructor = findSetConstructor(uiSet.getClass(), getHeader.getReturnType());
            if (setConstructor == null) {
                Log.w(TAG, "picker: EmoteUiSet constructor not found");
                return uiSet;
            }
            setConstructor.setAccessible(true);

            Object result = setConstructor.newInstance(header, list);
            Log.d(TAG, "picker added " + added + "/" + entries.size());
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "mergeGlobal failed", t);
            return uiSet;
        }
    }

    private static Object findModelTemplate(List<?> original) {
        for (Object item : original) {
            if (item == null) continue;
            if ("mtf".equals(item.getClass().getName())) return item;
            try {
                declaredField(item.getClass(), "b");
                declaredField(item.getClass(), "e");
                declaredField(item.getClass(), "f");
                return item;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean containsModelId(List<?> list, String id) {
        for (Object item : list) {
            if (item == null) continue;
            try {
                Field idField = declaredField(item.getClass(), "a");
                Object value = idField.get(item);
                if (id.equals(value)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Constructor<?> findSetConstructor(Class<?> setClass, Class<?> headerType) {
        for (Constructor<?> c : setClass.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 &&
                    p[0].isAssignableFrom(headerType) &&
                    List.class.isAssignableFrom(p[1])) {
                return c;
            }
        }
        return null;
    }

    private static Constructor<?> findModelConstructor(
            Class<?> model,
            Class<?> clickType,
            Class<?> assetType,
            Class<?> descriptorType
    ) throws NoSuchMethodException {
        for (Constructor<?> c : model.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 6 &&
                    p[0] == String.class &&
                    p[1].isAssignableFrom(clickType) &&
                    p[2].isAssignableFrom(assetType) &&
                    p[3].isAssignableFrom(descriptorType) &&
                    p[4] == int.class &&
                    p[5] == Integer.class) {
                c.setAccessible(true);
                return c;
            }
        }
        throw new NoSuchMethodException("Twitch EmoteUiModel constructor");
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String syntheticId(Entry entry) {
        return "KIZU-" + Integer.toHexString(entry.code.hashCode()) + "-" +
                Integer.toHexString(entry.url.hashCode());
    }

    private static List<Entry> loadForChannel(String channelId) {
        try {
            List<Emote> source = EmoteSupport.getAllForChannelForPicker(channelId);
            if (source == null || source.isEmpty()) return java.util.Collections.emptyList();
            List<Entry> out = new ArrayList<>(source.size());
            for (Emote value : source) {
                if (value != null && value.name != null && !value.name.isEmpty() &&
                        value.url != null && !value.url.isEmpty()) {
                    out.add(new Entry(value.name, pickerUrl(value.url, value.animated), value.animated));
                }
            }
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "loadForChannel failed", t);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Twitch's native picker recognizes our injected model as animated, but its
     * image pipeline does not reliably decode 7TV's animated WebP assets. 7TV
     * publishes equivalent GIF assets for animated emotes, so use GIF only for
     * the native picker. The chat renderer keeps its existing WebP path untouched.
     */
    private static String pickerUrl(String url, boolean animated) {
        if (!animated || url == null) return url;
        if (url.endsWith(".webp")) {
            return url.substring(0, url.length() - 5) + ".gif";
        }
        return url;
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
