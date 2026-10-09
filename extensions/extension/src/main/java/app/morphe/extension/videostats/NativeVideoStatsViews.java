package app.morphe.extension.videostats;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds the optional Video Stats button and a lightweight, live-updating player stats panel. */
public final class NativeVideoStatsViews {
    private static final Map<View, Binding> BINDINGS = new WeakHashMap<>();

    private NativeVideoStatsViews() {}

    public static synchronized void install(View root, View button, View volume) {
        if (root == null || button == null || volume == null) return;
        Binding binding = BINDINGS.get(button);
        if (binding == null) {
            binding = new Binding(root, button, volume);
            BINDINGS.put(button, binding);
            button.setOnClickListener(binding);
            button.addOnAttachStateChangeListener(binding);
            if (button.isAttachedToWindow()) binding.onViewAttachedToWindow(button);
        } else {
            binding.refresh();
        }
    }

    public static synchronized void refreshPreferences() {
        for (Binding binding : new ArrayList<>(BINDINGS.values())) {
            if (binding != null) binding.refresh();
        }
    }

    private static int dp(View view, float amount) {
        return Math.round(amount * view.getResources().getDisplayMetrics().density);
    }

    private static final class Binding implements View.OnClickListener,
            View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<View> root;
        private final WeakReference<View> button;
        private final WeakReference<View> volume;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private PopupWindow popup;
        private TextView statsText;
        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (popup == null || !popup.isShowing()) return;
                View target = displayView();
                VideoStatsRuntime.refreshFromPlayer(target);
                renderPanel();
                handler.postDelayed(this, 1200L);
            }
        };

        Binding(View root, View button, View volume) {
            this.root = new WeakReference<>(root);
            this.button = new WeakReference<>(button);
            this.volume = new WeakReference<>(volume);
        }

        synchronized void refresh() {
            View target = button.get();
            View original = volume.get();
            if (target == null || original == null) return;
            boolean show = VideoStatsRuntime.enabled()
                    && original.getVisibility() == View.VISIBLE;
            int next = show ? View.VISIBLE : View.GONE;
            if (target.getVisibility() != next) target.setVisibility(next);
            target.setEnabled(show && original.isEnabled());
            if (!show) dismissPanel();
        }

        @Override public void onClick(View view) {
            if (!view.isShown() || !view.isEnabled()) return;
            showPanel();
        }

        private View displayView() {
            View base = root.get();
            if (base == null) return null;
            View tree = base.getRootView();
            int id = base.getResources().getIdentifier(
                    "playback_view_container", "id", base.getContext().getPackageName());
            View viewport = id == 0 ? null : tree.findViewById(id);
            return viewport != null ? viewport : tree;
        }

        private void showPanel() {
            View anchor = button.get();
            View base = root.get();
            if (anchor == null || base == null) return;
            dismissPanel();
            VideoStatsRuntime.refreshFromPlayer(displayView());

            LinearLayout panel = new LinearLayout(anchor.getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(anchor, 14);
            panel.setPadding(pad, pad, pad, pad);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xF0161616);
            background.setCornerRadius(dp(anchor, 12));
            background.setStroke(dp(anchor, 1), 0xFF505050);
            panel.setBackground(background);

            TextView title = new TextView(anchor.getContext());
            title.setText("VIDEO STATS");
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            panel.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            statsText = new TextView(anchor.getContext());
            statsText.setTextColor(0xFFE7E7E7);
            statsText.setTextSize(12);
            statsText.setTypeface(Typeface.MONOSPACE);
            statsText.setLineSpacing(dp(anchor, 2), 1.0f);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dp(anchor, 8);
            panel.addView(statsText, textParams);

            TextView closeHint = new TextView(anchor.getContext());
            closeHint.setText("Tap outside to close");
            closeHint.setTextColor(0xFF9E9E9E);
            closeHint.setTextSize(10);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = dp(anchor, 8);
            panel.addView(closeHint, hintParams);

            popup = new PopupWindow(panel, dp(anchor, 284),
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setTouchable(true);
            popup.setElevation(dp(anchor, 8));
            popup.setOnDismissListener(() -> {
                handler.removeCallbacks(poll);
                popup = null;
                statsText = null;
            });
            renderPanel();

            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            DisplayMetrics metrics = anchor.getResources().getDisplayMetrics();
            int width = dp(anchor, 284);
            int margin = dp(anchor, 8);
            int x = Math.max(margin, Math.min(
                    location[0] + anchor.getWidth() - width,
                    metrics.widthPixels - width - margin));
            int y = location[1] + anchor.getHeight() + dp(anchor, 6);
            popup.showAtLocation(base, Gravity.TOP | Gravity.START, x, y);
            handler.postDelayed(poll, 1200L);
        }

        private void renderPanel() {
            TextView text = statsText;
            if (text != null) text.setText(VideoStatsRuntime.snapshot());
        }

        void refreshPanel() {
            View anchor = button.get();
            if (anchor == null) return;
            anchor.post(() -> {
                if (popup == null || !popup.isShowing()) return;
                VideoStatsRuntime.refreshFromPlayer(displayView());
                renderPanel();
            });
        }

        private void dismissPanel() {
            handler.removeCallbacks(poll);
            PopupWindow current = popup;
            popup = null;
            statsText = null;
            if (current != null && current.isShowing()) current.dismiss();
        }

        @Override public void onGlobalLayout() { refresh(); }

        @Override public void onViewAttachedToWindow(View view) {
            View current = root.get();
            if (current != null) {
                ViewTreeObserver observer = current.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(this);
            }
            refresh();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            View current = root.get();
            if (current != null) {
                ViewTreeObserver observer = current.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            }
            dismissPanel();
        }
    }
}
