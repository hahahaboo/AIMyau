package myau.management.altmanager.auth.refresh.request;

import com.google.gson.JsonObject;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.MicrosoftTokenResponse;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.CLIENT;
import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.GSON;

public final class RefreshTokenRequest {

    private static final String REFRESH_TOKENS_URL = "https://login.live.com/oauth20_token.srf";

    // AIMyau / OAuth GUI client (used by MicrosoftOAuthTranslation)
    private static final String CUSTOM_CLIENT_ID = "9fbc7315-7200-4b2b-a655-bb38c865da17";
    private static final String CUSTOM_CLIENT_SECRET = "Bzn8Q~YryydJsydgnnxHgJq.NM3Oo4.AEEohLbBb";
    private static final String CUSTOM_REDIRECT_URI = "http://localhost:8247";
    private static final String CUSTOM_SCOPE = "XboxLive.signin offline_access";

    // Minecraft Launcher style client (fallback)
    private static final String LAUNCHER_CLIENT_ID = "00000000402b5328";
    private static final String LAUNCHER_REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String LAUNCHER_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    /**
     * Refresh an OAuth token.
     * Tries the custom client first (same as OAuth GUI),
     * then falls back to the Minecraft Launcher client.
     */
    public static MicrosoftTokenResponse refreshToken(String refreshToken) throws IOException, AuthenticationException {
        AuthenticationException customError = null;

        try {
            System.out.println("[RefreshToken] Trying custom client (9fbc7315-...)");
            return doRefresh(buildCustomPayload(refreshToken));
        } catch (AuthenticationException e) {
            customError = e;
            System.out.println("[RefreshToken] Custom client failed: " + e.getMessage());
        }

        try {
            System.out.println("[RefreshToken] Falling back to launcher client (00000000402b5328)");
            return doRefresh(buildLauncherPayload(refreshToken));
        } catch (AuthenticationException e) {
            System.out.println("[RefreshToken] Launcher client also failed: " + e.getMessage());
            // Prefer the more specific first error if both fail
            String msg = customError != null
                    ? "Custom client: " + customError.getMessage() + " | Launcher client: " + e.getMessage()
                    : e.getMessage();
            throw new AuthenticationException(msg);
        }
    }

    private static FormBody buildCustomPayload(String refreshToken) {
        return new FormBody.Builder()
                .add("client_id", CUSTOM_CLIENT_ID)
                .add("client_secret", CUSTOM_CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("redirect_uri", CUSTOM_REDIRECT_URI)
                .add("scope", CUSTOM_SCOPE)
                .build();
    }

    private static FormBody buildLauncherPayload(String refreshToken) {
        return new FormBody.Builder()
                .add("client_id", LAUNCHER_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("redirect_uri", LAUNCHER_REDIRECT_URI)
                .add("refresh_token", refreshToken)
                .add("scope", LAUNCHER_SCOPE)
                .build();
    }

    private static MicrosoftTokenResponse doRefresh(FormBody payload) throws IOException, AuthenticationException {
        Request request = new Request.Builder()
                .post(payload)
                .url(REFRESH_TOKENS_URL)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() >= 500) {
                throw new AuthenticationException("Microsoft services are unavailable");
            }

            String body = response.body() != null ? response.body().string() : null;
            JsonObject json = body != null ? GSON.fromJson(body, JsonObject.class) : null;

            if (json == null) {
                throw new AuthenticationException("Received no response when trying to refresh oauth tokens (code " + response.code() + ")");
            }

            MicrosoftTokenResponse microsoftResponse = MicrosoftTokenResponse.fromJson(json);

            if (!microsoftResponse.isSuccessful()) {
                throw new AuthenticationException("Received an error while refreshing oauth tokens: " + microsoftResponse.getError());
            }

            return microsoftResponse;
        }
    }

}
