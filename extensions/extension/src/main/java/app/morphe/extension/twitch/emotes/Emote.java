package app.morphe.extension.twitch.emotes;

final class Emote {
    final String name;
    final String url;
    final boolean animated;
    final boolean zeroWidth;
    final int provider;

    Emote(String name, String url, boolean animated) {
        this(name, url, animated, false);
    }

    Emote(String name, String url, boolean animated, boolean zeroWidth) {
        this(name, url, animated, zeroWidth, providerFor(url));
    }

    Emote(String name, String url, boolean animated, boolean zeroWidth, int provider) {
        this.name = name;
        this.url = url;
        this.animated = animated;
        this.zeroWidth = zeroWidth;
        this.provider = provider;
    }

    String providerLabel() {
        return provider == 0 ? "BTTV" : provider == 1 ? "7TV" : provider == 2 ? "FrankerFaceZ" : "Third-party";
    }

    private static int providerFor(String url) {
        String value = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("betterttv.net")) return 0;
        if (value.contains("7tv.app") || value.contains("7tv.io")) return 1;
        if (value.contains("frankerfacez.com") || value.contains("ffz")) return 2;
        return -1;
    }
}

