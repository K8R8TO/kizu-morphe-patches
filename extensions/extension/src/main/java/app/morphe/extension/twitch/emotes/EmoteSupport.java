package app.morphe.extension.twitch.emotes;

import android.util.Log;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.method.LinkMovementMethod;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import app.morphe.extension.twitch.chat.MentionSupport;
import app.morphe.extension.twitch.chat.DeletedMessagesSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.Utils;
import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class EmoteSupport {
    private static final Object LOCK = new Object();
    private static final Pattern TIMESTAMP_TEXT = Pattern.compile(
            "(?:timestamp|createdAt|sentAt|time)\\s*=\\s*([^,}\\)]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final WeakHashMap<TextView, BoundMessage> BOUND_MESSAGES = new WeakHashMap<>();
    private static final EmoteCatalog CATALOG = new EmoteCatalog(EmoteSupport::refreshCatalog);
    private static final EmoteImageLoader IMAGES = new EmoteImageLoader(EmoteSupport::refreshImage);
    private static final NativeEmotePreview PREVIEW = new NativeEmotePreview();
    private static final View.OnAttachStateChangeListener VIEW_LIFECYCLE =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    // Runs on the chat RecyclerView's views; a throw here would be uncaught on the UI
                    // thread and crash Twitch, so keep it contained.
                    try {
                        TextView textView = (TextView) view;
                        BoundMessage message;
                        synchronized (LOCK) {
                            message = BOUND_MESSAGES.get(textView);
                        }
                        if (message != null) {
                            scheduleRender(textView, message);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    try {
                        stopAnimations(((TextView) view).getText());
                    } catch (Throwable ignored) {
                    }
                }
            };

    private static volatile String lastRoomId;
    private static volatile String lastRoomName;

    private EmoteSupport() {
    }

    public static void init(android.content.Context context) {
        // Runtime initialization is driven by chat row binding; keep this entry point for the
        // common Morphe extension bootstrap.
    }

    public static String getCurrentChannelId() {
        return lastRoomId;
    }

    public static java.util.List<Emote> getAllForChannel(String channelId) {
        try {
            if (channelId != null && Utils.getContext() != null) {
                CATALOG.ensureLoaded(Utils.getContext(), channelId);
            }
            return CATALOG.getAllForChannel(channelId);
        } catch (Throwable ignored) {
            return java.util.Collections.emptyList();
        }
    }

    // Patched into Twitch's ChannelChatConnectionKey constructor. That constructor has no handler of
    // its own, so a throw here would abort chat-connection setup; never let anything escape. (#196)
    public static void onChannelChanged(String channelId, String channelName) {
        try {
            String normalized = normalizeChannelId(channelId);
            if (normalized != null) {
                lastRoomId = normalized;
                lastRoomName = channelName;
                android.content.Context context = Utils.getContext();
                if (context != null) {
                    EmoteAutocomplete.startActivityDiscovery(context);
                    if (Settings.EMOTES_THIRD_PARTY.get()) CATALOG.ensureLoaded(context, normalized);
                    if (Settings.EMOTES_AUTOCOMPLETE.get()) NativeEmoteCatalog.ensureLoaded(context);
                }
                EmoteAutocomplete.refreshAll();
            }
        } catch (Throwable ignored) {
        }
    }

    static EmoteCatalog pickerCatalog() { return CATALOG; }

    static android.graphics.drawable.Drawable autocompleteDrawable(
            android.content.res.Resources resources, Emote emote) {
        try {
            return IMAGES.createDrawable(resources, emote);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void requestAutocompleteImage(android.content.Context context, Emote emote, int targetHeight) {
        try {
            if (context != null && emote != null) IMAGES.request(context, emote, targetHeight);
        } catch (Throwable ignored) {
        }
    }

    static void ensureAutocompleteCatalog(android.content.Context context) {
        try {
            if (context != null && Settings.EMOTES_THIRD_PARTY.get() && Settings.EMOTES_AUTOCOMPLETE.get()) {
                NativeEmoteCatalog.ensureLoaded(context);
                CATALOG.ensureLoaded(context, lastRoomId);
            }
        } catch (Throwable ignored) { }
    }

    /** Fast local-only query used before provider/network catalog work. */
    static List<Emote> autocompleteNative(String prefix, int limit) {
        try {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() ||
                    prefix == null || prefix.length() < 2 || limit <= 0) {
                return Collections.emptyList();
            }
            String query = prefix.toLowerCase(Locale.ROOT);
            LinkedHashMap<String, Emote> starts = new LinkedHashMap<>();
            LinkedHashMap<String, Emote> contains = new LinkedHashMap<>();
            for (Emote emote : NativeEmoteCatalog.snapshot()) {
                if (emote == null || emote.name == null) continue;
                String name = emote.name.toLowerCase(Locale.ROOT);
                if (name.startsWith(query)) starts.putIfAbsent(name, emote);
                else if (name.contains(query)) contains.putIfAbsent(name, emote);
            }

            ArrayList<Emote> result = new ArrayList<>(Math.min(limit, starts.size() + contains.size()));
            for (Emote emote : starts.values()) {
                if (result.size() >= limit) break;
                result.add(emote);
            }
            for (Emote emote : contains.values()) {
                if (result.size() >= limit) break;
                result.add(emote);
            }
            return result;
        } catch (Throwable error) {
            Log.w("KizuEmoteSupport", "Native autocomplete lookup failed", error);
            return Collections.emptyList();
        }
    }

    static List<Emote> autocomplete(String channelId, String prefix, int limit) {
        try {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() ||
                    prefix == null || prefix.length() < 2 || limit <= 0) return Collections.emptyList();
            String query = prefix.toLowerCase(Locale.ROOT);
            LinkedHashMap<String, Emote> starts = new LinkedHashMap<>();
            LinkedHashMap<String, Emote> contains = new LinkedHashMap<>();
            // Seed suggestions from the bundled Twitch-native catalog before the network catalogs.
            // Native entries are inserted first, so same-name collisions resolve to Twitch's emote.
            for (Emote emote : NativeEmoteCatalog.snapshot()) {
                if (emote == null || emote.name == null) continue;
                String name = emote.name.toLowerCase(Locale.ROOT);
                if (name.startsWith(query)) starts.putIfAbsent(name, emote);
                else if (name.contains(query)) contains.putIfAbsent(name, emote);
            }
            String[] scopes = channelId == null ? new String[] {null} : new String[] {channelId, null};
            for (int provider = 0; provider < 3; provider++) {
                if (!providerEnabled(provider)) continue;
                for (String scope : scopes) {
                    for (Emote emote : CATALOG.providerSnapshot(scope, provider).values()) {
                        if (emote == null || emote.name == null) continue;
                        String name = emote.name.toLowerCase(Locale.ROOT);
                        String key = name;
                        if (name.startsWith(query)) starts.putIfAbsent(key, emote);
                        else if (name.contains(query)) contains.putIfAbsent(key, emote);
                    }
                }
            }
            ArrayList<Emote> result = new ArrayList<>(Math.min(limit, starts.size() + contains.size()));
            result.addAll(starts.values().stream().limit(limit).collect(java.util.stream.Collectors.toList()));
            for (Emote emote : contains.values()) {
                if (result.size() >= limit) break;
                result.add(emote);
            }
            return result;
        } catch (Throwable error) {
            Log.w("KizuEmoteSupport", "Autocomplete catalog lookup failed for prefix='" +
                    prefix + "' and channelId=" + channelId, error);
            return Collections.emptyList();
        }
    }
    static String autocompleteDiagnostics(String channelId, String prefix) {
        return CATALOG.diagnostics(channelId, prefix);
    }

    /**
     * Returns all enabled third-party emotes for Twitch's native autocomplete backing map.
     */
    static List<Emote> autocompleteAll() {
        try {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get()) {
                return Collections.emptyList();
            }
            String channelId = lastRoomId;
            android.content.Context context = Utils.getContext();
            if (context != null) CATALOG.ensureLoaded(context, channelId);

            LinkedHashMap<String, Emote> merged = new LinkedHashMap<>();
            for (int provider = 0; provider < 3; provider++) {
                if (!providerEnabled(provider)) continue;
                String[] scopes = channelId == null
                        ? new String[] {null}
                        : new String[] {channelId, null};
                for (String scope : scopes) {
                    for (Emote emote : CATALOG.providerSnapshot(scope, provider).values()) {
                        merged.putIfAbsent(emote.name, emote);
                    }
                }
            }
            return new ArrayList<>(merged.values());
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static void preview(TextView textView, Emote emote) {
        try {
            if (textView == null || emote == null || !textView.isAttachedToWindow() || !textView.isShown() ||
                    !Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_TAP_PREVIEW.get()) return;
            BoundMessage message;
            synchronized (LOCK) { message = BOUND_MESSAGES.get(textView); }
            if (message == null) return;
            Emote current = CATALOG.find(message.channelId, emote.name);
            if (current == null || !current.url.equals(emote.url)) return;
            android.graphics.drawable.Drawable drawable = IMAGES.createDrawable(textView.getResources(), current);
            if (drawable != null) PREVIEW.open(textView, current, drawable);
        } catch (Throwable ignored) { }
    }

    static boolean pickerEnabled() {
        return Settings.EMOTES_THIRD_PARTY.get() && Settings.EMOTES_PICKER.get();
    }

    static boolean providerEnabled(int provider) {
        if (!Settings.EMOTES_THIRD_PARTY.get()) return false;
        if (provider == 0) return Settings.EMOTES_BTTV.get();
        if (provider == 1) return Settings.EMOTES_7TV.get();
        if (provider == 2) return Settings.EMOTES_FFZ.get();
        return false;
    }

    static void ensurePickerCatalog(String channelId) {
        try {
            android.content.Context context = Utils.getContext();
            if (context != null && pickerEnabled()) CATALOG.ensureLoaded(context, channelId);
        } catch (Throwable ignored) { }
    }

    static String currentChannelName(String channelId) {
        return channelId != null && channelId.equals(lastRoomId) ? lastRoomName : null;
    }
    // Patched in right after Twitch's own setText in the chat-row binder (udc.D). That binder runs
    // inside RecyclerView.onBindViewHolder and has no try/catch, so anything thrown here unwinds
    // through the adapter and takes out the whole chat list and message input. This hook must never
    // throw. (#196)
    public static void bind(TextView textView) {
        bind(null, textView, null);
    }

    public static void bind(Object messageModel, TextView textView) {
        bind(messageModel, textView, null);
    }

    public static void bind(TextView textView, String sourceChannelId) {
        bind(null, textView, sourceChannelId);
    }

    public static void bind(Object messageModel, TextView textView, String sourceChannelId) {
        if (textView == null) {
            return;
        }
        try {
            EmoteAutocomplete.attachFromChatRow(textView);
        } catch (Throwable ignored) {
        }
        try {
            bindInternal(messageModel, textView, sourceChannelId);
        } catch (Throwable ignored) {
            forget(textView);
        }
    }

    private static void forget(TextView textView) {
        try {
            synchronized (LOCK) {
                BOUND_MESSAGES.remove(textView);
            }
            MentionSupport.forget(textView);
            textView.removeOnAttachStateChangeListener(VIEW_LIFECYCLE);
        } catch (Throwable ignored) {
        }
    }

    private static void bindInternal(Object messageModel, TextView textView, String sourceChannelId) {
        DeletedMessagesSupport.onRowBound(messageModel, textView);
        CharSequence current = textView.getText();
        synchronized (LOCK) {
            BOUND_MESSAGES.remove(textView);
        }
        MentionSupport.bind(messageModel, textView);
        stopAnimations(current);
        textView.removeOnAttachStateChangeListener(VIEW_LIFECYCLE);

        if (current == null || current.length() == 0) {
            return;
        }
        String channelId = normalizeChannelId(sourceChannelId);
        if (channelId == null) {
            // Normal non-shared-chat rows can omit sourceChannelId. Twitch 29.9.1 presents one active
            // live-chat room, so use the latest connection key only for that missing-field case.
            channelId = lastRoomId;
        }

        CharSequence timestamped = applyTimestamp(current, messageModel);
        BoundMessage message = new BoundMessage(SpannedString.valueOf(timestamped), channelId);
        synchronized (LOCK) {
            BOUND_MESSAGES.put(textView, message);
        }
        textView.addOnAttachStateChangeListener(VIEW_LIFECYCLE);
        CATALOG.ensureLoaded(textView.getContext(), channelId);
        render(textView, message);
    }

    private static CharSequence applyTimestamp(CharSequence original, Object messageModel) {
        if (!Settings.CHAT_TIMESTAMPS.get() || original == null || original.length() == 0) {
            return original;
        }
        try {
            String timestamp = extractTimestamp(messageModel);
            if (timestamp == null || timestamp.isEmpty()) return original;
            String plain = original.toString();
            if (plain.matches("^\\s*\\[?\\d{1,2}:\\d{2}(?:[:.]\\d{2})?(?:\\s?[APap][Mm])?\\]?\\s+.*$")) {
                return original;
            }
            SpannableStringBuilder result = new SpannableStringBuilder();
            result.append(timestamp);
            result.append("  ");
            result.append(original);
            return result;
        } catch (Throwable ignored) {
            return original;
        }
    }

    private static String extractTimestamp(Object model) {
        if (model == null) return null;
        String[] names = {"getTimestamp", "timestamp", "getCreatedAt", "createdAt", "getSentAt", "sentAt", "getTime", "time"};
        for (String name : names) {
            try {
                Method method = model.getClass().getMethod(name);
                String value = formatTimestampValue(method.invoke(model));
                if (value != null) return value;
            } catch (Throwable ignored) {}
        }
        for (String name : names) {
            try {
                Field field = model.getClass().getDeclaredField(name);
                field.setAccessible(true);
                String value = formatTimestampValue(field.get(model));
                if (value != null) return value;
            } catch (Throwable ignored) {}
        }
        try {
            Matcher matcher = TIMESTAMP_TEXT.matcher(String.valueOf(model));
            if (matcher.find()) return formatTimestampValue(matcher.group(1).trim());
        } catch (Throwable ignored) {}
        return null;
    }

    private static String formatTimestampValue(Object value) {
        if (value == null) return null;
        long millis = -1L;
        if (value instanceof Number) {
            long raw = ((Number) value).longValue();
            millis = raw < 100000000000L ? raw * 1000L : raw;
        } else {
            String raw = String.valueOf(value).trim();
            try {
                long number = Long.parseLong(raw);
                millis = number < 100000000000L ? number * 1000L : number;
            } catch (NumberFormatException ignored) {
                try {
                    java.time.Instant instant = java.time.Instant.parse(raw);
                    millis = instant.toEpochMilli();
                } catch (Throwable parseIgnored) {}
            }
        }
        if (millis <= 0L) return null;
        try {
            String format = Settings.CHAT_TIMESTAMP_FORMAT.get();
            String pattern = "h12".equalsIgnoreCase(format) ? "h:mm a" : "HH:mm";
            return "[" + new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis)) + "]";
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void render(TextView textView, BoundMessage message) {
        // Reached on the UI thread from bind and from posted refreshes; contain any failure so a bad
        // emote/image can never crash Twitch or blank the row.
        try {
            renderInternal(textView, message);
        } catch (Throwable ignored) {
        }
    }

    private static void renderInternal(TextView textView, BoundMessage message) {
        synchronized (LOCK) {
            if (BOUND_MESSAGES.get(textView) != message) {
                return;
            }
        }
        if (!textView.isAttachedToWindow()) {
            return;
        }
        stopAnimations(textView.getText());

        SpannedString original = message.original;
        ReplacementSpan[] existingSpans = original.getSpans(
                0,
                original.length(),
                ReplacementSpan.class
        );
        SpannableStringBuilder builder = null;
        Set<String> pendingImages = null;
        Map<String, Emote> missingImages = null;
        int length = original.length();
        int index = 0;
        boolean hasTapPreview = false;
        while (index < length) {
            while (index < length && isSeparator(original.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < length && !isSeparator(original.charAt(index))) {
                index++;
            }
            int end = index;
            if (start == end || hasReplacementSpan(original, existingSpans, start, end)) {
                continue;
            }

            String token = original.subSequence(start, end).toString();
            Emote emote = CATALOG.find(message.channelId, token);
            if (emote == null) {
                continue;
            }
            android.graphics.drawable.Drawable drawable =
                    IMAGES.createDrawable(textView.getResources(), emote);
            if (drawable == null) {
                if (pendingImages == null) {
                    pendingImages = new HashSet<>();
                    missingImages = new LinkedHashMap<>();
                }
                pendingImages.add(emote.url);
                missingImages.put(emote.url, emote);
                continue;
            }

            if (DeletedMessagesSupport.isGreyDeletedMessage(original)) {
                DeletedMessagesSupport.applyGrayFilter(drawable);
            }

            if (builder == null) {
                builder = new SpannableStringBuilder(original);
            }

            boolean useZeroWidth = emote.zeroWidth && Settings.EMOTES_ZERO_WIDTH.get();
            builder.setSpan(
                    new CenteredImageSpan(textView, drawable, useZeroWidth),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            if (Settings.EMOTES_THIRD_PARTY.get() && Settings.EMOTES_TAP_PREVIEW.get()) {
                builder.setSpan(new EmoteClickSpan(emote), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                hasTapPreview = true;
            }

            // Chat text normally has a whitespace separator before each token. A zero-width
            // emote must also consume that separator, otherwise it is shifted to the right
            // instead of overlaying the previous emote. Make the immediately preceding
            // separator zero-width as well, including for consecutive zero-width emotes.
            if (useZeroWidth && start > 0 && isSeparator(original.charAt(start - 1))) {
                int separatorStart = start - 1;
                while (separatorStart > 0 && isSeparator(original.charAt(separatorStart - 1))) {
                    separatorStart--;
                }
                builder.setSpan(
                        new ZeroWidthSeparatorSpan(),
                        separatorStart,
                        start,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        message.pendingImages = pendingImages == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(pendingImages);

        // Publish pendingImages before enqueueing work. A disk-cache decode can complete immediately on
        // a worker, and its callback must already be able to find this row as a waiter.
        if (missingImages != null) {
            for (Emote emote : missingImages.values()) {
                IMAGES.request(
                        textView.getContext(),
                        emote,
                        Math.max(1, Math.round(textView.getTextSize() * 1.25f))
                );
            }
        }

        if (builder != null) {
            textView.setText(new SpannedString(builder), TextView.BufferType.SPANNABLE);
        } else if (textView.getText() != message.original) {
            textView.setText(message.original, TextView.BufferType.SPANNABLE);
        }
        MentionSupport.apply(textView);
        if (hasTapPreview && textView.getMovementMethod() == null) {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private static boolean hasReplacementSpan(
            Spanned text,
            ReplacementSpan[] spans,
            int start,
            int end
    ) {
        for (ReplacementSpan span : spans) {
            if (text.getSpanStart(span) < end && text.getSpanEnd(span) > start) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSeparator(char value) {
        return Character.isWhitespace(value) || (value >= '⁦' && value <= '⁩');
    }

    private static String normalizeChannelId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 20) {
            return null;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return null;
            }
        }
        return trimmed;
    }

    // Invoked from the catalog/image executor threads. An uncaught throw here would be fatal to the
    // whole app, so keep it contained.
    private static void refreshImage(String url) {
        try {
            EmoteAutocomplete.onImageLoaded(url);
            List<Map.Entry<TextView, BoundMessage>> snapshot = boundMessages();
            for (Map.Entry<TextView, BoundMessage> entry : snapshot) {
                TextView textView = entry.getKey();
                BoundMessage message = entry.getValue();
                if (textView != null && message != null && message.pendingImages.contains(url)) {
                    scheduleRender(textView, message);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void refreshCatalog(String channelId) {
        try {
            EmoteAutocomplete.refreshAll();
            EmotePickerRuntime.refresh(channelId);
            List<Map.Entry<TextView, BoundMessage>> snapshot = boundMessages();
            for (Map.Entry<TextView, BoundMessage> entry : snapshot) {
                TextView textView = entry.getKey();
                BoundMessage message = entry.getValue();
                if (textView == null || message == null) {
                    continue;
                }
                if (channelId == null || channelId.equals(message.channelId)) {
                    scheduleRender(textView, message);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<Map.Entry<TextView, BoundMessage>> boundMessages() {
        synchronized (LOCK) {
            return new ArrayList<>(BOUND_MESSAGES.entrySet());
        }
    }

    private static void scheduleRender(TextView textView, BoundMessage message) {
        synchronized (LOCK) {
            if (BOUND_MESSAGES.get(textView) != message || message.refreshPosted) {
                return;
            }
            message.refreshPosted = true;
        }
        boolean posted = textView.post(() -> {
            synchronized (LOCK) {
                message.refreshPosted = false;
                if (BOUND_MESSAGES.get(textView) != message) {
                    return;
                }
            }
            render(textView, message);
        });
        if (!posted) {
            synchronized (LOCK) {
                message.refreshPosted = false;
            }
        }
    }

    private static final class ZeroWidthSeparatorSpan extends ReplacementSpan {
        @Override
        public int getSize(
                android.graphics.Paint paint,
                CharSequence text,
                int start,
                int end,
                android.graphics.Paint.FontMetricsInt metrics
        ) {
            return 0;
        }

        @Override
        public void draw(
                android.graphics.Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int baseline,
                int bottom,
                android.graphics.Paint paint
        ) {
            // Intentionally empty: the separator is removed from layout while the following
            // zero-width emote is drawn at the previous content's exact endpoint.
        }
    }

    private static void stopAnimations(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) text;
        for (CenteredImageSpan span :
                spanned.getSpans(0, spanned.length(), CenteredImageSpan.class)) {
            span.stop();
        }
    }

    private static final class BoundMessage {
        final SpannedString original;
        final String channelId;
        volatile Set<String> pendingImages = Collections.emptySet();
        boolean refreshPosted;

        BoundMessage(SpannedString original, String channelId) {
            this.original = original;
            this.channelId = channelId;
        }
    }
}


