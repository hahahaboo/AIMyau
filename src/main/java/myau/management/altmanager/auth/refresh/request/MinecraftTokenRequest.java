package myau.management.altmanager.auth.refresh.request;

import com.google.gson.JsonObject;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.MinecraftTokenResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.CLIENT;
import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.GSON;

public final class MinecraftTokenRequest {

    private static final String LOGIN_WITH_XBOX_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static MinecraftTokenResponse getMinecraftAccessToken(String xstsToken, String userHash) throws IOException, AuthenticationException {
        JsonObject payload = new JsonObject();

        payload.addProperty("identityToken", String.format("XBL3.0 x=%s;%s", userHash, xstsToken));

        Request request = new Request.Builder()
                .post(RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8")))
                .url(LOGIN_WITH_XBOX_URL)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() >= 500) {
                throw new AuthenticationException("Xbox services are unavailable (login_with_xbox)");
            }

            MinecraftTokenResponse minecraftResponse = MinecraftTokenResponse.fromJson(GSON.fromJson(response.body().string(), JsonObject.class));

            if (!minecraftResponse.isSuccessful()) {
                throw new AuthenticationException("Received an error while trying to get Minecraft access token: " + minecraftResponse.getError());
            }

            return minecraftResponse;
        }
    }

}
