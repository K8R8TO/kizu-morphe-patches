package app.morphe.extension.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import app.morphe.extension.Utils;
import app.morphe.extension.settings.Settings;

/** PurpleTV-style landscape brightness/volume gestures on Twitch's existing player wrapper. */
public final class GesturePlayerWrapper extends RelativeLayout {
    private static final float GESTURE_SCALE = 0.7f;
    private static final int EDGE_IGNORE_DP = 30;
    private static final long HIDE_DELAY_MS = 500L;
    private static final long FADE_DURATION_MS = 500L;

    private final int touchSlop;
    private final GestureProgressView progressView;
    private ViewGroup playerOverlay;
    private ViewGroup debugPanel;
    private ViewGroup oneChatOverlay;
    private View chatWrapper;
    private View debugList;
    private boolean candidate;
    private boolean intercepted;
    private boolean brightnessSide;
    private boolean scrollMode;
    private float downY;
    private int startingVolume;
    private int lastVolumeStep;
    private int startingBrightness;
    private int maxVolume;
    private int edgeIgnorePx;
    private final int[] wrapperLocation = new int[2];
    private final int[] viewLocation = new int[2];

    public GesturePlayerWrapper(Context context) { this(context, null); }
    public GesturePlayerWrapper(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public GesturePlayerWrapper(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = Math.max(1, ViewConfiguration.get(context).getScaledTouchSlop());
        edgeIgnorePx = dp(EDGE_IGNORE_DP);
        progressView = new GestureProgressView(context);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setClickable(false);
        progressView.setFocusable(false);
        progressView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        playerOverlay = findGroup("player_overlay_container");
        debugPanel = findGroup("player_debug_stats_container");
        oneChatOverlay = findGroup("one_chat_overlay_container");
        chatWrapper = findNamedView("chat_wrapper");
        debugList = findNamedView("video_debug_list");
        if (playerOverlay != null && progressView.getParent() == null) {
            playerOverlay.addView(progressView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private View findNamedView(String name) {
        int id = getResources().getIdentifier(name, "id", getContext().getPackageName());
        return id == 0 ? null : findViewById(id);
    }

    private ViewGroup findGroup(String name) {
        View view = findNamedView(name);
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    /** Keep Twitch's nested player from disabling interception while collapse prevention is on. */
    @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (Settings.DISABLE_LANDSCAPE_SWIPE_TO_PORTRAIT.get()
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            super.requestDisallowInterceptTouchEvent(false);
            return;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginCandidate(event);
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                candidate = false;
                return intercepted;
            case MotionEvent.ACTION_MOVE:
                if (intercepted) return true;
                if (!candidate) return false;
                if (event.getPointerCount() > 1) { candidate = false; return false; }
                if (Math.abs(downY - event.getY()) > touchSlop
                        && (gestureEnabled() || Settings.DISABLE_LANDSCAPE_SWIPE_TO_PORTRAIT.get())) {
                    intercepted = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (intercepted) return true;
                resetTouchState();
                return false;
            default:
                return intercepted;
        }
    }

    private void beginCandidate(MotionEvent event) {
        resetTouchState();
        if (event.getPointerCount() > 1 || playerOverlay == null
                || getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) return;
        downY = event.getY();
        brightnessSide = isOnBrightnessSide(event.getX());
        boolean adjustGesture = gestureEnabled();
        boolean blockCollapse = Settings.DISABLE_LANDSCAPE_SWIPE_TO_PORTRAIT.get();
        // When collapse prevention is enabled, also own vertical swipes that begin at the
        // player's top/bottom edge. Twitch otherwise gets those edge gestures before Kizu.
        if ((!adjustGesture && !blockCollapse)
                || !isTouchAreaAllowed(event.getX(), event.getY(), blockCollapse)) return;
        try {
            if (adjustGesture && brightnessSide) {
                Activity activity = currentActivity();
                if (activity == null) {
                    if (!blockCollapse) return;
                } else {
                    startingBrightness = brightnessPercent(activity);
                }
            } else if (adjustGesture) {
                AudioManager audio = audioManager();
                if (audio == null) {
                    if (!blockCollapse) return;
                } else {
                    startingVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    lastVolumeStep = startingVolume;
                    maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (maxVolume <= 0 && !blockCollapse) return;
                }
            }
            candidate = true;
        } catch (Throwable ignored) {
            candidate = blockCollapse;
        }
    }

    /** Split gestures across the actual video pane, not the entire theatre window. */
    private boolean isOnBrightnessSide(float x) {
        if (playerOverlay == null || playerOverlay.getWidth() <= 0) {
            return x < getWidth() / 2.0f;
        }
        playerOverlay.getLocationOnScreen(viewLocation);
        getLocationOnScreen(wrapperLocation);
        float playerLeft = viewLocation[0] - wrapperLocation[0];
        return x < playerLeft + (playerOverlay.getWidth() / 2.0f);
    }

    private boolean gestureEnabled() {
        return brightnessSide ? Settings.BRIGHTNESS_GESTURE.get() : Settings.VOLUME_GESTURE.get();
    }

    private boolean isTouchAreaAllowed(float x, float y, boolean includeEdges) {
        if (!isPointInside(playerOverlay, x, y)) return false;
        if (!includeEdges && (y <= edgeIgnorePx || y >= localBottom(playerOverlay) - edgeIgnorePx)) return false;
        if (oneChatOverlay != null && oneChatOverlay.isShown() && oneChatOverlay.getChildCount() > 0) return false;
        if (chatWrapper != null && chatWrapper.isShown() && isPointInside(chatWrapper, x, y)) return false;
        if (debugPanel != null && debugPanel.getChildCount() > 0 && debugList != null
                && debugList.isShown() && isPointInside(debugList, x, y)) return false;
        // Never steal vertical swipes that start on Twitch's own settings/quality menus,
        // scroll containers, sliders, or compact player controls.
        if (isInteractiveControlAt(this, x, y, true)) return false;
        return true;
    }

    private boolean isInteractiveControlAt(View view, float x, float y, boolean root) {
        if (view == null || !view.isShown() || !isPointInside(view, x, y)) return false;
        if (!root && view != progressView && view != playerOverlay) {
            String idName = "";
            int id = view.getId();
            if (id != View.NO_ID) {
                try { idName = getResources().getResourceEntryName(id).toLowerCase(java.util.Locale.ROOT); }
                catch (Throwable ignored) { }
            }
            if (idName.contains("quality") || idName.contains("setting")
                    || idName.contains("menu") || idName.contains("sheet")
                    || idName.contains("dialog") || idName.contains("seek")
                    || idName.contains("slider") || idName.contains("list")) return true;

            String className = view.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (className.contains("scrollview") || className.contains("recyclerview")
                    || className.contains("abslistview") || className.contains("seekbar")
                    || className.contains("slider")) return true;
            try {
                if (view.canScrollVertically(-1) || view.canScrollVertically(1)) return true;
            } catch (Throwable ignored) { }

            long viewArea = (long) view.getWidth() * (long) view.getHeight();
            long paneArea = playerOverlay == null ? 0L
                    : (long) playerOverlay.getWidth() * (long) playerOverlay.getHeight();
            if ((view.isClickable() || view.isLongClickable())
                    && paneArea > 0L && viewArea > 0L && viewArea * 4L < paneArea) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (isInteractiveControlAt(child, x, y, false)) return true;
            }
        }
        return false;
    }

    private float localBottom(View view) {
        view.getLocationOnScreen(viewLocation);
        getLocationOnScreen(wrapperLocation);
        return viewLocation[1] + view.getHeight() - wrapperLocation[1];
    }

    private boolean isPointInside(View view, float x, float y) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        view.getLocationOnScreen(viewLocation);
        getLocationOnScreen(wrapperLocation);
        float sx = wrapperLocation[0] + x;
        float sy = wrapperLocation[1] + y;
        return sx >= viewLocation[0] && sx < viewLocation[0] + view.getWidth()
                && sy >= viewLocation[1] && sy < viewLocation[1] + view.getHeight();
    }

    @Override @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginCandidate(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!intercepted) return false;
                if (!candidate || event.getPointerCount() > 1) return true;
                scrollMode = true;
                updateGesture(downY - event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (scrollMode) scheduleProgressHide();
                resetTouchState();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                candidate = false;
                return intercepted;
            default:
                return intercepted;
        }
    }

    private void updateGesture(float delta) {
        // The swipe can still be consumed to block Twitch's collapse gesture when level control
        // is disabled; in that case it must not alter device settings or show a fake progress bar.
        if (!gestureEnabled()) {
            hideProgressImmediately();
            return;
        }
        if (brightnessSide) {
            Activity activity = currentActivity();
            if (activity == null) return;
            int value = calculate(delta, startingBrightness, 100, playerOverlay.getHeight());
            try {
                android.view.Window window = activity.getWindow();
                android.view.WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.screenBrightness = Math.max(0.01f, Math.min(1.0f, value / 100.0f));
                window.setAttributes(attributes);
            } catch (Throwable ignored) { return; }
            showProgress(value, 100, true);
        } else {
            AudioManager audio = audioManager();
            if (audio == null || maxVolume <= 0) return;
            int value = calculate(delta, startingVolume, maxVolume, playerOverlay.getHeight());
            try {
                // This is an absolute target based on the level captured at touch-down, not a
                // relative ADJUST_LOWER operation. It must be allowed to cross the touch-down
                // volume and reach zero. Re-apply the target while Kizu owns the swipe so Twitch
                // cannot leave its own gesture's stale volume level in effect.
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                lastVolumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                value = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            } catch (Throwable ignored) { return; }
            showProgress(value, maxVolume, false);
        }
    }

    private int calculate(float delta, int oldStep, int max, int height) {
        if (max <= 0 || height <= 0) return oldStep;
        float step = (height * GESTURE_SCALE) / max;
        if (step <= 0f) return oldStep;
        // Round instead of truncating toward zero so downward swipes can reduce volume
        // smoothly even when the starting media level is low.
        return Math.max(0, Math.min(max, oldStep + Math.round(delta / step)));
    }

    private void showProgress(int value, int max, boolean brightness) {
        if (!Settings.GESTURE_OSD.get()) { hideProgressImmediately(); return; }
        scrollMode = true;
        progressView.setProgress(value, max, brightness);
        progressView.animate().cancel();
        progressView.setAlpha(1f);
        progressView.setVisibility(View.VISIBLE);
        progressView.invalidate();
        progressView.removeCallbacks(hideProgressRunnable);
        progressView.postDelayed(hideProgressRunnable, HIDE_DELAY_MS);
    }

    private final Runnable hideProgressRunnable = new Runnable() {
        @Override public void run() {
            progressView.animate().cancel();
            progressView.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction(new Runnable() {
                @Override public void run() {
                    progressView.setVisibility(View.INVISIBLE);
                    progressView.setAlpha(1f);
                }
            }).start();
        }
    };

    private void scheduleProgressHide() {
        progressView.removeCallbacks(hideProgressRunnable);
        progressView.postDelayed(hideProgressRunnable, HIDE_DELAY_MS);
    }

    private void hideProgressImmediately() {
        progressView.removeCallbacks(hideProgressRunnable);
        progressView.animate().cancel();
        progressView.setAlpha(1f);
        progressView.setVisibility(View.INVISIBLE);
    }

    private void resetTouchState() {
        candidate = false;
        intercepted = false;
        brightnessSide = false;
        scrollMode = false;
        downY = -1f;
        lastVolumeStep = 0;
    }

    private AudioManager audioManager() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private Activity currentActivity() { return Utils.getCurrentActivity(); }

    private int brightnessPercent(Activity activity) {
        float brightness = Math.max(0f, activity.getWindow().getAttributes().screenBrightness);
        return Math.max(0, Math.min(100, (int) (brightness * 100f)));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class GestureProgressView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int value;
        private int max = 1;
        private boolean brightness;

        GestureProgressView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            backgroundPaint.setColor(Color.WHITE);
            fillPaint.setColor(Color.parseColor("#6441A5"));
            borderPaint.setColor(Color.BLACK);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(45f * getResources().getDisplayMetrics().scaledDensity);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setShadowLayer(dp(2), 0f, 0f, Color.BLACK);
        }

        void setProgress(int value, int max, boolean brightness) {
            this.value = value;
            this.max = Math.max(1, max);
            this.brightness = brightness;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = dp(14);
            float height = dp(110);
            float top = (getHeight() - height) / 2f;
            float left = brightness ? getWidth() - dp(10) - width : dp(12);
            float right = left + width;
            float bottom = top + height;
            canvas.drawRect(left, top, right, bottom, backgroundPaint);
            float fillHeight = height * Math.max(0, Math.min(max, value)) / (float) max;
            if (fillHeight > 0f) canvas.drawRect(left, bottom - fillHeight, right, bottom, fillPaint);
            canvas.drawRect(left, top, right, bottom, borderPaint);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float textY = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(Integer.toString(value), getWidth() / 2f, textY, textPaint);
        }
    }
}
