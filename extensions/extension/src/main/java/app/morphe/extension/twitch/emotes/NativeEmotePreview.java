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
        if (!(content.getParent() instanceof FrameLayout) || originalImage == null || name == null || provider == null) {
            return;
        }

        ViewGroup section = (ViewGroup) content.getParent();
        for (int index = 0; index < section.getChildCount(); index++) {
            View child = section.getChildAt(index);
            child.setVisibility(child == content ? View.VISIBLE : View.GONE);
        }
        for (int index = 0; index < content.getChildCount(); index++) {
            View child = content.getChildAt(index);
            child.setVisibility(child == originalImage || child == name || child == provider ? View.VISIBLE : View.GONE);
        }

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
}

