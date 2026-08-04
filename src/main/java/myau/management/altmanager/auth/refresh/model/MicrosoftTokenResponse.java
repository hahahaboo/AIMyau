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
public class MicrosoftTokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;

    private String error, description;

    public static MicrosoftTokenResponse fromJson(JsonObject json) {
        MicrosoftTokenResponse response = new MicrosoftTokenResponse();

        if (json.has("error")) {
            response.setError(json.get("error").getAsString());

            if (json.has("description")) {
                response.setDescription(json.get("description").getAsString());
            }
        } else if (json.has("access_token") && json.has("refresh_token") && json.has("expires_in")) {
            response.setAccessToken(json.get("access_token").getAsString());
            response.setRefreshToken(json.get("refresh_token").getAsString());
            response.setExpiresIn(json.get("expires_in").getAsLong());
        } else {
            throw new AuthenticationException("Received invalid JSON object while trying to refresh oauth tokens");
        }

        return response;
    }

    public String getError() {
        if (description != null) {
            return String.format("%s (%s)", error, description);
        }

        return error;
    }

    public boolean isSuccessful() {
        return error == null;
    }

}
