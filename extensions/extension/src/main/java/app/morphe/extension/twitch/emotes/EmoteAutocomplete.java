package app.morphe.extension.twitch.emotes;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import io.github.bakwudo.uyu.extension.settings.Settings;

/** Adds a small, non-focusable suggestion list to Twitch's native emote-input view. */
public final class EmoteAutocomplete {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<EditText, WeakReference<Controller>> CONTROLLERS = new WeakHashMap<>();
    private static final int MAX_SUGGESTIONS = 6;

    private EmoteAutocomplete() { }

    public static void install(Object target) {
        if (!(target instanceof EditText) || Looper.myLooper() != Looper.getMainLooper()) return;
        EditText input = (EditText) target;
        synchronized (CONTROLLERS) {
            WeakReference<Controller> existing = CONTROLLERS.get(input);
            if (existing != null && existing.get() != null) return;
            Controller controller = new Controller(input);
            CONTROLLERS.put(input, new WeakReference<>(controller));
            input.addTextChangedListener(controller);
            input.addOnAttachStateChangeListener(controller);
            View.OnFocusChangeListener previous = input.getOnFocusChangeListener();
            input.setOnFocusChangeListener((view, focused) -> {
                if (previous != null) previous.onFocusChange(view, focused);
                if (focused) controller.scheduleUpdate(); else controller.hide();
            });
        }
        EmoteSupport.ensureAutocompleteCatalog(input.getContext());
    }

    static void refreshAll() {
        MAIN.post(() -> {
            List<Controller> snapshot;
            synchronized (CONTROLLERS) {
                snapshot = new ArrayList<>();
                for (WeakReference<Controller> reference : CONTROLLERS.values()) {
                    Controller controller = reference.get();
                    if (controller != null) snapshot.add(controller);
                }
            }
            for (Controller controller : snapshot) controller.scheduleUpdate();
        });
    }

    public static void selectionChanged(Object target) {
        if (!(target instanceof EditText)) return;
        EditText input = (EditText) target;
        synchronized (CONTROLLERS) {
            WeakReference<Controller> reference = CONTROLLERS.get(input);
            Controller controller = reference == null ? null : reference.get();
            if (controller != null) controller.scheduleUpdate();
        }
    }

    private static final class Controller implements TextWatcher, View.OnAttachStateChangeListener {
        private final EditText input;
        private PopupWindow popup;
        private int tokenStart;
        private int tokenEnd;
        private final Runnable updateTask = this::update;

        Controller(EditText input) { this.input = input; }

        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable text) { scheduleUpdate(); }

        void scheduleUpdate() {
            MAIN.removeCallbacks(updateTask);
            MAIN.post(updateTask);
        }

        void update() {
            if (!Settings.EMOTES_THIRD_PARTY.get() || !Settings.EMOTES_AUTOCOMPLETE.get() ||
                    !input.hasFocus() || !input.isShown() || !input.isAttachedToWindow()) {
                hide();
                return;
            }
            Editable text = input.getText();
            int cursor = input.getSelectionStart();
            if (text == null || cursor < 0 || cursor > text.length()) { hide(); return; }
            int start = cursor;
            int end = cursor;
            while (start > 0 && isTokenCharacter(text.charAt(start - 1))) start--;
            while (end < text.length() && isTokenCharacter(text.charAt(end))) end++;
            boolean leadingColon = start > 0 && text.charAt(start - 1) == ':';
            tokenStart = leadingColon ? start - 1 : start;
            tokenEnd = end;
            String prefix = text.subSequence(start, cursor).toString();
            if (prefix.length() < 2) { hide(); return; }

            EmoteSupport.ensureAutocompleteCatalog(input.getContext());
            List<Emote> matches = EmoteSupport.autocomplete(
                    EmoteSupport.getCurrentChannelId(), prefix, MAX_SUGGESTIONS);
            if (matches.isEmpty()) { hide(); return; }
            show(matches);
        }

        private void show(List<Emote> matches) {
            hide();
            float density = input.getResources().getDisplayMetrics().density;
            LinearLayout rows = new LinearLayout(input.getContext());
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setPadding(dp(4, density), dp(4, density), dp(4, density), dp(4, density));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xF0222228);
            background.setCornerRadius(dp(12, density));
            rows.setBackground(background);
            for (Emote emote : matches) {
                TextView row = new TextView(input.getContext());
                row.setText(emote.name + "   ·   " + emote.providerLabel());
                row.setTextColor(Color.WHITE);
                row.setTextSize(14);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(44, density));
                row.setPadding(dp(12, density), 0, dp(12, density), 0);
                row.setMaxLines(1);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setOnClickListener(view -> select(emote));
                rows.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            int screenWidth = input.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screenWidth - dp(24, density), Math.max(input.getWidth(), dp(240, density)));
            popup = new PopupWindow(rows, width, ViewGroup.LayoutParams.WRAP_CONTENT, false);
            popup.setBackgroundDrawable(background);
            popup.setOutsideTouchable(false);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setElevation(dp(8, density));
            if (input.getWindowToken() != null) {
                rows.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                popup.showAsDropDown(input, 0, -input.getHeight() - rows.getMeasuredHeight(), Gravity.START);
            }
        }

        private void select(Emote emote) {
            hide();
            Editable text = input.getText();
            if (text == null || tokenStart < 0 || tokenEnd > text.length() || tokenStart > tokenEnd) return;
            String replacement = emote.name;
            if (tokenEnd == text.length()) replacement += " ";
            else if (!Character.isWhitespace(text.charAt(tokenEnd))) replacement += " ";
            text.replace(tokenStart, tokenEnd, replacement);
            int selection = Math.min(text.length(), tokenStart + replacement.length());
            input.setSelection(selection);
        }

        void hide() {
            PopupWindow current = popup;
            popup = null;
            if (current != null && current.isShowing()) current.dismiss();
        }

        @Override public void onViewAttachedToWindow(View view) { scheduleUpdate(); }
        @Override public void onViewDetachedFromWindow(View view) { hide(); }

        private static boolean isTokenCharacter(char value) {
            return Character.isLetterOrDigit(value) || value == '_' || value == '-';
        }

        private static int dp(int value, float density) { return Math.round(value * density); }
    }
}
