package myau.management.altmanager.auth.refresh.request;

import com.google.gson.JsonObject;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.XboxLiveTokenResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.CLIENT;
import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.GSON;

public final class XboxLiveTokenRequest {

    private static final String GET_XBL_TOKEN_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XBL_RP = "http://auth.xboxlive.com";

    public static XboxLiveTokenResponse getXboxLiveToken(String accessToken) throws IOException, AuthenticationException {
        JsonObject payload = new JsonObject();
        JsonObject properties = new JsonObject();

        payload.add("Properties", properties);

        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", String.format("t=%s", accessToken));

        payload.addProperty("RelyingParty", XBL_RP);
        payload.addProperty("TokenType", "JWT");

        Request request = new Request.Builder()
                .post(RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8")))
                .url(GET_XBL_TOKEN_URL)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() >= 500) {
                throw new AuthenticationException("Xbox services are unavailable (XBL)");
            }

            if (response.code() == 401) {
                throw new AuthenticationException("OAuth access token is invalid");
            }

            return XboxLiveTokenResponse.fromJson(GSON.fromJson(response.body().string(), JsonObject.class));
        }
    }

}
