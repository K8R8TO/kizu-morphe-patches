package app.morphe.extension.twitch.emotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Selection;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import app.morphe.extension.Utils;
import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * Standalone third-party emote picker. Twitch's native emote renderer is deliberately not used
 * for external assets; this UI owns its own image loading and insertion path.
 */
public final class EmotePickerBridge {
    private static final String TAG = "KizuPicker";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final EmoteImageLoader IMAGES =
            new EmoteImageLoader(EmotePickerBridge::imageUpdated);
    private static volatile PickerState CURRENT;

    private EmotePickerBridge() {}

    /**
     * Called from Twitch's native picker-open method. When disabled, this is a no-op and Twitch's
     * normal picker remains unchanged.
     */
    public static void onPickerOpened(Object ignored) {
        try {
            if (!Settings.EMOTES_PICKER.get()) return;
            final Activity activity = Utils.getCurrentActivity();
            if (activity == null || activity.isFinishing()) return;
            MAIN.postDelayed(() -> {
                try {
                    if (Settings.EMOTES_PICKER.get() && !activity.isFinishing()) {
                        applyWinkIcon(activity);
                        showPicker(activity);
                    }
                } catch (Throwable t) {
                    log("picker open failed", t);
                }
            }, 120L);
        } catch (Throwable t) {
            log("picker hook failed", t);
        }
    }

    private static void showPicker(Activity activity) {
        PickerState previous = CURRENT;
        if (previous != null && previous.dialog != null && previous.dialog.isShowing()) {
            previous.dialog.dismiss();
        }

        View input = activity.getCurrentFocus();
        if (!(input instanceof EditText)) {
            input = findEditText(activity.getWindow().getDecorView());
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 8), dp(activity, 2), dp(activity, 8), dp(activity, 8));

        LinearLayout filters = new LinearLayout(activity);
        filters.setGravity(Gravity.CENTER_VERTICAL);

        TextView all = filter(activity, "ALL");
        TextView seven = filter(activity, "7TV");
        TextView bttv = filter(activity, "BTTV");
        filters.addView(all);
        filters.addView(seven);
        filters.addView(bttv);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search emotes");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        searchParams.setMargins(dp(activity, 6), 0, 0, dp(activity, 4));
        filters.addView(search, searchParams);
        root.addView(filters);

