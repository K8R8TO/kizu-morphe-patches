package app.morphe.extension;

import android.content.Context;

import app.morphe.extension.twitch.emotes.EmoteSupport;

public final class Utils {
    private Utils() {}

    public static void setContext(Context context) {
        EmoteSupport.init(context);
    }
}
