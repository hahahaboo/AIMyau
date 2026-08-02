package myau.management.altmanager.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java port of CookieConvertor.py
 * <p>
 * 讀取 Netscape 格式（tab 分隔）的 cookie 匯出檔案，過濾出 login.live.com 的 cookie，
 * 依序完成 Xbox Live -> XSTS -> Minecraft 的登入流程，最終取得 Minecraft access token 與 profile。
 */
public class CookieAuthenticator {

    private static final String STEP1_URL =
            "https://sisu.xboxlive.com/connect/XboxLive/?state=login" +
                    "&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d" +
                    "&tid=896928775" +
                    "&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin" +
                    "&aid=1142970254";

    public static class CookieAuthResult {
        public final String accessToken;
        public final String uuid;
        public final String username;

        public CookieAuthResult(String accessToken, String uuid, String username) {
            this.accessToken = accessToken;
            this.uuid = uuid;
            this.username = username;
        }
    }

    /**
     * @param cookieFile 使用者選擇的 cookie 匯出 txt 檔
     * @return 登入結果 (access token / uuid / username)
     * @throws Exception 任一步驟失敗時拋出，訊息會顯示在 AltManager 狀態列
     */
    public static CookieAuthResult authenticate(File cookieFile) throws Exception {
        Map<String, String> cookieMap = parseCookieFile(cookieFile);
        if (cookieMap.isEmpty()) {
            throw new Exception("No valid login.live.com cookies found");
        }

        String cookieString = buildCookieString(cookieMap);
        String finalLocation = followRedirectChain(cookieString);

        String accessTokenParam = extractQueryParam(finalLocation, "accessToken=");
        if (accessTokenParam == null) {
            throw new Exception("Failed to extract access token from redirect");
        }

        String decodedToken = URLDecoder.decode(accessTokenParam, "UTF-8");
        decodedToken = fixBase64Padding(decodedToken);
        String decoded = new String(Base64.getDecoder().decode(decodedToken), StandardCharsets.UTF_8);

        String marker = "\"rp://api.minecraftservices.com/\",";
        int idx = decoded.indexOf(marker);
        if (idx == -1) {
            throw new Exception("Token parsing failed");
        }
        String rest = decoded.substring(idx + marker.length());

        String token = matchGroup("\"Token\":\"(.*?)\"", rest);
        String uhs = matchGroup("\"uhs\":\"(.*?)\"", rest);
        if (token == null || uhs == null) {
            throw new Exception("Failed to extract XBL token");
        }

        String xblToken = "XBL3.0 x=" + uhs + ";" + token;

        JsonObject mcResponse = postMinecraftLogin(xblToken);
        if (mcResponse == null || !mcResponse.has("access_token")) {
            throw new Exception("Failed to get Minecraft token");
        }
        String accessToken = mcResponse.get("access_token").getAsString();

        JsonObject profile = getMinecraftProfile(accessToken);
        if (profile == null || !profile.has("name") || !profile.has("id")) {
            throw new Exception("Failed to get profile");
        }

        return new CookieAuthResult(accessToken, profile.get("id").getAsString(), profile.get("name").getAsString());
    }

    private static Map<String, String> parseCookieFile(File file) throws IOException {
        Map<String, String> cookieMap = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\t");
                if (parts.length > 6 && parts[0].endsWith("login.live.com")) {
                    String name = parts[5].trim();
                    if (!cookieMap.containsKey(name)) {
                        cookieMap.put(name, parts[6].trim());
                    }
                }
            }
        }
        return cookieMap;
    }

    private static String buildCookieString(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private static String followRedirectChain(String cookieString) throws IOException {
        String location1 = getRedirectLocation(STEP1_URL, null);
        if (location1 == null) throw new IOException("Redirect failed at step 1");

        String location2 = getRedirectLocation(location1, cookieString);
        if (location2 == null) throw new IOException("Redirect failed at step 2");

        String location3 = getRedirectLocation(location2, cookieString);
        if (location3 == null) throw new IOException("Redirect failed at step 3");

        return location3;
    }

    private static String getRedirectLocation(String url, String cookieString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "*/*");
        if (cookieString != null) {
            connection.setRequestProperty("Cookie", cookieString);
        }
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();
        String location = connection.getHeaderField("Location");
        connection.disconnect();
        return location;
    }

    private static String extractQueryParam(String url, String keyWithEquals) {
        int idx = url.indexOf(keyWithEquals);
        if (idx == -1) return null;
        String value = url.substring(idx + keyWithEquals.length());
        int amp = value.indexOf('&');
        if (amp != -1) value = value.substring(0, amp);
        return value;
    }

    private static String fixBase64Padding(String b64) {
        int padding = (4 - (b64.length() % 4)) % 4;
        StringBuilder sb = new StringBuilder(b64);
        for (int i = 0; i < padding; i++) sb.append('=');
        return sb.toString();
    }

    private static String matchGroup(String regex, String content) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static JsonObject postMinecraftLogin(String xblToken) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", xblToken);
        payload.addProperty("ensureLegacyEnabled", true);

        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/authentication/login_with_xbox").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body);
        }

        int code = connection.getResponseCode();
        InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return null;
        String response = readStream(stream);
        connection.disconnect();
        if (code != 200) return null;
        return new JsonParser().parse(response).getAsJsonObject();
    }

    private static JsonObject getMinecraftProfile(String accessToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return null;
        String response = readStream(stream);
        connection.disconnect();
        if (code != 200) return null;
        return new JsonParser().parse(response).getAsJsonObject();
    }

    private static String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
