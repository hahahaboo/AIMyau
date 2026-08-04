package myau.management.altmanager.auth.refresh.request;

import com.google.gson.JsonObject;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.EntitlementsResponse;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.CLIENT;
import static myau.management.altmanager.auth.refresh.RefreshTokenAuthentication.GSON;

public final class EntitlementsRequest {

    private static final String MINECRAFTSERVICES_PRODUCTS_URL = "https://api.minecraftservices.com/entitlements/license?requestId=auth";

    public static EntitlementsResponse getEntitlements(String accessToken) throws IOException, AuthenticationException {
        Request request = new Request.Builder()
                .url(MINECRAFTSERVICES_PRODUCTS_URL)
                .header("Authorization", String.format("Bearer %s", accessToken))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() >= 500) {
                throw new AuthenticationException("Minecraft services are unavailable");
            }

            if (response.code() != 200) {
                throw new AuthenticationException("Received code " + response.code() + " when trying to check game ownership");
            }

            return EntitlementsResponse.fromJson(GSON.fromJson(response.body().string(), JsonObject.class));
        }
    }

}
