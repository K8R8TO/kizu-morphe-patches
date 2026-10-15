package app.morphe.extension.twitch.emotes;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Displays third-party emotes in Twitch's own emote-card bottom-sheet layout. */
final class NativeEmotePreview {
    private Dialog active;

    // These methods are replaced by the Twitch 31.3.1 bytecode patch.
    private static Dialog createDialog(Context context) { return null; }
    private static int[] resourceIds() { return new int[0]; }

    void open(TextView source, Emote emote, Drawable drawable) {
        Activity activity = activity(source.getContext());
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || drawable == null) return;
        close();
        int[] ids = resourceIds();
        if (ids.length != 6) return;

        Dialog dialog = createDialog(activity);
        if (dialog == null) return;
        View root = LayoutInflater.from(dialog.getContext()).inflate(ids[0], null, false);
        ViewGroup content = root.findViewById(ids[1]);
        View originalImage = content.findViewById(ids[2]);
        TextView name = content.findViewById(ids[3]);
        TextView provider = content.findViewById(ids[4]);
        if (content == null || originalImage == null || name == null || provider == null) {
            return;
        }

        ViewGroup sheet = ancestor(root, FrameLayout.class);
        if (sheet == null) return;
        retainPreviewPath(sheet, content, originalImage, name, provider);
        retainPreviewPath(content, content, originalImage, name, provider);
        originalImage.setVisibility(View.VISIBLE);
        name.setVisibility(View.VISIBLE);
        provider.setVisibility(View.VISIBLE);

        ImageView image = new ImageView(dialog.getContext());
        image.setId(originalImage.getId());
        image.setLayoutParams(originalImage.getLayoutParams());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageDrawable(drawable);
        image.setContentDescription(emote.name);
        int imageIndex = content.indexOfChild(originalImage);
        content.removeView(originalImage);
        content.addView(image, imageIndex);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(),
                source.getResources().getDimensionPixelSize(ids[5]));
        name.setText(emote.name);
        provider.setText(emote.providerLabel() + " emote");
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            if (drawable instanceof Animatable) ((Animatable) drawable).stop();
            image.setImageDrawable(null);
            if (active == dialog) active = null;
        });
        active = dialog;
        dialog.show();
        if (drawable instanceof Animatable) ((Animatable) drawable).start();
    }

    void close() {
        Dialog dialog = active;
        active = null;
        if (dialog != null) dialog.dismiss();
    }

    private static Activity activity(Context context) {
        for (int depth = 0; depth < 8; depth++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return null;
            context = base;
        }
        return null;
    }

    private static ViewGroup ancestor(View view, Class<? extends ViewGroup> type) {
        View current = view;
        while (current != null) {
            if (type.isInstance(current)) return (ViewGroup) current;
            if (!(current.getParent() instanceof View)) return null;
            current = (View) current.getParent();
        }
        return null;
    }

    private static void retainPreviewPath(ViewGroup parent, View content, View image, View name, View provider) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            boolean keep = child == content || child == image || child == name || child == provider ||
                    contains(child, content) || contains(child, image) || contains(child, name) || contains(child, provider);
            child.setVisibility(keep ? View.VISIBLE : View.GONE);
            if (keep && child instanceof ViewGroup && child != image && child != name && child != provider) {
                retainPreviewPath((ViewGroup) child, content, image, name, provider);
            }
        }
    }

    private static boolean contains(View root, View wanted) {
        if (root == wanted) return true;
        if (!(root instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (contains(group.getChildAt(index), wanted)) return true;
        }
        return false;
    }
}

