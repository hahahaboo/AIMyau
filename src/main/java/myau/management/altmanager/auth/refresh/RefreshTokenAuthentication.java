package myau.management.altmanager.auth.refresh;

import com.google.gson.Gson;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.EntitlementsResponse;
import myau.management.altmanager.auth.refresh.model.MicrosoftTokenResponse;
import myau.management.altmanager.auth.refresh.model.MinecraftProfileResponse;
import myau.management.altmanager.auth.refresh.model.MinecraftTokenResponse;
import myau.management.altmanager.auth.refresh.model.XboxLiveTokenResponse;
import myau.management.altmanager.auth.refresh.request.EntitlementsRequest;
import myau.management.altmanager.auth.refresh.request.MinecraftProfileRequest;
import myau.management.altmanager.auth.refresh.request.MinecraftTokenRequest;
import myau.management.altmanager.auth.refresh.request.RefreshTokenRequest;
import myau.management.altmanager.auth.refresh.request.XboxLiveTokenRequest;
import myau.management.altmanager.auth.refresh.request.XstsTokenRequest;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import java.io.IOException;

public class RefreshTokenAuthentication {

    public static final Gson GSON = new Gson();
    public static final OkHttpClient CLIENT = new OkHttpClient()
            .newBuilder()
            .followSslRedirects(false)
            .followRedirects(false)
            .addInterceptor(chain -> {
                Response response = chain.proceed(chain.request());

                if (response.code() == 429) {
                    response.close();
                    throw new AuthenticationException("You are rate limited, try again in a moment!");
                }

                return response;
            })
            .build();

    /**
     * Result of a full refresh-token login, including the (possibly rotated) refresh token.
     */
    public static final class AuthResult {
        private final MinecraftTokenResponse minecraftToken;
        private final String refreshToken;

        public AuthResult(MinecraftTokenResponse minecraftToken, String refreshToken) {
            this.minecraftToken = minecraftToken;
            this.refreshToken = refreshToken;
        }

        public MinecraftTokenResponse getMinecraftToken() {
            return minecraftToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getAccessToken() {
            return minecraftToken.getAccessToken();
        }
    }

    /* OAuth refresh -> OAuth access -> XBL -> XSTS -> MC */
    public static MinecraftTokenResponse authenticateWithRefreshToken(String refreshToken) throws IOException, AuthenticationException {
        return authenticateWithRefreshTokenFull(refreshToken).getMinecraftToken();
    }

    /**
     * Full login that also returns the refreshed (possibly new) refresh token so callers can persist it.
     */
    public static AuthResult authenticateWithRefreshTokenFull(String refreshToken) throws IOException, AuthenticationException {
        MicrosoftTokenResponse msToken = RefreshTokenRequest.refreshToken(refreshToken);

        XboxLiveTokenResponse xboxLiveResponse = XboxLiveTokenRequest.getXboxLiveToken(msToken.getAccessToken());

        MinecraftTokenResponse mcToken = MinecraftTokenRequest.getMinecraftAccessToken(
                XstsTokenRequest.getXstsToken(xboxLiveResponse.getToken()).getToken(),
                xboxLiveResponse.getUserHash()
        );

        // Prefer rotated refresh token from Microsoft when present
        String storedRefresh = msToken.getRefreshToken() != null && !msToken.getRefreshToken().isEmpty()
                ? msToken.getRefreshToken()
                : refreshToken;

        return new AuthResult(mcToken, storedRefresh);
    }

    /* MC -> Check game ownership -> Fetch profile */
    public static MinecraftProfileResponse getMinecraftProfile(MinecraftTokenResponse minecraftTokenResponse) throws IOException, AuthenticationException {
        EntitlementsResponse entitlements = EntitlementsRequest.getEntitlements(minecraftTokenResponse.getAccessToken());

        if (!entitlements.checkOwnership()) {
            throw new AuthenticationException("Account doesn't own Minecraft!");
        }

        return MinecraftProfileRequest.getMinecraftProfile(minecraftTokenResponse.getAccessToken());
    }

}
