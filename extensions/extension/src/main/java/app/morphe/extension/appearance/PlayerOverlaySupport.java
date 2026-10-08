package app.morphe.extension.appearance;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.settings.Settings;

/**
 * Hides both Create Clip controls present in Twitch 31.3.1's player overlay.
 *
 * Twitch has a legacy ImageView (create_clip_button) and a newer ComposeView
 * (create_clip_button_compose_view). We identify them by resource entry name so
 * obfuscated field names are not required.
 */
@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private static final Map<View, ClipGuard> GUARDS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, RootGuard> ROOT_GUARDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PlayerOverlaySupport() {
    }

    public static void bindPlayerOverlay(View root) {
        if (root == null) return;
        try {
            scanAndBind(root);
            if (!ROOT_GUARDS.containsKey(root)) {
                RootGuard guard = new RootGuard(root);
                ROOT_GUARDS.put(root, guard);
                root.addOnAttachStateChangeListener(guard);
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.addOnGlobalLayoutListener(guard);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scanAndBind(View view) {
        if (view == null) return;
        if (isClipView(view)) {
            bindClip(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanAndBind(group.getChildAt(i));
            }
        }
    }

    private static boolean isClipView(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return false;
        try {
            String name = view.getResources().getResourceEntryName(id);
            return "create_clip_button".equals(name)
                    || "create_clip_button_compose_view".equals(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void bindClip(View view) {
        try {
            ClipGuard guard = GUARDS.get(view);
            if (guard == null) {
                guard = new ClipGuard(view);
                GUARDS.put(view, guard);
                view.addOnAttachStateChangeListener(guard);
            }
            apply(view);
            if (view.isAttachedToWindow()) {
                guard.attach();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void apply(View view) {
        try {
            if (Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get()) {
                view.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class ClipGuard implements ViewTreeObserver.OnPreDrawListener,
            ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        private final View view;
        private ViewTreeObserver observer;

        private ClipGuard(View view) {
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

    private static final class RootGuard implements ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
        private final View root;

        private RootGuard(View root) {
            this.root = root;
        }

        @Override
        public void onGlobalLayout() {
            scanAndBind(root);
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            try {
                ViewTreeObserver observer = v.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(this);
            } catch (Throwable ignored) {
            }
            scanAndBind(v);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            try {
                ViewTreeObserver observer = v.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            } catch (Throwable ignored) {
            }
        }
    }
}
