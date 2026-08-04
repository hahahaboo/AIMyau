package myau.management.altmanager.auth.refresh;

import com.google.gson.Gson;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.EntitlementsResponse;
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

    /* OAuth refresh -> OAuth access -> XBL -> XSTS -> MC */
    public static MinecraftTokenResponse authenticateWithRefreshToken(String refreshToken) throws IOException, AuthenticationException {
        XboxLiveTokenResponse xboxLiveResponse = XboxLiveTokenRequest.getXboxLiveToken(
                RefreshTokenRequest.refreshToken(refreshToken).getAccessToken()
        );

        return MinecraftTokenRequest.getMinecraftAccessToken(
                XstsTokenRequest.getXstsToken(xboxLiveResponse.getToken()).getToken(),
                xboxLiveResponse.getUserHash()
        );
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
