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

    public static void onPickerOpened(Object ignored) {
        try {
            Log.d(TAG, "picker channel=" + EmoteSupport.getCurrentChannelId());
        } catch (Throwable t) {
            Log.w(TAG, "onPickerOpened failed", t);
        }
    }

    /**
     * Twitch 31.3.1: G2 returns Lmtf;, whose b() is the picker emote-model list.
     * The old bridge targeted pre-31.3.1 fully-qualified model names and therefore
     * silently failed every time.
     */
    public static Object mergeGlobal(Object uiSet) {
        if (!Settings.EMOTES_PICKER.get() || uiSet == null) return uiSet;
        try {
            List<Entry> entries = loadForChannel(EmoteSupport.getCurrentChannelId());
            if (entries.isEmpty()) return uiSet;

            Method getEmotes = uiSet.getClass().getDeclaredMethod("b");
            getEmotes.setAccessible(true);
            Object raw = getEmotes.invoke(uiSet);
            if (!(raw instanceof List)) return uiSet;

            List<?> original = (List<?>) raw;
            ArrayList<Object> list = new ArrayList<>(original);
            Object template = null;
            for (Object item : original) {
                if (item != null && "mtf".equals(item.getClass().getName())) {
                    template = item;
                    break;
                }
            }
            if (template == null) {
                Log.w(TAG, "picker: no Twitch 31.3.1 emote model template");
                return uiSet;
            }

            Class<?> uiModel = template.getClass();
            Field b = declaredField(uiModel, "b");
            Field e = declaredField(uiModel, "e");
            Field f = declaredField(uiModel, "f");

            ClassLoader cl = uiModel.getClassLoader();
            Class<?> assetType = Class.forName("xof", false, cl);
            Class<?> descriptorType = Class.forName("qof", false, cl);
            Object staticAsset = Enum.valueOf((Class<? extends Enum>) assetType, "STATIC");
            Object animatedAsset = Enum.valueOf((Class<? extends Enum>) assetType, "ANIMATED");
            Object staticDescriptor = Enum.valueOf((Class<? extends Enum>) descriptorType, "NONE");
            Object animatedDescriptor = Enum.valueOf((Class<? extends Enum>) descriptorType, "ANIMATED");

            Constructor<?> constructor = findModelConstructor(
                    uiModel, b.getType(), assetType, descriptorType
            );

            int added = 0;
            for (Entry entry : entries) {
                String id = syntheticId(entry);
                IMAGE_URLS.put(id, entry.url);
                Object asset = entry.animated ? animatedAsset : staticAsset;
                Object descriptor = entry.animated ? animatedDescriptor : staticDescriptor;
                Object model = constructor.newInstance(
                        id,
                        b.get(template),
                        asset,
                        descriptor,
                        e.getInt(template),
                        f.get(template)
                );
                list.add(model);
                added++;
            }

            if (added == 0) return uiSet;

            Method getHeader = uiSet.getClass().getDeclaredMethod("c");
            getHeader.setAccessible(true);
            Object header = getHeader.invoke(uiSet);
            Constructor<?> setConstructor = uiSet.getClass().getDeclaredConstructor(
                    getHeader.getReturnType(), List.class
            );
            setConstructor.setAccessible(true);
            Object result = setConstructor.newInstance(header, list);
            Log.d(TAG, "picker added " + added + "/" + entries.size());
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "mergeGlobal failed", t);
            return uiSet;
        }
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
        throw new NoSuchMethodException("Twitch 31.3.1 EmoteUiModel constructor");
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
            List<Emote> source = EmoteSupport.getAllForChannel(channelId);
            if (source == null || source.isEmpty()) return java.util.Collections.emptyList();
            List<Entry> out = new ArrayList<>(source.size());
            for (Emote value : source) {
                if (value != null && value.name != null && !value.name.isEmpty() &&
                        value.url != null && !value.url.isEmpty()) {
                    out.add(new Entry(value.name, value.url, value.animated));
                }
            }
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "loadForChannel failed", t);
            return java.util.Collections.emptyList();
        }
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