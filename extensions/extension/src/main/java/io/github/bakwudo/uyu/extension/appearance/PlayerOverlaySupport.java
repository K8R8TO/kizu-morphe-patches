package io.github.bakwudo.uyu.extension.appearance;

import android.view.View;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private static final Map<View, ClipVisibilityListener> LISTENERS = new WeakHashMap<>();

    private PlayerOverlaySupport() {
    }

    public static void bindClip(View createClipButton) {
        try {
            if (createClipButton == null) return;

            apply(createClipButton);
            createClipButton.post(() -> apply(createClipButton));

            ClipVisibilityListener listener = new ClipVisibilityListener(createClipButton);
            LISTENERS.put(createClipButton, listener);
            createClipButton.addOnAttachStateChangeListener(listener);

            if (createClipButton.isAttachedToWindow()) {
                listener.attachObserver();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void apply(View view) {
        try {
            boolean hide = Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get();
            if (hide) {
                if (view.getVisibility() != View.GONE) {
                    view.setVisibility(View.GONE);
                }
            } else if (view.getVisibility() == View.GONE) {
                view.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class ClipVisibilityListener
            implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final View view;
        private ViewTreeObserver observer;

        private ClipVisibilityListener(View view) {
            this.view = view;
        }

        private void attachObserver() {
            observer = view.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnPreDrawListener(this);
            }
            apply(view);
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            attachObserver();
            v.post(() -> apply(v));
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            if (observer != null && observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
            observer = null;
        }

        @Override
        public boolean onPreDraw() {
            apply(view);
            return true;
        }
    }
}
