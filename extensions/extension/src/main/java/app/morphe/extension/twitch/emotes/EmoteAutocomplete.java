package app.morphe.extension.twitch.emotes;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * PurpleTV-style independent emote suggestion strip.
 *
 * PurpleTV uses Xposed to observe MultiAutoCompleteTextView construction. Morphe cannot hook
 * Android framework constructors, so Kizu discovers Twitch's resource-named "message_input"
 * from the existing chat-row bind hook and wires the same TextWatcher/popup behavior there.
 *
 * This path intentionally does not inspect or mutate Twitch's private autocomplete provider.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class EmoteAutocomplete {
    private static final String TAG = "KizuEmoteAutocomplete";
    private static final String CHAT_INPUT_RESOURCE = "message_input";
    private static final int MIN_PREFIX_LENGTH = 2;
    private static final int MAX_SUGGESTIONS = 30;
    private static final long DISCOVERY_TIMEOUT_MS = 30_000L;
    private static final long DISCOVERY_RETRY_DELAY_MS = 300L;
    private static final long DISCOVERY_SCAN_THROTTLE_MS = 100L;
    private static final long STRIP_TIMEOUT_MS = 4_000L;

    private static final Set<EditText> WIRED =
            Collections.newSetFromMap(new WeakHashMap<EditText, Boolean>());
    private static final WeakHashMap<View, DiscoveryState> DISCOVERIES = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<EditText>> ROOT_INPUTS = new WeakHashMap<>();
    private static final Set<Application> OBSERVED_APPLICATIONS =
            Collections.newSetFromMap(new WeakHashMap<Application, Boolean>());
    private static final CopyOnWriteArrayList<WeakReference<InputState>> STATES =
            new CopyOnWriteArrayList<>();

    private EmoteAutocomplete() { }

    /**
     * Application-level discovery starts before any chat row is bound. Chat-row binding remains
     * a fallback for Twitch screens whose chat composer is inflated after Activity resume.
     */
    public static void startActivityDiscovery(Context context) {
        try {
            if (context == null) return;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return;
            Application app = (Application) appContext;
            boolean register;
            synchronized (OBSERVED_APPLICATIONS) {
                register = OBSERVED_APPLICATIONS.add(app);
            }
            if (register) {
                app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {
                        discoverActivity(activity, "created");
                    }
                    @Override public void onActivityStarted(Activity activity) { }
                    @Override public void onActivityResumed(Activity activity) {
                        discoverActivity(activity, "resumed");
                    }
                    @Override public void onActivityPaused(Activity activity) { }
                    @Override public void onActivityStopped(Activity activity) { }
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                    @Override public void onActivityDestroyed(Activity activity) { }
                });
                Log.i(TAG, "Registered early Activity lifecycle discovery");
            }

            if (Settings.EMOTES_THIRD_PARTY.get() && Settings.EMOTES_AUTOCOMPLETE.get()) {
                long started = SystemClock.elapsedRealtime();
                NativeEmoteCatalog.snapshot(); // force the bundled native list to initialize at startup
                NativeEmoteCatalog.ensureLoaded(app);
                EmoteSupport.ensureAutocompleteCatalog(app); // requests run on Kizu's worker pool
                Log.i(TAG, "Autocomplete catalogs primed in " +
                        (SystemClock.elapsedRealtime() - started) + "ms; native entries=" +
                        NativeEmoteCatalog.size());
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not start early Activity discovery", error);
        }
    }

    private static void discoverActivity(Activity activity, String phase) {
        try {
            if (activity == null || !Settings.EMOTES_THIRD_PARTY.get() ||
                    !Settings.EMOTES_AUTOCOMPLETE.get()) return;
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (decor != null) startDiscovery(decor, "activity-" + phase);
        } catch (Throwable error) {
            Log.d(TAG, "Activity discovery skipped during " + phase, error);
        }
    }

    /**
     * Chat-row fallback. A discovery observer stays attached to the root and reacts to later layout
     * passes, so it does not need another message-row bind if Twitch inflates the composer later.
     */
    public static void attachFromChatRow(TextView chatRow) {
        try {
            if (chatRow == null) return;
            View root = chatRow.getRootView();
            if (root != null) startDiscovery(root, "chat-row");
        } catch (Throwable error) {
            Log.w(TAG, "Could not queue chat-input discovery", error);
        }
    }

    private static void startDiscovery(View root, String source) {
        if (root == null) return;
        synchronized (ROOT_INPUTS) {
            WeakReference<EditText> reference = ROOT_INPUTS.get(root);
            EditText previous = reference == null ? null : reference.get();
            if (previous != null && previous.isAttachedToWindow() && previous.isShown()) return;
            ROOT_INPUTS.remove(root);
        }

        DiscoveryState state;
        synchronized (DISCOVERIES) {
            state = DISCOVERIES.get(root);
            if (state == null) {
                state = new DiscoveryState(root);
                DISCOVERIES.put(root, state);
                state.start(source);
                return;
            }
        }
        state.scan(source + "-retry");
    }

    private static EditText findChatInput(View view) {
        if (view == null) return null;
        if (view instanceof EditText && isChatInput(view)) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            EditText match = findChatInput(group.getChildAt(index));
            if (match != null) return match;
        }
        return null;
    }

    private static boolean isChatInput(View view) {
        try {
            int id = view.getId();
            return id != View.NO_ID &&
                    CHAT_INPUT_RESOURCE.equals(view.getResources().getResourceEntryName(id));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void attach(EditText input) {
        synchronized (WIRED) {
            if (!WIRED.add(input)) return;
        }
        long attachedAt = SystemClock.elapsedRealtime();
        InputState state = new InputState(input, attachedAt);
        STATES.add(new WeakReference<>(state));
        input.addTextChangedListener(state.watcher);
        input.addOnAttachStateChangeListener(state.attachListener);
        try {
            NativeEmoteCatalog.snapshot();
            NativeEmoteCatalog.ensureLoaded(input.getContext());
            EmoteSupport.ensureAutocompleteCatalog(input.getContext());
        } catch (Throwable error) {
            Log.d(TAG, "Autocomplete prewarm skipped", error);
        }
        Log.i(TAG, "Input listener attached; class=" + input.getClass().getName() +
                " id=" + input.getId() + " since-process-ms=" + attachedAt +
                " native-entries=" + NativeEmoteCatalog.size());
        input.post(state::refreshOnMain);
    }

    /** Called after catalogs load or the current channel changes. */
    static void refreshAll() {
        for (WeakReference<InputState> reference : STATES) {
            InputState state = reference.get();
            if (state == null) {
                STATES.remove(reference);
            } else {
                state.postRefresh();
            }
        }
    }

    /** Called by Kizu's shared image loader when a suggestion image becomes available. */
    static void onImageLoaded(String url) {
        if (url != null) refreshAll();
    }

    /**
     * Retain Twitch's native trigger adjustment, but no longer patch its private suggestion
     * provider. The custom strip is populated independently from Kizu's third-party catalog.
     */
    /**
     * True when the Kizu strip should own this token. The bytecode hook uses this to suppress
     * Twitch's competing vertical emote dropdown. Mentions and slash commands retain native UI.
     */
    public static boolean shouldOpenAutocomplete(CharSequence text) {
        if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() || text == null) {
            return false;
        }
        String value = text.toString();
        int start = Math.max(value.lastIndexOf(' '), value.lastIndexOf('\n')) + 1;
        String token = value.substring(start);
        if (token.isEmpty() || token.charAt(0) == '@' || token.charAt(0) == '/') return false;
        if (token.charAt(0) == ':') token = token.substring(1);
        return token.length() >= MIN_PREFIX_LENGTH;
    }

    /** Return the active token start, skipping an optional colon when Kizu owns the search. */
    public static int getBareAutocompleteStart(CharSequence text) {
        if (!shouldOpenAutocomplete(text)) return -1;
        String value = text.toString();
        int start = Math.max(value.lastIndexOf(' '), value.lastIndexOf('\n')) + 1;
        return start < value.length() && value.charAt(start) == ':' ? start + 1 : start;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    private static final class DiscoveryState
            implements ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        private final WeakReference<View> rootReference;
        private final long startedAt = SystemClock.elapsedRealtime();
        private final Runnable retry = () -> {
            retryPending = false;
            scan("timer");
        };
        private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this;
        private long lastScanAt;
        private int attempts;
        private boolean retryPending;
        private boolean finished;

        DiscoveryState(View root) {
            rootReference = new WeakReference<>(root);
        }

        void start(String source) {
            View root = rootReference.get();
            if (root == null) {
                finish("root-gone");
                return;
            }
            try {
                root.addOnAttachStateChangeListener(this);
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(layoutListener);
                Log.i(TAG, "Input discovery started source=" + source +
                        " root=" + root.getClass().getName());
                scan("initial");
            } catch (Throwable error) {
                Log.w(TAG, "Could not observe root for input discovery", error);
                finish("observer-error");
            }
        }

        @Override public void onGlobalLayout() {
            View root = rootReference.get();
            if (root != null && retryPending) {
                root.removeCallbacks(retry);
                retryPending = false;
            }
            scan("layout");
        }

        @Override public void onViewAttachedToWindow(View view) {
            scan("root-attached");
        }

        @Override public void onViewDetachedFromWindow(View view) {
            finish("root-detached");
        }

        void scan(String source) {
            if (finished) return;
            View root = rootReference.get();
            if (root == null) {
                finish("root-gone");
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastScanAt < DISCOVERY_SCAN_THROTTLE_MS) {
                scheduleRetry(root);
                return;
            }
            lastScanAt = now;
            attempts++;
            try {
                EditText input = findChatInput(root);
                if (input != null) {
                    synchronized (ROOT_INPUTS) {
                        ROOT_INPUTS.put(root, new WeakReference<>(input));
                    }
                    Log.i(TAG, "Input discovered source=" + source + " attempts=" + attempts +
                            " elapsed-ms=" + (now - startedAt));
                    finish("found");
                    attach(input);
                    return;
                }
            } catch (Throwable error) {
                Log.d(TAG, "Input scan failed at attempt=" + attempts, error);
            }

            if (now - startedAt >= DISCOVERY_TIMEOUT_MS) {
                Log.i(TAG, "Input discovery timed out after " + (now - startedAt) +
                        "ms; attempts=" + attempts + " source=" + source);
                finish("timeout");
            } else {
                scheduleRetry(root);
            }
        }

        private void scheduleRetry(View root) {
            if (finished || retryPending) return;
            retryPending = true;
            if (!root.postDelayed(retry, DISCOVERY_RETRY_DELAY_MS)) {
                retryPending = false;
                finish("retry-post-failed");
            }
        }

        private void finish(String reason) {
            if (finished) return;
            finished = true;
            View root = rootReference.get();
            if (root != null) {
                if (retryPending) root.removeCallbacks(retry);
                root.removeOnAttachStateChangeListener(this);
                try {
                    ViewTreeObserver observer = root.getViewTreeObserver();
                    if (observer.isAlive()) observer.removeOnGlobalLayoutListener(layoutListener);
                } catch (Throwable ignored) { }
                synchronized (DISCOVERIES) {
                    if (DISCOVERIES.get(root) == this) DISCOVERIES.remove(root);
                }
            }
            retryPending = false;
        }
    }

    private static final class InputState {
        private final WeakReference<EditText> inputReference;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final long attachedAt;
        private PopupWindow popup;
        private LinearLayout stripRow;
        private HorizontalScrollView stripScroll;
        private View popupRoot;
        private int tokenStart;
        private int tokenEnd;
        private int activePrefixLength;
        private long firstTextAt;
        private boolean firstTextLogged;
        private boolean firstNativeQueryLogged;
        private boolean firstCatalogQueryLogged;
        private boolean firstPopupLogged;
        private List<Emote> visibleSuggestions = Collections.emptyList();

        private final Runnable autoDismiss = this::hidePopup;

        private final TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                if (!firstTextLogged) {
                    firstTextLogged = true;
                    firstTextAt = SystemClock.elapsedRealtime();
                    Log.i(TAG, "First composer text change arrived " +
                            (firstTextAt - attachedAt) + "ms after listener attachment");
                }
                refreshOnMain();
            }
        };

        private final View.OnAttachStateChangeListener attachListener =
                new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) { postRefresh(); }
                    @Override public void onViewDetachedFromWindow(View view) {
                        handler.removeCallbacks(autoDismiss);
                        hidePopup();
                    }
                };

        InputState(EditText input, long attachedAt) {
            inputReference = new WeakReference<>(input);
            this.attachedAt = attachedAt;
        }

        void postRefresh() {
            EditText input = inputReference.get();
            if (input == null) return;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                refreshOnMain();
            } else {
                input.post(this::refreshOnMain);
            }
        }

        private void scheduleDismiss() {
            handler.removeCallbacks(autoDismiss);
            handler.postDelayed(autoDismiss, STRIP_TIMEOUT_MS);
        }

        void refreshOnMain() {
            EditText input = inputReference.get();
            if (input == null) {
                hidePopup();
                return;
            }
            try {
                if (!input.isAttachedToWindow() || !input.isShown() ||
                        !Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get()) {
                    hidePopup();
                    return;
                }

                Editable editable = input.getText();
                String text = editable == null ? "" : editable.toString();
                int cursor = input.getSelectionStart();
                if (cursor < 0) cursor = text.length();
                cursor = Math.max(0, Math.min(cursor, text.length()));

                int start = cursor;
                while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
                int end = cursor;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
                String rawToken = text.substring(start, end);
                // Keep Twitch's native UI for mentions and slash commands. For emote names,
                // accept bare text ("Kappa") and colon-prefixed text (":Kappa") in one strip.
                if (rawToken.startsWith("@") || rawToken.startsWith("/")) {
                    hidePopup();
                    return;
                }
                String token = rawToken.startsWith(":") ? rawToken.substring(1) : rawToken;
                if (token.length() < MIN_PREFIX_LENGTH) {
                    hidePopup();
                    return;
                }

                tokenStart = start;
                tokenEnd = end;
                activePrefixLength = token.length();
                Context context = input.getContext();

                // Do a pure local native match first. It is independent of all network catalogs,
                // so a native suggestion can be displayed even if every provider is still loading.
                long nativeStarted = SystemClock.elapsedRealtime();
                List<Emote> nativeMatches = EmoteSupport.autocompleteNative(token, MAX_SUGGESTIONS);
                long nativeElapsed = SystemClock.elapsedRealtime() - nativeStarted;
                if (!firstNativeQueryLogged) {
                    firstNativeQueryLogged = true;
                    Log.i(TAG, "First native prefix match took " + nativeElapsed +
                            "ms; catalog-size=" + NativeEmoteCatalog.size() +
                            " matches=" + (nativeMatches == null ? 0 : nativeMatches.size()));
                }
                if (nativeMatches != null && !nativeMatches.isEmpty()) {
                    visibleSuggestions = new ArrayList<>(nativeMatches);
                    showStrip(input, visibleSuggestions);
                    scheduleDismiss();
                }

                // Start or refresh provider loading only after the local suggestions have had a
                // chance to populate the strip. ensureLoaded only schedules background work.
                EmoteSupport.ensureAutocompleteCatalog(context);
                long catalogStarted = SystemClock.elapsedRealtime();
                List<Emote> matches = EmoteSupport.autocomplete(
                        EmoteSupport.getCurrentChannelId(), token, MAX_SUGGESTIONS);
                long catalogElapsed = SystemClock.elapsedRealtime() - catalogStarted;
                if (!firstCatalogQueryLogged) {
                    firstCatalogQueryLogged = true;
                    Log.i(TAG, "First merged autocomplete match took " + catalogElapsed +
                            "ms; matches=" + (matches == null ? 0 : matches.size()));
                }
                if (matches != null && !matches.isEmpty()) {
                    visibleSuggestions = new ArrayList<>(matches);
                    showStrip(input, visibleSuggestions);
                    scheduleDismiss();
                } else if (nativeMatches == null || nativeMatches.isEmpty()) {
                    visibleSuggestions = Collections.emptyList();
                    hidePopup();
                }
            } catch (Throwable error) {
                Log.w(TAG, "Standalone autocomplete update failed", error);
                hidePopup();
            }
        }

        private void showStrip(EditText input, List<Emote> suggestions) {
            Context context = input.getContext();
            int stripHeight = dp(context, 44f);
            int imageSize = dp(context, 26f);

            // Reuse the same PopupWindow and scroll container. Updating the children in-place
            // avoids a dismiss/show cycle on each keystroke and provider/image callback.
            if (stripRow == null || stripScroll == null || popup == null) {
                stripRow = new LinearLayout(context);
                stripRow.setOrientation(LinearLayout.HORIZONTAL);
                stripRow.setGravity(Gravity.CENTER_VERTICAL);
                stripRow.setPadding(dp(context, 4f), dp(context, 3f), dp(context, 4f), dp(context, 3f));

                stripScroll = new HorizontalScrollView(context);
                stripScroll.setHorizontalScrollBarEnabled(false);
                stripScroll.setFillViewport(false);
                stripScroll.setBackgroundColor(Color.parseColor("#FF1F1925"));
                stripScroll.addView(stripRow, new HorizontalScrollView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

                popup = new PopupWindow(stripScroll, ViewGroup.LayoutParams.MATCH_PARENT,
                        stripHeight, false);
                popup.setOutsideTouchable(false);
                popup.setTouchable(true);
                popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                popup.setClippingEnabled(false);
                popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            } else {
                stripRow.removeAllViews();
            }

            for (Emote emote : suggestions) {
                if (emote == null || emote.name == null) continue;
                LinearLayout chip = new LinearLayout(context);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setPadding(dp(context, 6f), dp(context, 3f), dp(context, 6f), dp(context, 3f));
                chip.setClickable(true);
                chip.setOnClickListener(view -> replaceToken(input, emote.name));

                Drawable drawable = EmoteSupport.autocompleteDrawable(input.getResources(), emote);
                if (drawable == null) {
                    EmoteSupport.requestAutocompleteImage(context, emote, imageSize);
                } else {
                    ImageView image = new ImageView(context);
                    image.setImageDrawable(drawable);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams imageParams =
                            new LinearLayout.LayoutParams(imageSize, imageSize);
                    imageParams.rightMargin = dp(context, 4f);
                    chip.addView(image, imageParams);
                }

                TextView name = new TextView(context);
                name.setText(emote.name);
                name.setTextColor(Color.WHITE);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                chip.addView(name);
                stripRow.addView(chip);
            }

            View root = input.getRootView();
            try {
                int[] location = new int[2];
                input.getLocationInWindow(location);
                int rootHeight = root.getHeight() > 0
                        ? root.getHeight() : context.getResources().getDisplayMetrics().heightPixels;
                int y = Math.max(dp(context, 4f), location[1] - stripHeight);
                y = Math.min(y, Math.max(dp(context, 4f), rootHeight - stripHeight));

                boolean wasShowing = popup.isShowing();
                if (!wasShowing) {
                    popupRoot = root;
                    popup.showAtLocation(root, Gravity.TOP | Gravity.START, 0, y);
                } else {
                    popup.update(0, y, ViewGroup.LayoutParams.MATCH_PARENT, stripHeight);
                }
                if (!firstPopupLogged) {
                    firstPopupLogged = true;
                    long now = SystemClock.elapsedRealtime();
                    long sinceFirstText = firstTextLogged ? now - firstTextAt : -1L;
                    Log.i(TAG, "First autocomplete strip displayed " + sinceFirstText +
                            "ms after first text change; prefix-length=" + activePrefixLength);
                }
            } catch (Throwable error) {
                Log.w(TAG, "Could not display autocomplete strip", error);
            }
        }

        private void replaceToken(EditText input, String name) {
            try {
                Editable editable = input.getText();
                if (editable == null) return;
                int start = Math.max(0, Math.min(tokenStart, editable.length()));
                int end = Math.max(start, Math.min(tokenEnd, editable.length()));
                String insert = name + " ";
                editable.replace(start, end, insert);
                int selection = Math.min(editable.length(), start + insert.length());
                input.setSelection(selection);
            } catch (Throwable error) {
                Log.w(TAG, "Could not insert selected emote", error);
            } finally {
                handler.removeCallbacks(autoDismiss);
                hidePopup();
            }
        }

        private void hidePopup() {
            try {
                if (popup != null && popup.isShowing()) popup.dismiss();
            } catch (Throwable ignored) { }
        }
    }
}
