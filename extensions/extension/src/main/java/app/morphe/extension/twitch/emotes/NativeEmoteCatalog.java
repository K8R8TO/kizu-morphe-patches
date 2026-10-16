package app.morphe.extension.twitch.emotes;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native Twitch emotes used only for autocomplete suggestions.
 *
 * The bundled global list makes native suggestions available before any network catalog loads.
 * A best-effort read of Twitch's own emotes.db adds account-specific/recent native emotes without
 * replacing Twitch's native message rendering.
 */
final class NativeEmoteCatalog {
    private static final String TAG = "KizuNativeEmotes";
    private static final String GLOBAL_DATA =
            "VhagarChamp|emotesv2_09a0aeda7a6b4722b97429ddb591e028\n" +
            "Yagoo|emotesv2_de2cc5fc92c645a29d0a4f40a9e7cde5\n" +
            "SipTime|emotesv2_ba5ae4be5c724ca59d649fa713ff0730\n" +
            "EleGiggle|emotesv2_cb0cec2c9975497fa093be1c3276dd92\n" +
            "FeverFighter|emotesv2_fc2f39b6a62c4d7e832993eab17547d2\n" +
            "WeDidThat|emotesv2_a7ab2c184e334d4a9784e6e5d51947f7\n" +
            "PewPewPew|emotesv2_587405136a8147148c77df74baaa1bf4\n" +
            "JinxLUL|emotesv2_4e1c5651219a462894aefa8b6720efc5\n" +
            "FeelsVi|emotesv2_4b51b45f35df4dd8ad45a611c9a9ec35\n" +
            "AmbessaLove|emotesv2_a829c76ca15f49a2bf71e1270f83fe83\n" +
            "EkkoChest|emotesv2_db169cf5364e4634b529eef8b0616d94\n" +
            "CaitThinking|emotesv2_50b3304bc0884c6792f13615db072a5c\n" +
            "Cinheimer|emotesv2_d351c5d5e9084402b30bc39eaa3d92ae\n" +
            "BratChat|emotesv2_f2fd474fbe964964b2570671575b39ea\n" +
            "BigSad|emotesv2_db3cd788399347a8b2ebfb8a85f5badb\n" +
            "GRASSLORD|emotesv2_973cf33af6e14a92b7c5b970a9d55afd\n" +
            "TWITH|emotesv2_5fa0f9da251941988f31b6d7632c021c\n" +
            "SUBtember|emotesv2_bfb533e2253044f3a77d0032b2354c0b\n" +
            "AnotherRecord|emotesv2_9eade28238d64e83b0219a9025d4692d\n" +
            "GoatEmotey|emotesv2_e41e4d6808224f25ae1fb625aa26de63\n" +
            "GoldPLZ|emotesv2_c1f4899e65cf4f53b2fd98e15733973a\n" +
            "TwitchConHYPE|emotesv2_13b6dd7f3a3146ef8dc10f66d8b42a96\n" +
            "PopNemo|emotesv2_5d523adb8bbb4786821cd7091e47da21\n" +
            "DinoDance|emotesv2_dcd06b30a5c24f6eb871e8f5edbd44f7\n" +
            "NewRecord|emotesv2_53f6a6af8b0e453d874bbefee49b3e73\n" +
            "SUBprise|emotesv2_fcbeed664f7c47d6ba3b57691275ef51\n" +
            "ImTyping|emotesv2_b0c6ccb3b12b4f99a9cc83af365a09f1\n" +
            "Shush|emotesv2_819621bcb8f44566a1bd8ea63d06c58f\n" +
            "MyAvatar|emotesv2_c0c9c932c82244ff920ad2134be90afb\n" +
            "PizzaTime|emotesv2_f202746ed88f4e7c872b50b1f7fd78cc\n" +
            "LaundryBasket|emotesv2_ecb0bfd49b3c4325864b948d46c8152b\n" +
            "ModLove|emotesv2_a2dfbbbbf66f4a75b0f53db841523e6c\n" +
            "Jebasted|emotesv2_031bf329c21040a897d55ef471da3dd3\n" +
            "TransgenderPride|307827377\n" +
            "PansexualPride|307827370\n" +
            "NonbinaryPride|307827356\n" +
            "LesbianPride|307827340\n" +
            "IntersexPride|307827332\n" +
            "GenderFluidPride|307827326\n" +
            "GayPride|307827321\n" +
            "BisexualPride|307827313\n" +
            "AsexualPride|307827267\n" +
            "PogChamp|305954156\n" +
            "GlitchNRG|304489309\n" +
            "GlitchLit|304489128\n" +
            "StinkyGlitch|304486324\n" +
            "GlitchCat|304486301\n" +
            "FootGoal|302628617\n" +
            "FootYellow|302628613\n" +
            "FootBall|302628600\n" +
            "BlackLivesMatter|302537250\n" +
            "ExtraLife|302426269\n" +
            "VirtualHug|301696583\n" +
            "o_o|555555575\n" +
            "BOP|301428702\n" +
            "SingsNote|300116350\n" +
            "SingsMic|300116349\n" +
            "TwitchSings|300116344\n" +
            "SoonerLater|2113050\n" +
            "HolidayTree|1713825\n" +
            "HolidaySanta|1713822\n" +
            "HolidayPresent|1713819\n" +
            "HolidayLog|1713816\n" +
            "HolidayCookie|1713813\n" +
            "PixelBob|1547903\n" +
            "FBPenalty|1441289\n" +
            "FBChallenge|1441285\n" +
            "FBCatch|1441281\n" +
            "FBBlock|1441276\n" +
            "FBSpiral|1441273\n" +
            "FBPass|1441271\n" +
            "FBRun|1441261\n" +
            "MaxLOL|1290325\n" +
            "TwitchRPG|1220086\n" +
            "PinkMercy|1003190\n" +
            "MercyWing2|1003189\n" +
            "MercyWing1|1003187\n" +
            "PartyHat|965738\n" +
            "EarthDay|959018\n" +
            "TombRaid|864205\n" +
            "PopCorn|724216\n" +
            "FBtouchdown|626795\n" +
            "TPFufun|508650\n" +
            "TwitchVotes|479745\n" +
            "DarkMode|461298\n" +
            "HSWP|446979\n" +
            "HSCheers|444572\n" +
            "PowerUpL|425688\n" +
            "PowerUpR|425671\n" +
            "LUL|425618\n" +
            "EntropyWins|376765\n" +
            "TPcrunchyroll|323914\n" +
            "TwitchUnity|196892\n" +
            "Squid4|191767\n" +
            "Squid3|191764\n" +
            "Squid2|191763\n" +
            "Squid1|191762\n" +
            "CrreamAwk|191313\n" +
            "CarlSmile|166266\n" +
            "TwitchLit|166263\n" +
            "TehePelo|160404\n" +
            "TearGlove|160403\n" +
            "SabaPing|160402\n" +
            "PunOko|160401\n" +
            "KonCha|160400\n" +
            "Kappu|160397\n" +
            "InuyoFace|160396\n" +
            "BigPhish|160395\n" +
            "BegWan|160394\n" +
            "ThankEgg|160392\n" +
            "MorphinTime|156787\n" +
            "TheIlluminati|145315\n" +
            "TBAngel|143490\n" +
            "MVGame|142140\n" +
            "NinjaGrumpy|138325\n" +
            "PartyTime|135393\n" +
            "RlyTho|134256\n" +
            "UWot|134255\n" +
            "YouDontSay|134254\n" +
            "KAPOW|133537\n" +
            "ItsBoshyTime|133468\n" +
            "CoolStoryBob|123171\n" +
            "TriHard|120232\n" +
            "SuperVinlin|118772\n" +
            "FreakinStinkin|117701\n" +
            "Poooound|117484\n" +
            "CurseLit|116625\n" +
            "BatChest|115234\n" +
            "BrainSlug|115233\n" +
            "PrimeMe|115075\n" +
            "StrawBeary|114876\n" +
            "RaccAttack|114870\n" +
            "UncleNox|114856\n" +
            "WTRuck|114847\n" +
            "TooSpicy|114846\n" +
            "Jebaited|114836\n" +
            "DogFace|114835\n" +
            "BlargNaut|114738\n" +
            "TakeNRG|112292\n" +
            "GivePLZ|112291\n" +
            "imGlitch|112290\n" +
            "pastaThat|112289\n" +
            "copyThis|112288\n" +
            "UnSane|111792\n" +
            "DatSheffy|111700\n" +
            "TheTarFu|111351\n" +
            "PicoMause|111300\n" +
            "TinyFace|111119\n" +
            "DxCat|110734\n" +
            "RuleFive|107030\n" +
            "VoteNay|106294\n" +
            "VoteYea|106293\n" +
            "PJSugar|102556\n" +
            "DoritosChip|102242\n" +
            "OpieOP|100590\n" +
            "FutureMan|98562\n" +
            "ChefFrank|90129\n" +
            "StinkyCheese|90076\n" +
            "NomNom|90075\n" +
            "SmoocherZ|89945\n" +
            "cmonBruh|84608\n" +
            "KappaWealth|81997\n" +
            "MikeHogu|81636\n" +
            "VoHiYo|81274\n" +
            "KomodoHype|81273\n" +
            "SeriousSloth|81249\n" +
            "OSFrog|81248\n" +
            "OhMyDog|81103\n" +
            "KappaClaus|74510\n" +
            "KappaRoss|70433\n" +
            "MingLee|68856\n" +
            "SeemsGood|64138\n" +
            "twitchRaid|62836\n" +
            "bleedPurple|62835\n" +
            "duDudu|62834\n" +
            "riPepperonis|62833\n" +
            "NotLikeThis|58765\n" +
            "DendiFace|58135\n" +
            "CoolCat|58127\n" +
            "KappaPride|55338\n" +
            "ShadyLulu|52492\n" +
            "ArgieB8|51838\n" +
            "CorgiDerp|49106\n" +
            "PraiseIt|38586\n" +
            "TTours|38436\n" +
            "mcaT|35063\n" +
            "NotATK|34875\n" +
            "HeyGuys|30259\n" +
            "Mau5|30134\n" +
            "PRChase|28328\n" +
            "WutFace|28087\n" +
            "BuddhaBar|27602\n" +
            "PermaSmug|27509\n" +
            "panicBasket|22998\n" +
            "BabyRage|22639\n" +
            "HassaanChop|20225\n" +
            "TheThing|7427\n" +
            "RitzMitz|4338\n" +
            "YouWHY|4337\n" +
            "PipeHype|4240\n" +
            "BrokeBack|4057\n" +
            "ANELE|3792\n" +
            "PanicVis|3668\n" +
            "GrammarKing|3632\n" +
            "PeoplesChamp|3412\n" +
            "SoBayed|1906\n" +
            "BigBrother|1904\n" +
            "Keepo|1902\n" +
            "Kippa|1901\n" +
            "RalpherZ|1900\n" +
            "TF2John|1899\n" +
            "ThunBeast|1898\n" +
            "WholeWheat|1896\n" +
            "DAESuppy|973\n" +
            "FailFish|360\n" +
            "HotPokket|357\n" +
            "4Head|354\n" +
            "ResidentSleeper|245\n" +
            "FUNgineer|244\n" +
            "PMSTwin|92\n" +
            "ShazBotstix|87\n" +
            "AsianGlow|74\n" +
            "DBstyle|73\n" +
            "BloodTrail|69\n" +
            "OneHand|66\n" +
            "FrankerZ|65\n" +
            "SMOrc|52\n" +
            "ArsonNoSexy|50\n" +
            "PunchTrees|47\n" +
            "SSSsss|46\n" +
            "Kreygasm|41\n" +
            "KevinTurtle|40\n" +
            "PJSalt|36\n" +
            "SwiftRage|34\n" +
            "DansGame|33\n" +
            "GingerPower|32\n" +
            "BCWarrior|30\n" +
            "MrDestructoid|28\n" +
            "JonCarnage|26\n" +
            "Kappa|25\n" +
            "RedCoat|22\n" +
            "TheRinger|18\n" +
            "StoneLightning|17\n" +
            "OptimizePrime|16\n" +
            "JKanStyle|15\n";
    private static final Map<String, Emote> EMOTES = new LinkedHashMap<>();
    private static final AtomicBoolean DB_LOAD_STARTED = new AtomicBoolean(false);