        ScrollView scroll = new ScrollView(activity);
        GridLayout grid = new GridLayout(activity);
        int columns = Math.max(4, Math.min(7,
                activity.getResources().getDisplayMetrics().widthPixels / dp(activity, 58)));
        grid.setColumnCount(columns);
        scroll.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView status = new TextView(activity);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setText("Loading third-party emotes…");
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 30)));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Kizu Emotes")
                .setView(root)
                .create();

        PickerState state = new PickerState(activity, dialog, grid, search, status, input);
        CURRENT = state;

        all.setOnClickListener(v -> { state.provider = 0; rebuild(state); });
        seven.setOnClickListener(v -> { state.provider = 1; rebuild(state); });
        bttv.setOnClickListener(v -> { state.provider = 2; rebuild(state); });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                rebuild(state);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.setOnDismissListener(d -> {
            if (CURRENT == state) CURRENT = null;
            stopAnimations(grid);
        });
        dialog.setOnShowListener(d -> sizeDialog(dialog, activity));

        dialog.show();
        sizeDialog(dialog, activity);

        final String channel = EmoteSupport.getCurrentChannelId();
        new Thread(() -> {
            List<Emote> entries;
            try {
                entries = EmoteSupport.getAllForChannelForPicker(channel);
            } catch (Throwable t) {
                entries = java.util.Collections.emptyList();
            }
            state.entries = entries == null ? java.util.Collections.emptyList() : entries;
            MAIN.post(() -> {
                if (CURRENT != state || !dialog.isShowing()) return;
                status.setText(state.entries.isEmpty()
                        ? "No third-party emotes loaded"
                        : state.entries.size() + " emotes");
                rebuild(state);
            });
        }, "kizu-picker-catalog").start();
    }

    private static void sizeDialog(AlertDialog dialog, Context context) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 520));
        window.setDimAmount(0.65f);
    }

    private static TextView filter(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setMinWidth(dp(context, 52));
        view.setMinHeight(dp(context, 40));
        view.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        return view;
    }

    private static void rebuild(PickerState state) {
        if (state == null || state.grid == null) return;
        state.grid.removeAllViews();

        String query = state.search.getText() == null
                ? ""
                : state.search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;

        for (Emote emote : state.entries) {
            if (emote == null || emote.name == null || emote.url == null) continue;
            if (!matchesProvider(emote, state.provider)) continue;
            if (!query.isEmpty() &&
                    !emote.name.toLowerCase(Locale.ROOT).contains(query)) continue;

            Drawable drawable = IMAGES.createDrawable(state.activity.getResources(), emote);
            if (drawable != null) {
                ImageButton button = new ImageButton(state.activity);
                button.setBackgroundColor(Color.TRANSPARENT);
                button.setPadding(dp(state.activity, 5), dp(state.activity, 5),
                        dp(state.activity, 5), dp(state.activity, 5));
                button.setContentDescription(emote.name);
                button.setImageDrawable(drawable);
                button.setOnClickListener(v -> insertEmote(state, emote.name));
                state.grid.addView(button, cellParams(state.activity));
            } else {
                TextView placeholder = new TextView(state.activity);
                placeholder.setText(emote.name);
                placeholder.setGravity(Gravity.CENTER);
                placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                placeholder.setMaxLines(2);
                placeholder.setEllipsize(android.text.TextUtils.TruncateAt.END);
                placeholder.setOnClickListener(v -> insertEmote(state, emote.name));
                state.grid.addView(placeholder, cellParams(state.activity));
                IMAGES.request(state.activity, emote, dp(state.activity, 42));
            }
            count++;
        }

        if (count == 0 && !state.entries.isEmpty()) {
            TextView empty = new TextView(state.activity);
            empty.setText("No matching emotes");
            empty.setGravity(Gravity.CENTER);
            state.grid.addView(empty, new GridLayout.LayoutParams());
        }
    }

    private static GridLayout.LayoutParams cellParams(Context context) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        int size = dp(context, 58);
        params.width = size;
        params.height = size;
        params.setMargins(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1));
        return params;
    }

    private static boolean matchesProvider(Emote emote, int provider) {
        if (provider == 0) return true;
        String url = emote.url.toLowerCase(Locale.ROOT);
        if (provider == 1) return url.contains("7tv") || url.contains("7tv.app");
        return url.contains("betterttv") || url.contains("bttv");
    }

    private static void insertEmote(PickerState state, String code) {
        try {
            View view = state.input instanceof EditText
                    ? state.input
                    : findEditText(state.activity.getWindow().getDecorView());
            if (!(view instanceof EditText)) {
                state.dialog.dismiss();
                return;
            }

            EditText input = (EditText) view;
            Editable editable = input.getText();
            if (editable == null) {
                state.dialog.dismiss();
                return;
            }

            int start = Math.max(0, input.getSelectionStart());
            int end = Math.max(0, input.getSelectionEnd());
            start = Math.min(start, editable.length());
            end = Math.min(end, editable.length());
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }

            String prefix = start > 0 && !Character.isWhitespace(editable.charAt(start - 1))
                    ? " " : "";
            String suffix = end < editable.length() &&
                    !Character.isWhitespace(editable.charAt(end)) ? " " : "";
            String value = prefix + code + suffix;
            editable.replace(start, end, value);

            int cursor = start + value.length() - suffix.length();
            Selection.setSelection(editable,
                    Math.max(0, Math.min(cursor, editable.length())));
            input.requestFocus();
            state.dialog.dismiss();
        } catch (Throwable t) {
            log("insert failed", t);
            state.dialog.dismiss();
        }
    }

    private static EditText findEditText(View root) {
        if (root instanceof EditText && root.getVisibility() == View.VISIBLE && root.isShown()) {
            return (EditText) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = findEditText(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void imageUpdated(String url) {
        PickerState state = CURRENT;
        if (state == null) return;
        MAIN.post(() -> {
            if (CURRENT == state && state.dialog.isShowing()) rebuild(state);
        });
    }

    private static void stopAnimations(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) {
                Drawable drawable = ((ImageView) child).getDrawable();
                if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    ((android.graphics.drawable.AnimatedImageDrawable) drawable).stop();
                }
            }
        }
    }

    private static void applyWinkIcon(Activity activity) {
        try {
            markWinkIcons(activity.getWindow().getDecorView());
        } catch (Throwable ignored) {
        }
    }

    private static void markWinkIcons(View view) {
        if (view instanceof ImageButton || view instanceof ImageView) {
            CharSequence description = view.getContentDescription();
            String text = description == null
                    ? "" : description.toString().toLowerCase(Locale.ROOT);
            if (text.contains("emote") || text.contains("emoji") || text.contains("smiley")) {
                ((ImageView) view).setImageDrawable(new WinkDrawable());
                return;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) markWinkIcons(group.getChildAt(i));
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    private static void log(String message, Throwable t) {
        try {
            if (t == null) android.util.Log.d(TAG, message);
            else android.util.Log.e(TAG, message, t);
        } catch (Throwable ignored) {}
    }

    private static final class PickerState {
        final Activity activity;
        final AlertDialog dialog;
        final GridLayout grid;
        final EditText search;
        final TextView status;
        final View input;
        volatile List<Emote> entries = java.util.Collections.emptyList();
        volatile int provider;

        PickerState(Activity activity, AlertDialog dialog, GridLayout grid,
                    EditText search, TextView status, View input) {
            this.activity = activity;
            this.dialog = dialog;
            this.grid = grid;
            this.search = search;
            this.status = status;
            this.input = input;
        }
    }

    private static final class WinkDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mouth = new Path();

        @Override
        public void draw(Canvas canvas) {
            RectF b = new RectF(getBounds());
            float cx = b.centerX();
            float cy = b.centerY();
            float r = Math.min(b.width(), b.height()) * 0.34f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1f, r * 0.12f));
            paint.setColor(Color.BLACK);

            canvas.drawCircle(cx + r * 0.38f, cy - r * 0.18f, r * 0.09f, paint);
            canvas.drawLine(cx - r * 0.48f, cy - r * 0.18f,
                    cx - r * 0.28f, cy - r * 0.18f, paint);

            mouth.reset();
            mouth.moveTo(cx - r * 0.34f, cy + r * 0.22f);
            mouth.quadTo(cx, cy + r * 0.48f, cx + r * 0.34f, cy + r * 0.22f);
            canvas.drawPath(mouth, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
