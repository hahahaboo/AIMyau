package myau.management.altmanager.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 讀取使用者匯出的 login.live.com cookie 檔案，走完整條 Microsoft -> Xbox Live -> XSTS -> Minecraft
 * 登入流程，最終取得 Minecraft access token 與 profile。
 * <p>
 * 邏輯移植自實戰驗證過的 CookieLoginUtil，比第一版更貼近真實瀏覽器行為
 * （正確的 Sec-Fetch-* / Referer 標頭、只在正確網域附帶 cookie、支援多種 cookie 檔格式）。
 */
public class CookieAuthenticator {

    static {
        // 部分較舊的 JVM 預設未啟用 TLS 1.2/1.3，會導致連線到 Microsoft 端點失敗
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    }

    private static final String SISU_URL =
            "https://sisu.xboxlive.com/connect/XboxLive/?state=login" +
                    "&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d" +
                    "&tid=896928775" +
                    "&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin" +
                    "&aid=1142970254";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"Token\":\"(.*?)\"");
    private static final Pattern UHS_PATTERN = Pattern.compile("\"uhs\":\"(.*?)\"");

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
     * @param cookieFile 使用者選擇的 cookie 檔（支援 Netscape tab 格式 / JSON 陣列 / raw cookie 字串）
     */
    public static CookieAuthResult authenticate(File cookieFile) throws Exception {
        String cookies = parseCookieContent(new String(Files.readAllBytes(cookieFile.toPath()), StandardCharsets.UTF_8));
        if (cookies.isEmpty()) {
            throw new IOException("No valid login.live.com cookies found in file");
        }
        return loginWithCookies(cookies);
    }

    private static CookieAuthResult loginWithCookies(String cookies) throws Exception {
        HttpsURLConnection connection = null;
        try {
            connection = openGet(SISU_URL, null, null);
            String location1 = requireLocation(connection, 1);
            closeQuietly(connection);

            connection = openGet(location1, cookiesFor(location1, cookies), SISU_URL);
            String location2 = requireLocation(connection, 2);
            closeQuietly(connection);

            connection = openGet(location2, cookiesFor(location2, cookies), location1);
            String location3 = requireLocation(connection, 3);
            closeQuietly(connection);

            String xbl = decodeXboxIdentityToken(extractAccessToken(location3));

            JsonObject mcResponse = postMinecraftLogin(xbl);
            if (mcResponse == null || !mcResponse.has("access_token")) {
                throw new IOException("[Step 4] No Minecraft access_token in response");
            }
            String mcToken = mcResponse.get("access_token").getAsString();

            JsonObject profile = getMinecraftProfile(mcToken);
            if (profile == null || !profile.has("id") || !profile.has("name")) {
                throw new IOException("[Step 5] Minecraft profile missing - account may not own Java Edition");
            }

            return new CookieAuthResult(mcToken, profile.get("id").getAsString(), profile.get("name").getAsString());
        } finally {
            closeQuietly(connection);
        }
    }

    // ---------------------------------------------------------------------
    // Cookie 檔案解析：支援 Netscape tab 格式 / JSON 陣列格式 / 單行 raw cookie 字串
    // ---------------------------------------------------------------------

    static String parseCookieContent(String content) {
        LinkedHashMap<String, String> cookies = new LinkedHashMap<>();
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) return "";

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (!parsed.isJsonArray()) return "";

            JsonArray array = parsed.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject cookie = element.getAsJsonObject();
                if (!cookie.has("domain") || !cookie.has("name") || !cookie.has("value")) continue;
                if (isLoginLiveDomain(cookie.get("domain").getAsString())) {
                    putCookie(cookies, cookie.get("name").getAsString(), cookie.get("value").getAsString());
                }
            }
            return formatCookies(cookies);
        }

        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\t", -1);
            if (parts.length >= 7) {
                if (isLoginLiveDomain(parts[0])) putCookie(cookies, parts[5], parts[6]);
            } else {
                addRawCookies(cookies, line);
            }
        }
        return formatCookies(cookies);
    }

    private static void addRawCookies(Map<String, String> cookies, String raw) {
        for (String part : raw.split("[;\\r\\n]+")) {
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            putCookie(cookies, part.substring(0, separator), part.substring(separator + 1));
        }
    }

    private static void putCookie(Map<String, String> cookies, String name, String value) {
        name = name.trim();
        value = value.trim();
        if (name.isEmpty() || value.isEmpty()) return;
        cookies.remove(name);
        cookies.put(name, value);
    }

    private static String formatCookies(Map<String, String> cookies) {
        StringJoiner joiner = new StringJoiner("; ");
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            joiner.add(cookie.getKey() + "=" + cookie.getValue());
        }
        return joiner.toString();
    }

    private static boolean isLoginLiveDomain(String domain) {
        domain = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        return domain.equals("login.live.com") || domain.endsWith(".login.live.com");
    }

    // ---------------------------------------------------------------------
    // Access token / Xbox token 解析
    // ---------------------------------------------------------------------

    static String extractAccessToken(String location) throws IOException {
        int start = location == null ? -1 : location.indexOf("accessToken=");
        if (start < 0) throw new IOException("[Step 3] No accessToken in redirect URL");

        String token = location.substring(start + "accessToken=".length());
        int end = token.indexOf('&');
        if (end >= 0) token = token.substring(0, end);
        token = URLDecoder.decode(token.replace("+", "%2B"), "UTF-8");
        while (token.length() % 4 != 0) token += "=";
        return token;
    }

    static String decodeXboxIdentityToken(String accessToken) throws IOException {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(accessToken), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("[Step 3] Invalid accessToken payload", e);
        }

        String marker = "\"rp://api.minecraftservices.com/\",";
        int markerIndex = decoded.indexOf(marker);
        if (markerIndex < 0) throw new IOException("[Step 3] Minecraft relying-party payload missing");
        String minecraftPayload = decoded.substring(markerIndex + marker.length());

        Matcher tokenMatcher = TOKEN_PATTERN.matcher(minecraftPayload);
        Matcher uhsMatcher = UHS_PATTERN.matcher(minecraftPayload);
        if (!tokenMatcher.find() || !uhsMatcher.find())
            throw new IOException("[Step 3] Xbox token or UHS missing");
        return "XBL3.0 x=" + uhsMatcher.group(1) + ";" + tokenMatcher.group(1);
    }

    // ---------------------------------------------------------------------
    // HTTP 請求（重導向鏈）
    // ---------------------------------------------------------------------

    private static String requireLocation(HttpsURLConnection connection, int step) throws IOException {
        int code = connection.getResponseCode();
        String location = connection.getHeaderField("Location");
        if (location == null) throw new IOException("[Step " + step + "] No redirect Location header (HTTP " + code + ")");
        return location.replace(" ", "%20");
    }

    private static HttpsURLConnection openGet(String url, String cookies, String referer) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", referer == null ? "none" : "cross-site");
        connection.setRequestProperty("Sec-Fetch-User", "?1");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        if (referer != null) connection.setRequestProperty("Referer", referer);
        if (cookies != null) connection.setRequestProperty("Cookie", cookies);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.connect();
        return connection;
    }

    /**
     * 只在目標網域確實是 Microsoft 驗證相關網域時才附上 cookie，
     * 避免把帳號 cookie 送到不相干的網域。
     */
    private static String cookiesFor(String url, String cookies) {
        return isMsAuthUrl(url) ? cookies : null;
    }

    private static boolean isMsAuthUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("live.com") || host.endsWith(".live.com")
                    || host.equals("microsoft.com") || host.endsWith(".microsoft.com")
                    || host.equals("xboxlive.com") || host.endsWith(".xboxlive.com")
                    || host.equals("xbox.com") || host.endsWith(".xbox.com")
                    || host.equals("microsoftonline.com") || host.endsWith(".microsoftonline.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void closeQuietly(HttpsURLConnection connection) {
        if (connection != null) connection.disconnect();
    }

    // ---------------------------------------------------------------------
    // Minecraft 服務登入 / 取得 profile（沿用 Gson，避免額外依賴）
    // ---------------------------------------------------------------------

    private static JsonObject postMinecraftLogin(String xblToken) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", xblToken);
        payload.addProperty("ensureLegacyEnabled", true);

        HttpsURLConnection connection = (HttpsURLConnection) URI.create(
                "https://api.minecraftservices.com/authentication/login_with_xbox").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
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
        if (code != 200) throw new IOException("[Step 4] Minecraft login HTTP " + code + ": " + snippet(response));
        return JsonParser.parseString(response).getAsJsonObject();
    }

    private static JsonObject getMinecraftProfile(String accessToken) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(
                "https://api.minecraftservices.com/minecraft/profile").toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return null;
        String response = readStream(stream);
        connection.disconnect();
        if (code != 200) throw new IOException("[Step 5] Minecraft profile HTTP " + code + ": " + snippet(response));
        return JsonParser.parseString(response).getAsJsonObject();
    }

    private static String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String trimmed = body.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }
}
