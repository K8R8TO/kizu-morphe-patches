package io.github.bakwudo.uyu.extension.appearance;

import android.view.View;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private static final Map<View, ClipVisibilityGuard> GUARDS = new WeakHashMap<>();

    private PlayerOverlaySupport() {
    }

    public static void bindClip(View createClipView) {
        if (createClipView == null) return;
        try {
            ClipVisibilityGuard guard = GUARDS.get(createClipView);
            if (guard == null) {
                guard = new ClipVisibilityGuard(createClipView);
                GUARDS.put(createClipView, guard);
                createClipView.addOnAttachStateChangeListener(guard);
            }
            apply(createClipView);
            if (createClipView.isAttachedToWindow()) {
                guard.attach();
            } else {
                final ClipVisibilityGuard finalGuard = guard;
                createClipView.post(() -> {
                    apply(createClipView);
                    finalGuard.attach();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** Binds the player Share/Live Share control to the Kizu setting. */
    public static void bindLiveShareButton(View shareButton) {
        if (shareButton == null) return;
        try {
            HiddenView.attach(
                    shareButton,
                    view -> Settings.HIDE_PLAYER_LIVE_SHARE_BUTTON.get(),
                    false
            );
        } catch (Throwable ignored) {
        }
    }

    /** Binds the player Chromecast/Cast control to the Kizu setting. */
    public static void bindCastButton(View castButton) {
        if (castButton == null) return;
        try {
            HiddenView.attach(
                    castButton,
                    view -> Settings.HIDE_CAST_BUTTON.get(),
                    false
            );
        } catch (Throwable ignored) {
        }
    }

    private static void apply(View view) {
        try {
            if (Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get()) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class ClipVisibilityGuard
            implements ViewTreeObserver.OnPreDrawListener,
                       ViewTreeObserver.OnGlobalLayoutListener,
                       View.OnAttachStateChangeListener {
        private final View view;
        private ViewTreeObserver observer;

        private ClipVisibilityGuard(View view) {
            this.view = view;
        }

        void attach() {
            try {
                ViewTreeObserver next = view.getViewTreeObserver();
                if (!next.isAlive()) return;
                if (observer == next) {
                    apply(view);
                    return;
                }
                detachObserver();
                observer = next;
                observer.addOnPreDrawListener(this);
                observer.addOnGlobalLayoutListener(this);
                apply(view);
            } catch (Throwable ignored) {
            }
        }

        private void detachObserver() {
            if (observer == null || !observer.isAlive()) return;
            try { observer.removeOnPreDrawListener(this); } catch (Throwable ignored) {}
            try { observer.removeOnGlobalLayoutListener(this); } catch (Throwable ignored) {}
            observer = null;
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            attach();
            v.post(() -> apply(v));
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            detachObserver();
        }

        @Override
        public boolean onPreDraw() {
            apply(view);
            return true;
        }

        @Override
        public void onGlobalLayout() {
            apply(view);
        }
    }
}
