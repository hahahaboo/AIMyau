package myau.management.altmanager.auth;

import com.google.gson.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cookie login for Microsoft accounts.
 * <p>
 * Reads browser-exported cookies (Netscape / JSON / raw), obtains a Microsoft
 * access token via OAuth redirects, then completes the standard
 * Xbox Live -> XSTS -> Minecraft authentication chain.
 * <p>
 * Flow inspired by the robust cookie auth used in AccountManager forks
 * (abusez/Lumiere lineage), adapted to AIMyau's structure and HttpsURLConnection.
 */
public class CookieAuthenticator {

    static {
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Minecraft / Xbox Live OAuth (sisu) */
    private static final String OAUTH_URL_SISU =
            "https://login.live.com/oauth20_authorize.srf"
                    + "?redirect_uri=https://sisu.xboxlive.com/connect/oauth/XboxLive"
                    + "&response_type=token"
                    + "&client_id=000000004420578E"
                    + "&scope=XboxLive.Signin%20XboxLive.offline_access"
                    + "&prompt=none";

    /** Classic desktop OAuth used by many 1.8.9 account managers */
    private static final String OAUTH_URL_DESKTOP =
            "https://login.live.com/oauth20_authorize.srf"
                    + "?client_id=00000000402b5328"
                    + "&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf"
                    + "&response_type=token"
                    + "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"
                    + "&prompt=none";

    private static final List<String> COOKIE_ORDER_JSHP = Arrays.asList(
            "__Host-MSAAUTH", "__Host-MSAAUTHP",
            "JSHP", "JSH",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK",
            "MSPShared", "MSPPre", "MSPCID", "MSPOAuthVis",
            "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt"
    );

    private static final List<String> COOKIE_ORDER_JSH = Arrays.asList(
            "__Host-MSAAUTH", "__Host-MSAAUTHP",
            "JSH", "JSHP",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK",
            "MSPShared", "MSPPre", "MSPCID", "MSPOAuthVis",
            "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt"
    );

    private static final Set<String> REQUIRED_AUTH_COOKIE_NAMES = new HashSet<String>(Arrays.asList(
            "__Host-MSAAUTH", "__Host-MSAAUTHP", "MSPAuth", "JSH", "JSHP"
    ));

    // -------------------------------------------------------------------------
    // Public API (unchanged for AltManagerGui)
    // -------------------------------------------------------------------------

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
     * @param cookieFile Netscape .txt / JSON array / raw cookie string
     */
    public static CookieAuthResult authenticate(File cookieFile) throws Exception {
        String content = new String(Files.readAllBytes(cookieFile.toPath()), StandardCharsets.UTF_8);
        Map<String, String> cookieMap = parseCookieContent(content);
        if (cookieMap.isEmpty()) {
            throw new IOException("No valid Microsoft cookies found in file");
        }
        if (!hasRequiredAuthCookies(cookieMap)) {
            throw new IOException("Missing auth cookies (need __Host-MSAAUTH, MSPAuth, JSH or JSHP)");
        }
        return loginWithCookieMap(cookieMap);
    }

    // -------------------------------------------------------------------------
    // Main login pipeline
    // -------------------------------------------------------------------------

    private static CookieAuthResult loginWithCookieMap(Map<String, String> cookieMap) throws Exception {
        Exception lastError = null;

        String[] oauthUrls = {OAUTH_URL_SISU, OAUTH_URL_DESKTOP};
        List<List<String>> orderings = Arrays.asList(COOKIE_ORDER_JSHP, COOKIE_ORDER_JSH);

        for (String oauthUrl : oauthUrls) {
            for (List<String> ordering : orderings) {
                try {
                    String msAccessToken = followOAuthForAccessToken(oauthUrl, cookieMap, ordering);
                    if (msAccessToken == null || msAccessToken.isEmpty()) {
                        continue;
                    }
                    return finishWithMicrosoftToken(msAccessToken);
                } catch (Exception e) {
                    lastError = e;
                    System.err.println("[CookieAuth] OAuth path failed (" + shortUrl(oauthUrl) + "): " + e.getMessage());
                }
            }
        }

        // Last-resort: old SISU identity-token style (may still work for some cookies)
        try {
            return tryLegacySisuFlow(cookieMap);
        } catch (Exception e) {
            lastError = e;
            System.err.println("[CookieAuth] Legacy SISU flow failed: " + e.getMessage());
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Cookie authentication failed (cookies may be expired or incomplete)");
    }

    private static CookieAuthResult finishWithMicrosoftToken(String msAccessToken) throws Exception {
        Map<String, String> xbl = acquireXboxLiveToken(msAccessToken);
        String xstsToken = acquireXstsToken(xbl.get("Token"));
        String identityToken = "XBL3.0 x=" + xbl.get("uhs") + ";" + xstsToken;

        JsonObject mcResponse = postMinecraftLogin(identityToken);
        if (mcResponse == null || !mcResponse.has("access_token")) {
            throw new IOException("Minecraft login did not return access_token");
        }
        String mcToken = mcResponse.get("access_token").getAsString();

        JsonObject profile = getMinecraftProfile(mcToken);
        if (profile == null || !profile.has("id") || !profile.has("name")) {
            throw new IOException("Minecraft profile missing - account may not own Java Edition");
        }

        return new CookieAuthResult(
                mcToken,
                profile.get("id").getAsString(),
                profile.get("name").getAsString()
        );
    }

    // -------------------------------------------------------------------------
    // OAuth redirect following (HttpsURLConnection)
    // -------------------------------------------------------------------------

    private static String followOAuthForAccessToken(String startUrl,
                                                   Map<String, String> cookieMap,
                                                   List<String> preferredOrder) throws Exception {
        String currentUrl = startUrl;
        final int maxHops = 12;

        for (int hop = 0; hop < maxHops; hop++) {
            HttpsURLConnection connection = openGet(currentUrl, buildCookieHeader(cookieMap, preferredOrder, currentUrl), null);
            try {
                int code = connection.getResponseCode();
                String location = connection.getHeaderField("Location");

                // Merge Set-Cookie into our map (best-effort)
                mergeSetCookies(connection, cookieMap);

                if (location != null) {
                    location = location.replace(" ", "%20");
                    String oauthError = extractOAuthError(location);
                    if (oauthError != null) {
                        throw new IOException(oauthError);
                    }

                    String token = extractAccessTokenFromUrl(location);
                    if (token != null) {
                        return token;
                    }

                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        currentUrl = resolveRedirect(currentUrl, location);
                        continue;
                    }
                }

                if (code >= 200 && code < 300) {
                    // Some flows put the token in the final URL without another Location
                    String token = extractAccessTokenFromUrl(currentUrl);
                    if (token != null) {
                        return token;
                    }
                    return null;
                }

                throw new IOException("OAuth hop failed HTTP " + code + " at " + shortUrl(currentUrl));
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many OAuth redirects");
    }

    private static String extractAccessTokenFromUrl(String url) {
        if (url == null) return null;
        // access_token=... or accessToken=...
        for (String key : new String[]{"access_token=", "accessToken="}) {
            int start = url.indexOf(key);
            if (start < 0) continue;
            String token = url.substring(start + key.length());
            int end = token.indexOf('&');
            if (end >= 0) token = token.substring(0, end);
            int hash = token.indexOf('#');
            if (hash >= 0) token = token.substring(0, hash);
            try {
                token = URLDecoder.decode(token.replace("+", "%2B"), "UTF-8");
            } catch (Exception ignored) {
            }
            if (!token.isEmpty()) return token;
        }
        // Fragment form: #access_token=...
        int hash = url.indexOf('#');
        if (hash >= 0) {
            return extractAccessTokenFromUrl(url.substring(hash + 1));
        }
        return null;
    }

    private static String extractOAuthError(String location) {
        if (location == null) return null;
        if (location.contains("error=")) {
            try {
                String err = extractQueryValue(location, "error");
                String desc = extractQueryValue(location, "error_description");
                if (err != null) {
                    return "OAuth error: " + err + (desc != null ? " - " + URLDecoder.decode(desc, "UTF-8") : "");
                }
            } catch (Exception ignored) {
            }
            return "OAuth error in redirect";
        }
        return null;
    }

    private static String extractQueryValue(String url, String key) {
        String pattern = key + "=";
        int start = url.indexOf(pattern);
        if (start < 0) return null;
        String v = url.substring(start + pattern.length());
        int end = v.indexOf('&');
        if (end >= 0) v = v.substring(0, end);
        int hash = v.indexOf('#');
        if (hash >= 0) v = v.substring(0, hash);
        return v;
    }

    // -------------------------------------------------------------------------
    // Xbox Live / XSTS / Minecraft
    // -------------------------------------------------------------------------

    private static Map<String, String> acquireXboxLiveToken(String accessToken) throws Exception {
        Exception lastError = null;
        for (String ticketPrefix : new String[]{"d=", "t="}) {
            try {
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", ticketPrefix + accessToken);

                JsonObject body = new JsonObject();
                body.add("Properties", properties);
                body.addProperty("RelyingParty", "http://auth.xboxlive.com");
                body.addProperty("TokenType", "JWT");

                String response = postJson(
                        "https://user.auth.xboxlive.com/user/authenticate",
                        body.toString(),
                        new String[][]{
                                {"Content-Type", "application/json"},
                                {"Accept", "application/json"},
                                {"x-xbl-contract-version", "1"}
                        }
                );

                JsonObject json = new JsonParser().parse(response).getAsJsonObject();
                Map<String, String> result = new LinkedHashMap<String, String>();
                result.put("Token", json.get("Token").getAsString());
                result.put("uhs", json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject()
                        .get("uhs").getAsString());
                return result;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Xbox Live authentication failed");
    }

    private static String acquireXstsToken(String xboxToken) throws Exception {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xboxToken));
        properties.add("UserTokens", userTokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        String response = postJson(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                body.toString(),
                new String[][]{
                        {"Content-Type", "application/json"},
                        {"Accept", "application/json"},
                        {"x-xbl-contract-version", "1"}
                }
        );

        JsonObject json = new JsonParser().parse(response).getAsJsonObject();
        if (json.has("XErr")) {
            throw new IOException("XSTS error: " + json.get("XErr").getAsString());
        }
        return json.get("Token").getAsString();
    }

    private static JsonObject postMinecraftLogin(String xblToken) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", xblToken);
        payload.addProperty("ensureLegacyEnabled", true);

        String response = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                payload.toString(),
                new String[][]{
                        {"Content-Type", "application/json"},
                        {"Accept", "application/json"}
                }
        );
        return new JsonParser().parse(response).getAsJsonObject();
    }

    private static JsonObject getMinecraftProfile(String accessToken) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(
                "https://api.minecraftservices.com/minecraft/profile").toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);

        int code = connection.getResponseCode();
        InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
        String body = stream == null ? "" : readStream(stream);
        connection.disconnect();
        if (code != 200) {
            throw new IOException("Minecraft profile HTTP " + code + ": " + snippet(body));
        }
        return new JsonParser().parse(body).getAsJsonObject();
    }

