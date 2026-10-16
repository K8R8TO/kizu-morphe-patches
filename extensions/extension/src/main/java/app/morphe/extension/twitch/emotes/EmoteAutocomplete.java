package app.morphe.extension.twitch.emotes;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private static final int INPUT_SCAN_ATTEMPTS = 8;
    private static final long INPUT_SCAN_DELAY_MS = 350L;
    private static final long STRIP_TIMEOUT_MS = 4_000L;

    private static final Set<EditText> WIRED =
            Collections.newSetFromMap(new WeakHashMap<EditText, Boolean>());
    private static final Set<View> SCANNING_ROOTS =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final WeakHashMap<View, WeakReference<EditText>> ROOT_INPUTS = new WeakHashMap<>();
    private static final CopyOnWriteArrayList<WeakReference<InputState>> STATES =
            new CopyOnWriteArrayList<>();

    private EmoteAutocomplete() { }

    /**
     * Called when Kizu's chat-row binder runs. We defer to the decor/root view so the input can
     * be found without relying on Twitch's obfuscated autocomplete class names.
     */
    public static void attachFromChatRow(TextView chatRow) {
        try {
            if (chatRow == null) return;
            View root = chatRow.getRootView();
            if (root == null) return;

            synchronized (ROOT_INPUTS) {
                WeakReference<EditText> reference = ROOT_INPUTS.get(root);
                EditText previous = reference == null ? null : reference.get();
                if (previous != null && previous.isAttachedToWindow()) return;
                ROOT_INPUTS.remove(root);
            }

            synchronized (SCANNING_ROOTS) {
                if (!SCANNING_ROOTS.add(root)) return;
            }
            if (!root.post(() -> scanForInput(root, 0))) {
                synchronized (SCANNING_ROOTS) { SCANNING_ROOTS.remove(root); }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not queue chat-input discovery", error);
        }
    }

    private static void scanForInput(View root, int attempt) {
        try {
            EditText input = findChatInput(root);
            if (input != null) {
                attach(input);
                synchronized (ROOT_INPUTS) {
                    ROOT_INPUTS.put(root, new WeakReference<>(input));
                }
                synchronized (SCANNING_ROOTS) { SCANNING_ROOTS.remove(root); }
                return;
            }
            if (attempt < INPUT_SCAN_ATTEMPTS) {
                if (root.postDelayed(() -> scanForInput(root, attempt + 1), INPUT_SCAN_DELAY_MS)) return;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Chat-input discovery failed", error);
        }
        synchronized (SCANNING_ROOTS) { SCANNING_ROOTS.remove(root); }
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
        InputState state = new InputState(input);
        STATES.add(new WeakReference<>(state));
        input.addTextChangedListener(state.watcher);
        input.addOnAttachStateChangeListener(state.attachListener);
        Log.i(TAG, "Wired standalone autocomplete to " + input.getClass().getName() +
                " id=" + input.getId());
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

    private static final class InputState {
        private final WeakReference<EditText> inputReference;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private PopupWindow popup;
        private int tokenStart;
        private int tokenEnd;
        private List<Emote> visibleSuggestions = Collections.emptyList();

        private final Runnable autoDismiss = this::hidePopup;

        private final TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) { refreshOnMain(); }
        };

        private final View.OnAttachStateChangeListener attachListener =
                new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) { postRefresh(); }
                    @Override public void onViewDetachedFromWindow(View view) {
                        handler.removeCallbacks(autoDismiss);
                        hidePopup();
                    }
                };

        InputState(EditText input) {
            inputReference = new WeakReference<>(input);
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
                Context context = input.getContext();
                EmoteSupport.ensureAutocompleteCatalog(context);
                List<Emote> matches = EmoteSupport.autocomplete(
                        EmoteSupport.getCurrentChannelId(), token, MAX_SUGGESTIONS);
                if (matches == null || matches.isEmpty()) {
                    visibleSuggestions = Collections.emptyList();
                    hidePopup();
                    return;
                }
                visibleSuggestions = new ArrayList<>(matches);
                showStrip(input, visibleSuggestions);

                handler.removeCallbacks(autoDismiss);
                handler.postDelayed(autoDismiss, STRIP_TIMEOUT_MS);
            } catch (Throwable error) {
                Log.w(TAG, "Standalone autocomplete update failed", error);
                hidePopup();
            }
        }

        private void showStrip(EditText input, List<Emote> suggestions) {
            Context context = input.getContext();
            int stripHeight = dp(context, 44f);
            int imageSize = dp(context, 26f);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 4f), dp(context, 3f), dp(context, 4f), dp(context, 3f));

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
                row.addView(chip);
            }

            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setFillViewport(false);
            scroll.setBackgroundColor(Color.parseColor("#FF1F1925"));
            scroll.addView(row);

            PopupWindow next = new PopupWindow(
                    scroll, ViewGroup.LayoutParams.MATCH_PARENT, stripHeight, false);
            next.setOutsideTouchable(false);
            next.setTouchable(true);
            next.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            next.setClippingEnabled(false);
            next.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

            View root = input.getRootView();
            try {
                int[] location = new int[2];
                input.getLocationInWindow(location);
                int rootHeight = root.getHeight() > 0
                        ? root.getHeight() : context.getResources().getDisplayMetrics().heightPixels;
                int y = Math.max(dp(context, 4f), location[1] - stripHeight);
                y = Math.min(y, Math.max(dp(context, 4f), rootHeight - stripHeight));

                if (popup != null) popup.dismiss();
                popup = next;
                popup.showAtLocation(root, Gravity.TOP | Gravity.START, 0, y);
            } catch (Throwable error) {
                popup = null;
                try { next.dismiss(); } catch (Throwable ignored) { }
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
                if (popup != null) {
                    popup.dismiss();
                    popup = null;
                }
            } catch (Throwable ignored) {
                popup = null;
            }
        }
    }
}
