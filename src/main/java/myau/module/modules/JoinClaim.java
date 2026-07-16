package myau.module.modules;

import com.google.gson.JsonObject;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.util.ThreadUtil;                    // ← 已改成 ThreadUtil
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class JoinClaim extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public JoinClaim() {
        super("JoinClaim", "Prevents others from logging in to the server you're on.", Category.MISC, 0, false, false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;

        if (mc.thePlayer == null || mc.theWorld == null || mc.isSingleplayer())
            return;

        if (mc.thePlayer.ticksExisted > 20 && mc.thePlayer.ticksExisted % 40 == 0) {
            final String accessToken = mc.getSession().getToken();
            final String selectedProfile = mc.getSession().getPlayerID().replace("-", "");
            final String serverId = "114514";

            if (accessToken == null || selectedProfile == null || serverId == null)
                return;

            ThreadUtil.runAsync(() -> joinSession(accessToken, selectedProfile, serverId));  // ← 使用 ThreadUtil
        }
    }

    private static void joinSession(String accessToken, String selectedProfile, String serverId) {
        HttpURLConnection connection = null;
        try {
            connection = makeJoinConnection();

            JsonObject body = new JsonObject();
            body.addProperty("accessToken", accessToken);
            body.addProperty("selectedProfile", selectedProfile);
            body.addProperty("serverId", serverId);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                return;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection makeJoinConnection() throws IOException {
        java.net.URL url = java.net.URI.create("https://sessionserver.mojang.com/session/minecraft/join").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        return conn;
    }
}