    // -------------------------------------------------------------------------
    // Legacy SISU identity-token fallback (original AIMyau approach, improved)
    // -------------------------------------------------------------------------

    private static final String LEGACY_SISU_URL =
            "https://sisu.xboxlive.com/connect/XboxLive/?state=login"
                    + "&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d"
                    + "&tid=896928775"
                    + "&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin"
                    + "&aid=1142970254";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"Token\":\"(.*?)\"");
    private static final Pattern UHS_PATTERN = Pattern.compile("\"uhs\":\"(.*?)\"");

    private static CookieAuthResult tryLegacySisuFlow(Map<String, String> cookieMap) throws Exception {
        String cookies = formatCookies(cookieMap);
        HttpsURLConnection connection = null;
        try {
            connection = openGet(LEGACY_SISU_URL, null, null);
            String location1 = requireLocation(connection, 1);
            connection.disconnect();

            connection = openGet(location1, cookiesFor(location1, cookies), LEGACY_SISU_URL);
            String location2 = requireLocation(connection, 2);
            connection.disconnect();

            connection = openGet(location2, cookiesFor(location2, cookies), location1);
            String location3 = requireLocation(connection, 3);
            connection.disconnect();
            connection = null;

            String xbl = decodeXboxIdentityToken(extractLegacyAccessToken(location3));
            JsonObject mcResponse = postMinecraftLogin(xbl);
            if (mcResponse == null || !mcResponse.has("access_token")) {
                throw new IOException("[Legacy] No Minecraft access_token");
            }
            String mcToken = mcResponse.get("access_token").getAsString();
            JsonObject profile = getMinecraftProfile(mcToken);
            if (profile == null || !profile.has("id") || !profile.has("name")) {
                throw new IOException("[Legacy] Minecraft profile missing");
            }
            return new CookieAuthResult(mcToken, profile.get("id").getAsString(), profile.get("name").getAsString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String extractLegacyAccessToken(String location) throws IOException {
        int start = location == null ? -1 : location.indexOf("accessToken=");
        if (start < 0) throw new IOException("[Legacy] No accessToken in redirect URL");
        String token = location.substring(start + "accessToken=".length());
        int end = token.indexOf('&');
        if (end >= 0) token = token.substring(0, end);
        token = URLDecoder.decode(token.replace("+", "%2B"), "UTF-8");
        while (token.length() % 4 != 0) token += "=";
        return token;
    }

    private static String decodeXboxIdentityToken(String accessToken) throws IOException {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(accessToken), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("[Legacy] Invalid accessToken payload", e);
        }
        String marker = "\"rp://api.minecraftservices.com/\",";
        int markerIndex = decoded.indexOf(marker);
        if (markerIndex < 0) throw new IOException("[Legacy] Minecraft relying-party payload missing");
        String minecraftPayload = decoded.substring(markerIndex + marker.length());
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(minecraftPayload);
        Matcher uhsMatcher = UHS_PATTERN.matcher(minecraftPayload);
        if (!tokenMatcher.find() || !uhsMatcher.find()) {
            throw new IOException("[Legacy] Xbox token or UHS missing");
        }
        return "XBL3.0 x=" + uhsMatcher.group(1) + ";" + tokenMatcher.group(1);
    }

    // -------------------------------------------------------------------------
    // Cookie parsing
    // -------------------------------------------------------------------------

    static Map<String, String> parseCookieContent(String content) {
        LinkedHashMap<String, String> cookies = new LinkedHashMap<String, String>();
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) return cookies;

        // JSON array (or object with "cookies" array)
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                JsonElement parsed = new JsonParser().parse(trimmed);
                JsonArray array = null;
                if (parsed.isJsonArray()) {
                    array = parsed.getAsJsonArray();
                } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("cookies")) {
                    array = parsed.getAsJsonObject().getAsJsonArray("cookies");
                }
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject cookie = element.getAsJsonObject();
                        if (!cookie.has("name") || !cookie.has("value")) continue;
                        String domain = cookie.has("domain") ? cookie.get("domain").getAsString() : "";
                        if (isMicrosoftDomain(domain) || domain.isEmpty()) {
                            putCookie(cookies, cookie.get("name").getAsString(), cookie.get("value").getAsString());
                        }
                    }
                    if (!cookies.isEmpty()) return cookies;
                }
            } catch (Exception ignored) {
            }
        }

        // Netscape / line-based
        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) continue;

            if (line.startsWith("#HttpOnly_")) {
                line = line.substring("#HttpOnly_".length());
            }

            String[] parts = line.split("\\t", -1);
            if (parts.length >= 7) {
                String domain = parts[0];
                if (isMicrosoftDomain(domain)) {
                    putCookie(cookies, parts[5], parts[6]);
                }
            } else {
                addRawCookies(cookies, line);
            }
        }

        // Whole-file raw fallback
        if (cookies.isEmpty()) {
            addRawCookies(cookies, content.replace("\n", ";").replace("\r", ""));
        }
        return cookies;
    }

    private static void addRawCookies(Map<String, String> cookies, String raw) {
        for (String part : raw.split("[;\\r\\n]+")) {
            part = part.trim();
            int separator = part.indexOf('=');
            if (separator <= 0) continue;
            putCookie(cookies, part.substring(0, separator), part.substring(separator + 1));
        }
    }

    private static void putCookie(Map<String, String> cookies, String name, String value) {
        if (name == null || value == null) return;
        name = name.trim();
        value = value.trim();
        if (name.isEmpty() || value.isEmpty()) return;
        if ("Disabled".equalsIgnoreCase(value)) return;
        cookies.remove(name);
        cookies.put(name, value);
    }

    private static boolean hasRequiredAuthCookies(Map<String, String> cookies) {
        for (String name : cookies.keySet()) {
            if (REQUIRED_AUTH_COOKIE_NAMES.contains(name)) return true;
        }
        return false;
    }

    private static boolean isMicrosoftDomain(String domain) {
        if (domain == null) return false;
        domain = domain.trim().toLowerCase(Locale.ROOT);
        if (domain.startsWith(".")) domain = domain.substring(1);
        return domain.equals("login.live.com")
                || domain.endsWith(".login.live.com")
                || domain.equals("live.com")
                || domain.endsWith(".live.com")
                || domain.equals("microsoft.com")
                || domain.endsWith(".microsoft.com")
                || domain.equals("microsoftonline.com")
                || domain.endsWith(".microsoftonline.com")
                || domain.equals("xboxlive.com")
                || domain.endsWith(".xboxlive.com")
                || domain.equals("xbox.com")
                || domain.endsWith(".xbox.com")
                || domain.equals("minecraft.net")
                || domain.endsWith(".minecraft.net");
    }

    private static String formatCookies(Map<String, String> cookies) {
        StringJoiner joiner = new StringJoiner("; ");
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            joiner.add(e.getKey() + "=" + e.getValue());
        }
        return joiner.toString();
    }

    private static String buildCookieHeader(Map<String, String> cookieMap,
                                           List<String> preferredOrder,
                                           String url) {
        if (!isMsAuthUrl(url)) return null;

        LinkedHashMap<String, String> ordered = new LinkedHashMap<String, String>();
        if (preferredOrder != null) {
            for (String name : preferredOrder) {
                if (cookieMap.containsKey(name)) {
                    ordered.put(name, cookieMap.get(name));
                }
            }
        }
        for (Map.Entry<String, String> e : cookieMap.entrySet()) {
            if (!ordered.containsKey(e.getKey())) {
                ordered.put(e.getKey(), e.getValue());
            }
        }
        return formatCookies(ordered);
    }

    private static void mergeSetCookies(HttpsURLConnection connection, Map<String, String> cookieMap) {
        // HttpsURLConnection may expose only one Set-Cookie; best-effort
        Map<String, List<String>> headers = connection.getHeaderFields();
        if (headers == null) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null) continue;
            if (!"set-cookie".equalsIgnoreCase(entry.getKey())) continue;
            for (String header : entry.getValue()) {
                if (header == null) continue;
                String nameValue = header.split(";", 2)[0];
                int eq = nameValue.indexOf('=');
                if (eq <= 0) continue;
                putCookie(cookieMap, nameValue.substring(0, eq), nameValue.substring(eq + 1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private static HttpsURLConnection openGet(String url, String cookies, String referer) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-Site", referer == null ? "none" : "cross-site");
        connection.setRequestProperty("Sec-Fetch-User", "?1");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        if (referer != null) connection.setRequestProperty("Referer", referer);
        if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.connect();
        return connection;
    }

    private static String postJson(String url, String jsonBody, String[][] extraHeaders) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (extraHeaders != null) {
            for (String[] h : extraHeaders) {
                connection.setRequestProperty(h[0], h[1]);
            }
        }
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);

        byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body);
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = stream == null ? "" : readStream(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("POST " + shortUrl(url) + " HTTP " + code + ": " + snippet(response));
        }
        return response;
    }

    private static String requireLocation(HttpsURLConnection connection, int step) throws IOException {
        int code = connection.getResponseCode();
        String location = connection.getHeaderField("Location");
        if (location == null) {
            throw new IOException("[Step " + step + "] No redirect Location (HTTP " + code + ")");
        }
        return location.replace(" ", "%20");
    }

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
                    || host.equals("microsoftonline.com") || host.endsWith(".microsoftonline.com")
                    || host.equals("sisu.xboxlive.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String resolveRedirect(String current, String location) {
        try {
            URI base = URI.create(current);
            URI resolved = base.resolve(location);
            return resolved.toString();
        } catch (Exception e) {
            return location;
        }
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

    private static String shortUrl(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, q) : url;
    }
}
