package app.morphe.extension.twitch.emotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.style.ImageSpan
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object EmoteSupport {
    private val emotes = ConcurrentHashMap<String, Emote>()
    private var appContext: Context? = null

    // This MUST be called when the app starts (usually in SharedExtensionPatch)
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onChannelChanged(channelId: String, channelLogin: String) {
        if (appContext == null) return
        
        // Clear previous channel emotes
        emotes.clear()
        
        // Run network requests on a background thread so we don't freeze the chat
        thread {
            fetchBttv(channelId)
            fetchFfz(channelId)
            fetch7tv(channelId)
        }
    }

    fun bind(textView: TextView) {
        val context = appContext ?: return
        val text = textView.text.toString()
        if (text.isEmpty()) return

        val spannable = Spannable.Factory.getInstance().newSpannable(text)
        var modified = false
        val words = text.split(" ")
        var currentIndex = 0

        for (word in words) {
            val emote = emotes[word]
            if (emote != null) {
                try {
                    val drawable = createEmoteDrawable(emote)
                    if (drawable != null) {
                        // Set bounds to match text size
                        val size = textView.textSize.toInt()
                        drawable.setBounds(0, 0, size, size)
                        
                        val span = ImageSpan(drawable, ImageSpan.ALIGN_BASELINE)
                        spannable.setSpan(span, currentIndex, currentIndex + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        modified = true
                    }
                } catch (e: Exception) {
                    // Failed to load image
                }
            }
            currentIndex += word.length + 1
        }

        if (modified) {
            textView.text = spannable
        }
    }

    private fun createEmoteDrawable(emote: Emote): Drawable? {
        val context = appContext ?: return null
        return try {
            val url = URL(emote.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val inputStream = connection.inputStream

            if (emote.isAnimated) {
                // For animated emotes, we need a Movie or a custom Drawable
                val movie = Movie.decodeStream(inputStream)
                if (movie != null) {
                    MovieDrawable(movie)
                } else {
                    null
                }
            } else {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                BitmapDrawable(context.resources, bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    // --- API Fetching Logic ---

    private fun fetchBttv(channelId: String) {
        try {
            val url = URL("https://api.betterttv.net/3/cached/users/twitch/$channelId")
            val json = JSONObject(getJson(url))
            
            // Channel emotes
            val channelEmotes = json.getJSONArray("channelEmotes")
            for (i in 0 until channelEmotes.length()) {
                val emoteJson = channelEmotes.getJSONObject(i)
                val id = emoteJson.getString("id")
                val code = emoteJson.getString("code")
                val imageUrl = "https://cdn.betterttv.net/emote/$id/2x"
                emotes[code] = Emote(id, code, imageUrl, false)
            }

            // Shared emotes
            val sharedEmotes = json.getJSONArray("sharedEmotes")
            for (i in 0 until sharedEmotes.length()) {
                val emoteJson = sharedEmotes.getJSONObject(i)
                val id = emoteJson.getString("id")
                val code = emoteJson.getString("code")
                val imageUrl = "https://cdn.betterttv.net/emote/$id/2x"
                emotes[code] = Emote(id, code, imageUrl, false)
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun fetchFfz(channelId: String) {
        try {
            val url = URL("https://api.frankerfacez.com/v1/room/$channelId")
            val json = JSONObject(getJson(url))
            val sets = json.getJSONObject("sets")
            
            val roomId = json.getJSONObject("room").getString("set")
            val emotesArray = sets.getJSONObject(roomId).getJSONArray("emoticons")
            
            for (i in 0 until emotesArray.length()) {
                val emoteJson = emotesArray.getJSONObject(i)
                val id = emoteJson.getString("id")
                val name = emoteJson.getString("name")
                // FFZ URLs are usually in a "urls" object
                val urls = emoteJson.getJSONObject("urls")
                var imageUrl = ""
                if (urls.has("4")) imageUrl = urls.getString("4")
                else if (urls.has("2")) imageUrl = urls.getString("2")
                else if (urls.has("1")) imageUrl = urls.getString("1")
                
                if (imageUrl.isNotEmpty()) {
                    emotes[name] = Emote(id, name, imageUrl, false)
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun fetch7tv(channelId: String) {
        // 7TV API is more complex. 
        // We'll use a simplified approach for now.
        // In a real implementation, you'd need to handle the 7TV API v3 structure.
        // For now, let's skip 7TV or use a placeholder.
    }

    private fun getJson(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    data class Emote(val id: String, val name: String, val url: String, val isAnimated: Boolean)

    // Custom Drawable for Animated Emotes (Movie)
    class MovieDrawable(private val movie: Movie) : Drawable() {
        private var startTime = 0L
        override fun draw(canvas: Canvas) {
            if (startTime == 0L) startTime = System.currentTimeMillis()
            val relTime = ((System.currentTimeMillis() - startTime) % movie.duration()).toInt()
            movie.setTime(relTime)
            movie.draw(canvas, 0f, 0f)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = movie.width()
        override fun getIntrinsicHeight(): Int = movie.height()
    }
}
