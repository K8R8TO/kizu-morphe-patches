package app.morphe.extension.twitch.emotes

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.style.ImageSpan
import android.widget.TextView
import io.github.bakwudo.uyu.extension.settings.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object EmoteSupport {
    private val emotes = ConcurrentHashMap<String, Emote>()
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onChannelChanged(channelId: String, channelLogin: String) {
        if (appContext == null) return
        emotes.clear()
        thread {
            if (Settings.EMOTES_BTTV.get()) fetchBttv(channelId)
            if (Settings.EMOTES_FFZ.get()) fetchFfz(channelId)
            if (Settings.EMOTES_7TV.get()) fetch7tv(channelId)
        }
    }

    fun bind(textView: TextView) {
        val text = textView.text.toString()
        if (text.isEmpty()) return

        val spannable = Spannable.Factory.getInstance().newSpannable(text)
        var modified = false
        var hasAnimated = false
        val words = text.split(" ")
        var currentIndex = 0

        for (word in words) {
            val emote = emotes[word]
            if (emote != null) {
                try {
                    val drawable = createEmoteDrawable(emote)
                    if (drawable != null) {
                        val size = textView.textSize.toInt()
                        drawable.setBounds(0, 0, size, size)
                        val span = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                        spannable.setSpan(
                            span, currentIndex, currentIndex + word.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        if (drawable is AnimatedImageDrawable) hasAnimated = true
                        modified = true
                    }
                } catch (_: Exception) {}
            }
            currentIndex += word.length + 1
        }

        if (modified) {
            textView.text = spannable
            if (hasAnimated) startTicker(textView)
        }
    }

    private fun startTicker(view: TextView) {
        handler.post(object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                view.invalidate()
                handler.postDelayed(this, 40)
            }
        })
    }

    private fun createEmoteDrawable(emote: Emote): Drawable? {
        val context = appContext ?: return null
        return try {
            val connection = (URL(emote.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            val wantAnimated = emote.isAnimated && Settings.EMOTES_ANIMATED.get() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            if (wantAnimated) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(source)
                (drawable as? AnimatedImageDrawable)?.start()
                drawable
            } else {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) null else BitmapDrawable(context.resources, bitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- BTTV ----------
    private fun fetchBttv(channelId: String) {
        try {
            addBttvArray(JSONArray(getJson(URL("https://api.betterttv.net/3/cached/emotes/global"))))
            val json = JSONObject(getJson(URL("https://api.betterttv.net/3/cached/users/twitch/$channelId")))
            addBttvArray(json.getJSONArray("channelEmotes"))
            addBttvArray(json.getJSONArray("sharedEmotes"))
        } catch (_: Exception) {}
    }

    private fun addBttvArray(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val id = e.getString("id")
            emotes[e.getString("code")] = Emote(
                id,
                e.getString("code"),
                "https://cdn.betterttv.net/emote/$id/2x",
                e.optString("imageType", "png") != "png",
            )
        }
    }

    // ---------- FFZ ----------
    private fun fetchFfz(channelId: String) {
        try {
            val json = JSONObject(getJson(URL("https://api.frankerfacez.com/v1/room/$channelId")))
            val sets = json.getJSONObject("sets")
            parseFfzSet(sets.getJSONObject(json.getJSONObject("room").getString("set")))
            val global = JSONObject(getJson(URL("https://api.frankerfacez.com/v1/set/global")))
            val gsets = global.getJSONObject("sets")
            val keys = gsets.keys()
            while (keys.hasNext()) parseFfzSet(gsets.getJSONObject(keys.next()))
        } catch (_: Exception) {}
    }

    private fun parseFfzSet(set: JSONObject) {
        val arr = set.getJSONArray("emoticons")
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val urls = e.getJSONObject("urls")
            var url = urls.optString("4", urls.optString("2", urls.optString("1", "")))
            if (url.startsWith("//")) url = "https:$url"
            if (url.isNotEmpty()) {
                emotes[e.getString("name")] = Emote(
                    e.getString("id"),
                    e.getString("name"),
                    url,
                    e.optString("image_type", "png") != "png",
                )
            }
        }
    }

    // ---------- 7TV ----------
    private fun fetch7tv(channelId: String) {
        try {
            val user = JSONObject(getJson(URL("https://7tv.io/v3/users/twitch/$channelId")))
            user.optJSONObject("emote_set")?.let { parse7tvSet(it) }
            val global = JSONObject(getJson(URL("https://7tv.io/v3/emote-sets/global")))
            parse7tvSet(global)
        } catch (_: Exception) {}
    }

    private fun parse7tvSet(set: JSONObject) {
        val arr = set.optJSONArray("emotes") ?: return
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val data = e.optJSONObject("data") ?: continue
            val files = data.optJSONArray("files") ?: continue
            var chosen: JSONObject? = null
            for (f in 0 until files.length()) {
                val file = files.getJSONObject(f)
                when (file.optString("format")) {
                    "WEBP" -> { chosen = file; break }
                    "GIF" -> { if (chosen == null) chosen = file }
                    "PNG" -> { if (chosen == null) chosen = file }
                }
            }
            val file = chosen ?: continue
            val host = data.getJSONObject("host")
            val url = "https:" + host.getString("url") + "/" + file.getString("name")
            emotes[e.getString("name")] = Emote(
                data.optString("id"),
                e.getString("name"),
                url,
                data.optBoolean("animated", false) || file.optString("format") == "GIF",
            )
        }
    }

    private fun getJson(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    data class Emote(val id: String, val name: String, val url: String, val isAnimated: Boolean)
}
