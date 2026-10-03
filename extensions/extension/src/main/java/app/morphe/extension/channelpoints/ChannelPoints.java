package app.morphe.extension.channelpoints;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import app.morphe.extension.Utils;
import app.morphe.extension.settings.Settings;
import app.morphe.extension.twitch.emotes.EmoteRepo;

/**
 * UI-independent Twitch Channel Points bonus claimant.
 *
 * The claim path deliberately stays outside Twitch's CommunityPointsModel/provider lifecycle.
 * It asks Twitch GraphQL for the current channel's availableClaim and submits Twitch's own
 * ClaimCommunityPoints mutation. The visible bonus chest is not required to exist on screen.
 *
 * The architecture is adapted from the working PurpleTV ReVive implementation and the query
 * shape is taken from the exact Twitch 31.3.1 APK reference stored in reference/channel-points/.
 */
public final class ChannelPoints {
    private static final String TAG = "kizu-cp";
    private static final long POLL_INTERVAL_MS = 30_000L;

    private static final String CHANNEL_POINTS_QUERY =
            "query CommunityPointsSettingsQuery($id: ID!) {" +
            " user(id: $id) {" +
            "  channel { communityPointsSettings { isEnabled isAvailable } }" +
            "  self {" +
            "   communityPoints {" +
            "    balance" +
            "    availableClaim {" +
            "     id pointsEarnedTotal pointsEarnedBaseline" +
            "     multipliers { factor reasonCode }" +
            "    }" +
            "    activeMultipliers { factor reasonCode }" +
            "   }" +
            "  }" +
            " }" +
            "}";

    // PurpleTV ReVive fallback persisted-query hash.
    private static final String FALLBACK_CONTEXT_HASH =
            "1530a003a7d374b0380b79db0be0534f30ff46e61cffa2bc0e2468a909fbc024";

    private static final String CLAIM_HASH =
            "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0";

    private static final Object LOCK = new Object();

    private static volatile String channelId;
    private static volatile String channelLogin;
    private static volatile String lastSuccessfulClaimKey;
    private static volatile boolean started;

    private ChannelPoints() {}