    static {
        for (String record : GLOBAL_DATA.split("\\n")) {
            int separator = record.indexOf('|');
            if (separator <= 0 || separator >= record.length() - 1) continue;
            String name = record.substring(0, separator);
            String id = record.substring(separator + 1);
            put(name, id);
        }
    }

    private NativeEmoteCatalog() { }

    private static synchronized void put(String name, String id) {
        if (name == null || id == null || !name.matches("[A-Za-z0-9_]{2,}") || id.isEmpty()) return;
        String key = name.toLowerCase(Locale.ROOT);
        EMOTES.put(key, new Emote(name,
                "https://static-cdn.jtvnw.net/emoticons/v2/" + id + "/static/dark/2.0",
                false, false, -1));
    }

    static void ensureLoaded(Context context) {
        if (context == null || !DB_LOAD_STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        Thread worker = new Thread(() -> loadRecentNativeEmotes(app), "kizu-native-emote-db");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loadRecentNativeEmotes(Context context) {
        int before = size();
        File file = context.getDatabasePath("emotes.db");
        if (file == null || !file.isFile()) {
            Log.i(TAG, "Twitch emotes.db is unavailable; using bundled global list (" + before + ")");
            return;
        }
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = database.rawQuery("SELECT token, id FROM frequent_emotes", null)) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String id = cursor.getString(1);
                if (name != null && id != null) put(name, id);
            }
            Log.i(TAG, "Merged Twitch emotes.db entries; native autocomplete catalog size=" + size());
        } catch (Throwable error) {
            Log.i(TAG, "Could not read Twitch emotes.db; bundled globals remain available", error);
            return;
        }
        if (size() != before) EmoteAutocomplete.refreshAll();
    }

    static synchronized int size() {
        return EMOTES.size();
    }

    static synchronized List<Emote> snapshot() {
        return new java.util.ArrayList<>(EMOTES.values());
    }
}
