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
public class XboxLiveTokenResponse {

    private String token;
    private String userHash;

    public static XboxLiveTokenResponse fromJson(JsonObject json) {
        XboxLiveTokenResponse xboxLiveResponse = new XboxLiveTokenResponse();

        if (!json.has("Token") || !json.has("DisplayClaims")) {
            throw new AuthenticationException("Missing Token or DisplayClaims when trying to get Xbox live token");
        }

        xboxLiveResponse.setToken(json.get("Token").getAsString());
        xboxLiveResponse.setUserHash(
                json.get("DisplayClaims").getAsJsonObject()
                        .get("xui").getAsJsonArray()
                        .get(0).getAsJsonObject()
                        .get("uhs").getAsString()
        );

        return xboxLiveResponse;
    }

}
