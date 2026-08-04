package myau.management.altmanager.auth.refresh.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.XstsTokenResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.CLIENT;
import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.GSON;

public final class XstsTokenRequest {

    private static final String GET_XSTS_TOKEN_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static XstsTokenResponse getXstsToken(String accessToken) throws IOException, AuthenticationException {
        JsonObject payload = new JsonObject();
        JsonObject properties = new JsonObject();
        JsonArray userTokens = new JsonArray();

        properties.addProperty("SandboxId", "RETAIL");
        userTokens.add(accessToken);
        properties.add("UserTokens", userTokens);

        payload.add("Properties", properties);
        payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        payload.addProperty("TokenType", "JWT");

        Request request = new Request.Builder()
                .post(RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8")))
                .url(GET_XSTS_TOKEN_URL)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() >= 500) {
                throw new AuthenticationException("Xbox services are unavailable (XSTS)");
            }

            XstsTokenResponse xstsResponse = XstsTokenResponse.fromJson(GSON.fromJson(response.body().string(), JsonObject.class));

            if (!xstsResponse.isSuccessful()) {
                throw new AuthenticationException("Received an error while getting XSTS Token: " + xstsResponse.getError());
            }

            return xstsResponse;
        }
    }

}