    public static void start(Context appContext) {
        if (appContext == null) return;

        synchronized (LOCK) {
            if (started) return;
            started = true;
        }

        final Context context = appContext.getApplicationContext();
        Thread watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                log("GraphQL auto-claim watcher started");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                            claimAvailable(context);
                        }
                    } catch (Throwable error) {
                        log("watcher error: " + error);
                    }

                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "kizu-channel-points");

        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Called from Kizu's stable ChannelChatConnectionKey(String,String) hook.
     */
    public static void onChannelChanged(String id, String login) {
        String normalizedId = normalize(id);
        String normalizedLogin = normalize(login);

        if (normalizedId == null && normalizedLogin == null) return;

        channelId = normalizedId;
        channelLogin = normalizedLogin;
        lastSuccessfulClaimKey = null;
        log("active channel id=" + normalizedId + " login=" + normalizedLogin);
    }

    private static void claimAvailable(Context context) {
        String id = channelId;
        String login = channelLogin;

        if (id == null || id.isEmpty() || login == null || login.isEmpty()) return;

        String token = EmoteRepo.authToken(context);
        if (token == null || token.isEmpty()) return;

        JSONObject claim = fetchAvailableClaim(token, id, login);
        if (claim == null) return;

        String claimId = claim.optString("id", "");
        if (claimId.isEmpty()) return;

        String claimKey = id + ":" + claimId;
        if (claimKey.equals(lastSuccessfulClaimKey)) return;

        log("available bonus detected channel=" + login + " claim=" + claimId);

        ClaimResult result = submitClaim(token, id, claimId);
        if (result.success) {
            lastSuccessfulClaimKey = claimKey;
            String message = result.points > 0
                    ? "Channel Points +" + result.points + " claimed"
                    : "Channel Points bonus claimed";
            log("claim success: " + message);
            Utils.showClaimStatus(message);
        } else {
            log("claim failed: " + result.error);
            Utils.showClaimStatus("Channel Points claim failed");
        }
    }

    private static JSONObject fetchAvailableClaim(String token, String id, String login) {
        try {
            JSONObject body = new JSONObject()
                    .put("operationName", "CommunityPointsSettingsQuery")
                    .put("variables", new JSONObject().put("id", id))
                    .put("query", CHANNEL_POINTS_QUERY);

            JSONObject result = parseObject(EmoteRepo.gqlPost(token, body.toString()));
            JSONObject claim = extractTwitch31Claim(result);
            if (claim != null) return claim;

            // Fallback to the exact PurpleTV ReVive context request for installations where
            // the gateway rejects the inline query shape but still accepts the persisted query.
            JSONObject fallback = new JSONObject()
                    .put("operationName", "ChannelPointsContext")
                    .put("variables", new JSONObject().put("channelLogin", login))
                    .put("extensions", new JSONObject().put(
                            "persistedQuery",
                            new JSONObject()
                                    .put("version", 1)
                                    .put("sha256Hash", FALLBACK_CONTEXT_HASH)));

            result = parseObject(EmoteRepo.gqlPost(token, fallback.toString()));
            return extractPurpleTvClaim(result);
        } catch (Throwable error) {
            log("context error: " + error);
            return null;
        }
    }

    private static JSONObject extractTwitch31Claim(JSONObject root) {
        if (root == null || hasErrors(root)) return null;

        JSONObject data = root.optJSONObject("data");
        if (data == null) return null;

        JSONObject user = data.optJSONObject("user");
        if (user == null) return null;

        JSONObject self = user.optJSONObject("self");
        if (self == null) return null;

        JSONObject communityPoints = self.optJSONObject("communityPoints");
        return communityPoints == null ? null : communityPoints.optJSONObject("availableClaim");
    }

    private static JSONObject extractPurpleTvClaim(JSONObject root) {
        if (root == null || hasErrors(root)) return null;

        JSONObject data = root.optJSONObject("data");
        if (data == null) return null;

        JSONObject community = data.optJSONObject("community");
        if (community == null) return null;

        JSONObject channel = community.optJSONObject("channel");
        if (channel == null) return null;

        JSONObject self = channel.optJSONObject("self");
        if (self == null) return null;

        JSONObject communityPoints = self.optJSONObject("communityPoints");
        return communityPoints == null ? null : communityPoints.optJSONObject("availableClaim");
    }

    private static ClaimResult submitClaim(String token, String id, String claimId) {
        try {
            JSONObject input = new JSONObject()
                    .put("channelID", id)
                    .put("claimID", claimId);

            JSONObject body = new JSONObject()
                    .put("operationName", "ClaimCommunityPoints")
                    .put("variables", new JSONObject().put("input", input))
                    .put("extensions", new JSONObject().put(
                            "persistedQuery",
                            new JSONObject()
                                    .put("version", 1)
                                    .put("sha256Hash", CLAIM_HASH)));

            JSONObject root = parseObject(EmoteRepo.gqlPost(token, body.toString()));
            if (root == null) return ClaimResult.failed("invalid response");
            if (hasErrors(root)) return ClaimResult.failed(firstError(root));

            JSONObject data = root.optJSONObject("data");
            if (data == null) return ClaimResult.failed("missing data");

            JSONObject payload = data.optJSONObject("claimCommunityPoints");
            if (payload == null) return ClaimResult.failed("missing claim payload");

            JSONObject error = payload.optJSONObject("error");
            if (error != null) {
                return ClaimResult.failed(error.optString("code", "unknown"));
            }

            JSONObject claim = payload.optJSONObject("claim");
            int points = 0;
            if (claim != null) {
                int total = claim.optInt("pointsEarnedTotal", 0);
                int baseline = claim.optInt("pointsEarnedBaseline", 0);
                if (total >= baseline) points = total - baseline;
            }

            return ClaimResult.success(points);
        } catch (Throwable error) {
            return ClaimResult.failed(error.toString());
        }
    }

    private static JSONObject parseObject(String response) {
        if (response == null || response.isEmpty()) return null;
        try {
            return new JSONObject(response);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasErrors(JSONObject root) {
        JSONArray errors = root.optJSONArray("errors");
        return errors != null && errors.length() > 0;
    }

    private static String firstError(JSONObject root) {
        JSONArray errors = root.optJSONArray("errors");
        if (errors == null || errors.length() == 0) return "unknown error";
        JSONObject first = errors.optJSONObject(0);
        if (first == null) return "unknown error";
        return first.optString("message", "unknown error");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void log(String message) {
        android.util.Log.d(TAG, message);
    }

    private static final class ClaimResult {
        final boolean success;
        final int points;
        final String error;

        private ClaimResult(boolean success, int points, String error) {
            this.success = success;
            this.points = points;
            this.error = error;
        }

        static ClaimResult success(int points) {
            return new ClaimResult(true, points, null);
        }

        static ClaimResult failed(String error) {
            return new ClaimResult(false, 0, error == null ? "unknown error" : error);
        }
    }
}
