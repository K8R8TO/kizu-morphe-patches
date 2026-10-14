package app.morphe.extension.twitch.emotes;
import java.net.URI;
final class EmoteProviders {
 static String channelId(String id) { return id != null && id.matches("[1-9][0-9]{0,19}") ? id : null; }
 static boolean imageUrl(String url) {
  try { URI u=URI.create(url); String h=u.getHost(); return "https".equals(u.getScheme()) && u.getUserInfo()==null && u.getPort()==-1 && ("cdn.betterttv.net".equals(h)||"cdn.7tv.app".equals(h)||"cdn.7tv.io".equals(h)||"cdn.frankerfacez.com".equals(h)); }
  catch (IllegalArgumentException e) { return false; }
 }
 private EmoteProviders() {}
}
