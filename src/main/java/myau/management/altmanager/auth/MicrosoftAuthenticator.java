package myau.management.altmanager.auth;

import myau.management.altmanager.auth.model.request.MinecraftLoginRequest;
import myau.management.altmanager.auth.model.request.XSTSAuthorizationProperties;
import myau.management.altmanager.auth.model.request.XboxLiveLoginProperties;
import myau.management.altmanager.auth.model.request.XboxLoginRequest;
import myau.management.altmanager.auth.model.response.*;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MicrosoftAuthenticator {
    public static final String MICROSOFT_AUTHORIZATION_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    public static final String MICROSOFT_TOKEN_ENDPOINT = "https://login.live.com/oauth20_token.srf";
    public static final String MICROSOFT_REDIRECTION_ENDPOINT = "https://login.live.com/oauth20_desktop.srf";

    public static final String XBOX_LIVE_AUTH_HOST = "user.auth.xboxlive.com";
    public static final String XBOX_LIVE_CLIENT_ID = "000000004C12AE6F";
    public static final String XBOX_LIVE_SERVICE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    public static final String XBOX_LIVE_AUTHORIZATION_ENDPOINT = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_AUTHORIZATION_ENDPOINT = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MINECRAFT_AUTH_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static final String XBOX_LIVE_AUTH_RELAY = "https://auth.xboxlive.com";
    public static final String MINECRAFT_AUTH_RELAY = "rp://api.minecraftservices.com/";

    public static final String MINECRAFT_STORE_ENDPOINT = "https://api.minecraftservices.com/entitlements/mcstore";
    public static final String MINECRAFT_PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";

    public static final String MINECRAFT_STORE_IDENTIFIER = "game_minecraft";

    // 自己的 OAuth Client（與 MicrosoftOAuthTranslation / Tenacity 相同）
    public static final String OWN_CLIENT_ID = "9fbc7315-7200-4b2b-a655-bb38c865da17";
    public static final String OWN_CLIENT_SECRET = "Bzn8Q~YryydJsydgnnxHgJq.NM3Oo4.AEEohLbBb";
    public static final String OWN_REDIRECT_URI = "http://localhost:8247";
    public static final String OWN_SCOPE = "XboxLive.signin offline_access";

    // Vanilla Minecraft Launcher Client ID
    public static final String VANILLA_CLIENT_ID = "00000000402b5328";
    public static final String VANILLA_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    private final HttpClient http;

    public MicrosoftAuthenticator() {
        this.http = new HttpClient();
    }

    public MicrosoftAuthResult loginWithCredentials(String email, String password) throws MicrosoftAuthenticationException {
        CookieHandler currentHandler = CookieHandler.getDefault();
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

        Map<String, String> params = new HashMap<>();
        params.put("login", email);
        params.put("loginfmt", email);
        params.put("passwd", password);

        HttpURLConnection result;

        try {
            PreAuthData authData = preAuthRequest();
            params.put("PPFT", authData.getPPFT());

            result = http.followRedirects(http.postForm(authData.getUrlPost(), params));
        } finally {
            CookieHandler.setDefault(currentHandler);
        }

        try {
            // credentials 流程使用 Xbox Live client，不需要 d= 前綴
            return loginWithTokens(extractTokens(result.getURL().toString()), true, false);
        } catch (MicrosoftAuthenticationException e) {
            if (match("(identity/confirm)", http.readResponse(result)) != null) {
                throw new MicrosoftAuthenticationException(
                        "User has enabled double-authentication or must allow sign-in on https://account.live.com/activity"
                );
            }

            throw e;
        }
    }

    /**
     * Logs in a player using a Microsoft account refresh token.
     * 依序嘗試：自己的 Client ID → Vanilla
     * （參考 Tenacity MicrosoftLogin 的做法）
     */
    public MicrosoftAuthResult loginWithRefreshToken(String refreshToken) throws MicrosoftAuthenticationException {
        // 格式：name, clientId, clientSecret(可null), redirectUri, scope(可null), useDPrefix
        Object[][] attempts = {
                { "Own",     OWN_CLIENT_ID,     OWN_CLIENT_SECRET, OWN_REDIRECT_URI,              OWN_SCOPE,     true  },
                { "Vanilla", VANILLA_CLIENT_ID, null,              MICROSOFT_REDIRECTION_ENDPOINT, VANILLA_SCOPE, false }
        };

        MicrosoftAuthenticationException lastException = null;

        for (Object[] attempt : attempts) {
            String name         = (String)  attempt[0];
            String clientId     = (String)  attempt[1];
            String clientSecret = (String)  attempt[2];
            String redirectUri  = (String)  attempt[3];
            String scope        = (String)  attempt[4];
            boolean useDPrefix  = (Boolean) attempt[5];

            try {
                System.out.println("[TokenLogin] Trying client: " + name + " (" + clientId + ")");

                Map<String, String> params = new HashMap<>();
                params.put("client_id", clientId);
                params.put("refresh_token", refreshToken);
                params.put("grant_type", "refresh_token");
                params.put("redirect_uri", redirectUri);

                if (clientSecret != null) {
                    params.put("client_secret", clientSecret);
                }
                if (scope != null) {
                    params.put("scope", scope);
                }

                MicrosoftRefreshResponse response = http.postFormGetJson(
                        MICROSOFT_TOKEN_ENDPOINT,
                        params,
                        MicrosoftRefreshResponse.class
                );

                if (response != null && response.getAccessToken() != null) {
                    System.out.println("[TokenLogin] Success with client: " + name);
                    String newRefresh = response.getRefreshToken() != null
                            ? response.getRefreshToken()
                            : refreshToken;
                    return loginWithTokens(
                            new AuthTokens(response.getAccessToken(), newRefresh),
                            true,
                            useDPrefix
                    );
                }
            } catch (MicrosoftAuthenticationException e) {
                System.out.println("[TokenLogin] Failed with client " + name + ": " + e.getMessage());
                lastException = e;
            } catch (Exception e) {
                System.out.println("[TokenLogin] Unexpected error with client " + name + ": " + e.getMessage());
                lastException = new MicrosoftAuthenticationException(e);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new MicrosoftAuthenticationException("All client_id attempts failed for refresh token");
    }

    /**
     * Logs in a player using a Microsoft account tokens retrieved earlier.
     * <b>If the token was retrieved using Azure AAD/MSAL / own OAuth client, useDPrefix should be true (adds d=).</b>
     */
    public MicrosoftAuthResult loginWithTokens(AuthTokens tokens, boolean retrieveProfile) throws MicrosoftAuthenticationException {
        // 預設不使用 d= 前綴（相容舊呼叫）
        return loginWithTokens(tokens, retrieveProfile, false);
    }

    public MicrosoftAuthResult loginWithTokens(AuthTokens tokens, boolean retrieveProfile, boolean useDPrefix) throws MicrosoftAuthenticationException {
        XboxLoginResponse xboxLiveResponse = xboxLiveLogin(tokens.getAccessToken(), useDPrefix);
        XboxLoginResponse xstsResponse = xstsLogin(xboxLiveResponse.getToken());

        String userHash = xstsResponse.getDisplayClaims().getUsers()[0].getUserHash();
        MinecraftLoginResponse minecraftResponse = minecraftLogin(userHash, xstsResponse.getToken());
        MinecraftStoreResponse storeResponse = http.getJson(
                MINECRAFT_STORE_ENDPOINT,
                minecraftResponse.getAccessToken(),
                MinecraftStoreResponse.class
        );

        if (Arrays.stream(storeResponse.getItems()).noneMatch(item -> item.getName().equals(MINECRAFT_STORE_IDENTIFIER))) {
            throw new MicrosoftAuthenticationException("Player didn't buy Minecraft Java Edition or did not migrate its account");
        }
        MinecraftProfile profile = null;
        if (retrieveProfile) {
            profile = http.getJson(
                    MINECRAFT_PROFILE_ENDPOINT,
                    minecraftResponse.getAccessToken(),
                    MinecraftProfile.class
            );
        }

        return new MicrosoftAuthResult(
                profile,
                minecraftResponse.getAccessToken(),
                tokens.getRefreshToken(),
                xboxLiveResponse.getDisplayClaims().getUsers()[0].getUserHash(),
                Base64.getEncoder().encodeToString(minecraftResponse.getUsername().getBytes())
        );
    }


    protected PreAuthData preAuthRequest() throws MicrosoftAuthenticationException {
        Map<String, String> params = getLoginParams();
        params.put("display", "touch");
        params.put("locale", "en");

        String result = http.getText(MICROSOFT_AUTHORIZATION_ENDPOINT, params);

        String ppft = match("sFTTag:'.*value=\"([^\"]*)\"", result);
        String urlPost = match("urlPost: ?'(.+?(?='))", result);

        return new PreAuthData(ppft, urlPost);
    }

    protected XboxLoginResponse xboxLiveLogin(String accessToken) throws MicrosoftAuthenticationException {
        return xboxLiveLogin(accessToken, false);
    }

    protected XboxLoginResponse xboxLiveLogin(String accessToken, boolean useDPrefix) throws MicrosoftAuthenticationException {
        // 自己的 OAuth Client 取得的 token 需要加 d= 前綴（Tenacity 做法）
        String rpsTicket = useDPrefix ? ("d=" + accessToken) : accessToken;

        XboxLiveLoginProperties properties = new XboxLiveLoginProperties("RPS", XBOX_LIVE_AUTH_HOST, rpsTicket);
        XboxLoginRequest<XboxLiveLoginProperties> request = new XboxLoginRequest<>(
                properties, XBOX_LIVE_AUTH_RELAY, "JWT"
        );

        return http.postJson(XBOX_LIVE_AUTHORIZATION_ENDPOINT, request, XboxLoginResponse.class);
    }

    protected XboxLoginResponse xstsLogin(String xboxLiveToken) throws MicrosoftAuthenticationException {
        XSTSAuthorizationProperties properties = new XSTSAuthorizationProperties("RETAIL", new String[]{xboxLiveToken});
        XboxLoginRequest<XSTSAuthorizationProperties> request = new XboxLoginRequest<>(
                properties, MINECRAFT_AUTH_RELAY, "JWT"
        );

        return http.postJson(XSTS_AUTHORIZATION_ENDPOINT, request, XboxLoginResponse.class);
    }

    protected MinecraftLoginResponse minecraftLogin(String userHash, String xstsToken) throws MicrosoftAuthenticationException {
        MinecraftLoginRequest request = new MinecraftLoginRequest(String.format("XBL3.0 x=%s;%s", userHash, xstsToken));
        return http.postJson(MINECRAFT_AUTH_ENDPOINT, request, MinecraftLoginResponse.class);
    }


    protected Map<String, String> getLoginParams() {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", XBOX_LIVE_CLIENT_ID);
        params.put("redirect_uri", MICROSOFT_REDIRECTION_ENDPOINT);
        params.put("scope", XBOX_LIVE_SERVICE_SCOPE);
        params.put("response_type", "token");

        return params;
    }

    protected AuthTokens extractTokens(String url) throws MicrosoftAuthenticationException {
        return new AuthTokens(extractValue(url, "access_token"), extractValue(url, "refresh_token"));
    }

    protected String extractValue(String url, String key) throws MicrosoftAuthenticationException {
        String matched = match(key + "=([^&]*)", url);
        if (matched == null) {
            throw new MicrosoftAuthenticationException("Invalid credentials or tokens");
        }

        try {
            return URLDecoder.decode(matched, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MicrosoftAuthenticationException(e);
        }
    }

    protected String match(String regex, String content) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }
}
