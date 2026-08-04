package myau.management.altmanager.auth.refresh.model;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class XstsTokenResponse {

    private static final Map<Long, String> ERRORS = new HashMap<>();
    static {
        ERRORS.put(2148916227L, "The account is banned from Xbox");
        ERRORS.put(2148916233L, "The account doesn't have an Xbox account (never signed in)");
        ERRORS.put(2148916235L, "The account is from a country where Xbox Live is not available/banned");
        ERRORS.put(2148916236L, "The account needs adult verification on Xbox page. (South Korea)");
        ERRORS.put(2148916237L, "The account needs adult verification on Xbox page. (South Korea)");
        /* won't ever happen anyway as we're using mc launcher client id */
        ERRORS.put(2148916238L, "The account is a child (under 18) and cannot proceed unless the account is added to a Family by an adult");
        ERRORS.put(2148916262L, "Unknown error");
    }

    private String token;
    private Long errorCode;
    private String error;

    public static XstsTokenResponse fromJson(JsonObject json) {
        XstsTokenResponse response = new XstsTokenResponse();

        if (json.has("XErr")) {
            response.setErrorCode(json.get("XErr").getAsLong());
            String message = ERRORS.get(response.getErrorCode());
            response.setError(message != null ? message : "Unknown error");
        } else if (json.has("Token")) {
            response.setToken(json.get("Token").getAsString());
        } else {
            throw new AuthenticationException("XSTS token not found");
        }

        return response;
    }

    public boolean isSuccessful() {
        return error == null;
    }

}
