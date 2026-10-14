package app.morphe.extension.twitch.emotes;
import android.view.View;
import java.util.List;
public final class EmotePickerBridge {
 private EmotePickerBridge() {}
 public static Object emote(String id,String code,boolean animated) { return null; }
 public static Object section(String title,List<?> entries) { return null; }
 public static void publish(Object presenter,List<?> sections,boolean visible) {}
 public static void scroll(Object delegate,View root,int position) {}
 public static int footerId() { return 0; }
 public static int itemCount(List<?> sections) { return 0; }
 public static int insertionIndex(List<?> sections,String channel) { return 0; }
}
