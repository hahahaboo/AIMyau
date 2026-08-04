package myau.management.altmanager;

import myau.management.altmanager.auth.MicrosoftAuthResult;
import myau.management.altmanager.auth.MicrosoftAuthenticationException;
import myau.management.altmanager.auth.MicrosoftAuthenticator;
import myau.management.altmanager.auth.refresh.RefreshTokenAuthentication;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.MinecraftProfileResponse;
import myau.management.altmanager.microsoft.MicrosoftOAuthTranslation;
import myau.management.altmanager.util.AltJsonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

public class SessionChanger {
    public static String username = null;
    private static SessionChanger instance;
    private final Minecraft mc = Minecraft.getMinecraft();
    public long timeSinceFail;
    private MicrosoftAuthenticator auth;

    public static SessionChanger instance() {
        if (instance == null) {
            instance = new SessionChanger();
        }

        return instance;
    }

    public void loginCracked(String n) {
        SessionUtil.setSession(mc, new Session(n, n, "0", "legacy"));
        username = n;
    }

    public void loginMicrosoft(String email, String password) {
        new Thread(() -> {
            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            AltManagerGui.status = "§6Logging in";
            try {
                MicrosoftAuthResult acc = authenticator.loginWithCredentials(email, password);
                if (acc != null) {
                    SessionUtil.setSession(
                            Minecraft.getMinecraft(),
                            new Session(
                                    acc.getProfile().getName(),
                                    acc.getProfile().getId(),
                                    acc.getAccessToken(),
                                    "mojang"
                            )
                    );
                    username = acc.getProfile().getName();
                    System.out.println("Login successful");
                } else {
                    System.out.println("Failed login");
                    timeSinceFail = System.currentTimeMillis();
                }
                AltManagerGui.status = "§aIdle";
            } catch (MicrosoftAuthenticationException e) {
                e.printStackTrace();
                System.out.println("Failed login");
                timeSinceFail = System.currentTimeMillis();
            }
        }).start();
    }

    /**
     * Login using a stored refresh token.
     * Uses the same RefreshTokenAuthentication path as Token Login GUI:
     * custom client first, then launcher client fallback.
     */
    public void loginWithRefreshToken(String refreshToken) {
        new Thread(() -> {
            AltManagerGui.status = "§6Logging in with refresh token...";
            try {
                RefreshTokenAuthentication.AuthResult authResult =
                        RefreshTokenAuthentication.authenticateWithRefreshTokenFull(refreshToken);
                MinecraftProfileResponse profile =
                        RefreshTokenAuthentication.getMinecraftProfile(authResult.getMinecraftToken());

                String name = profile.getUsername();
                String uuid = profile.getUuid().toString();
                String accessToken = authResult.getAccessToken();
                String newRefreshToken = authResult.getRefreshToken();

                SessionUtil.setSession(mc, new Session(name, uuid, accessToken, "mojang"));
                username = name;

                // Persist rotated refresh token on the matching alt
                updateAltRefreshToken(name, uuid, newRefreshToken);

                AltManagerGui.status = "§aLogged in as " + name;
                System.out.println("Refresh token login successful: " + name);
            } catch (AuthenticationException e) {
                e.printStackTrace();
                timeSinceFail = System.currentTimeMillis();
                AltManagerGui.status = "§c" + e.getMessage();
            } catch (Exception e) {
                e.printStackTrace();
                timeSinceFail = System.currentTimeMillis();
                AltManagerGui.status = "§cRefresh token login failed";
            }
        }).start();
    }

    private void updateAltRefreshToken(String name, String uuid, String refreshToken) {
        try {
            Alt existing = null;
            for (Alt alt : AltManagerGui.alts) {
                if (alt.getName().equals(name) || (uuid != null && uuid.equals(alt.getUuid()))) {
                    existing = alt;
                    break;
                }
            }
            if (existing != null) {
                existing.setUuid(uuid);
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    existing.setRefreshToken(refreshToken);
                }
                AltJsonHandler.saveAlts();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setSessionWithData(MicrosoftOAuthTranslation.LoginData loginData) {
        SessionUtil.setSession(mc, new Session(loginData.username, loginData.uuid, loginData.mcToken, "mojang"));
        username = loginData.username;
        System.out.println("OAuth login successful: " + loginData.username);
    }

    public String getUser(String email, String password) {
        MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
        try {
            MicrosoftAuthResult acc = authenticator.loginWithCredentials(email, password);
            return acc.getProfile().getName();
        } catch (MicrosoftAuthenticationException e) {

        }
        return "";
    }
}
