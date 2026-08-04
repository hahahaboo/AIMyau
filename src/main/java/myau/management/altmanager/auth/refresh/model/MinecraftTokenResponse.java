package myau.management.altmanager.auth.refresh.model;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;

@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftTokenResponse {

    private String accessToken;
    private Long expiresIn;

    private String error;

    public static MinecraftTokenResponse fromJson(JsonObject json) {
        MinecraftTokenResponse response = new MinecraftTokenResponse();

        if (json.has("path")) {
            if (json.has("error")) {
                response.setError(json.get("error").getAsString());
            } else if (json.has("details") && json.get("details").getAsJsonObject().has("reason")) {
                response.setError(json.get("details").getAsJsonObject().get("reason").getAsString());
            } else {
                response.setError("You're being rate limited, try again in a moment!");
            }
        } else if (json.has("access_token") && json.has("expires_in")) {
            response.setAccessToken(json.get("access_token").getAsString());
            response.setExpiresIn(json.get("expires_in").getAsLong());
        } else {
            throw new AuthenticationException("No Minecraft access token found!");
        }

        return response;
    }

    public boolean isSuccessful() {
        return error == null;
    }

}
