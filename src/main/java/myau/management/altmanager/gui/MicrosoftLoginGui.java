package myau.management.altmanager.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.management.altmanager.AltManagerGui;
import myau.management.altmanager.auth.refresh.RefreshTokenAuthentication;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;
import myau.management.altmanager.auth.refresh.model.MinecraftProfileResponse;
import myau.management.altmanager.auth.refresh.model.MinecraftTokenResponse;
import myau.ui.impl.gui.BackgroundRenderer;
import myau.util.font.FontManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MicrosoftLoginGui extends GuiScreen {
    private final AltManagerGui parent;
    private GuiTextField tokenField;

    public MicrosoftLoginGui(AltManagerGui parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {

        int centerX = this.width / 2;
        int fieldWidth = 150;
        int fieldHeight = 20;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int baseY = this.height / 2 - 20;

        this.buttonList.clear();
        this.tokenField = new GuiTextField(0, this.fontRendererObj, centerX - (fieldWidth / 2), baseY, fieldWidth, fieldHeight);
        this.tokenField.setMaxStringLength(32767);
        // 使用 .trim() 去除可能的首尾空格
        GuiButton loginButton = new GuiButton(0, centerX - (buttonWidth / 2), baseY + fieldHeight + 10, buttonWidth, buttonHeight, "Login");
        GuiButton backButton = new GuiButton(1, centerX - (buttonWidth / 2), baseY + fieldHeight + 40, buttonWidth, buttonHeight, "Back");

        this.buttonList.add(loginButton);
        this.buttonList.add(backButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        BackgroundRenderer.draw(this.width, this.height);
        // Use custom font renderer if available
        if (mc.fontRendererObj != null) {
            mc.fontRendererObj.drawString("Token Login", (int) (this.width / 2.0f - FontManager.productSans20.getStringWidth("Token Login") / 2.0f), 20, 0xFFFFFF);
            mc.fontRendererObj.drawString("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            mc.fontRendererObj.drawString("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        } else {
            // Fallback to standard Minecraft font renderer
            FontRenderer fontRenderer = mc.fontRendererObj;
            mc.fontRendererObj.drawStringWithShadow("Token Login", this.width / 2.0f - fontRenderer.getStringWidth("Token Login") / 2.0f, 20, 0xFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            mc.fontRendererObj.drawStringWithShadow("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        }
        this.tokenField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            // 添加非空检查
            String token = tokenField.getText().trim();
            if (!token.isEmpty()) {
                loginWithToken(token);
            }
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(parent);
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        this.tokenField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void loginWithToken(String token) {
        AltManagerGui.status = "§eAnalyzing Token...";
        final String cleanToken = token.trim();

        new Thread(() -> {
            try {
                // 第一步：如果是超长的 JWT，这通常是最终的 Minecraft Token 或 微软 Access Token
                if (cleanToken.startsWith("eyJ") || cleanToken.length() > 500) {
                    AltManagerGui.status = "§eAttempting Direct Login...";
                    try {
                        String[] profile = getProfileInfo(cleanToken); // 尝试直接获取 profile
                        handleLoginSuccess(cleanToken, profile[0], profile[1]);
                        return;
                    } catch (IOException e) {
                        // 如果报 401，说明它是微软 Token，需要走完整的微软链（本代码暂未实现该特定路径）
                        mc.addScheduledTask(() -> AltManagerGui.status = "§cInvalid Access Token (401)");
                        return;
                    }
                }else{
                    // Refresh token：走 org.localts 移植過來的完整鏈路
                    // OAuth refresh -> OAuth access -> XBL -> XSTS -> MC -> 檢查持有 -> 取得 profile
                    AltManagerGui.status = "§eRefreshing token...";
                    try {
                        MinecraftTokenResponse mcToken = RefreshTokenAuthentication.authenticateWithRefreshToken(cleanToken);
                        MinecraftProfileResponse profile = RefreshTokenAuthentication.getMinecraftProfile(mcToken);
                        // 這裡儲存的是原始 refresh token，而不是短效的 Minecraft access token，
                        // 讓這個 Alt 之後也可以被 AltManagerGui 的 hasRefreshToken() 流程正常重新登入
                        handleLoginSuccess(mcToken.getAccessToken(), profile.getUsername(), profile.getUuid().toString());
                    } catch (AuthenticationException e) {
                        String errorMessage = e.getMessage();
                        mc.addScheduledTask(() -> AltManagerGui.status = "§c" + errorMessage);
                    }
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                mc.addScheduledTask(() -> AltManagerGui.status = "§cLogin Failed");
            }
        }).start();
    }

    private void handleLoginSuccess(String token, String username, String uuid) {
        // 1. 创建 Minecraft Session
        net.minecraft.util.Session newSession = new net.minecraft.util.Session(username, uuid, token, "mojang");

        try {
            // 2. 反射或通过工具类设置当前游戏的 Session
            myau.management.altmanager.SessionUtil.setSession(mc, newSession);

            mc.addScheduledTask(() -> {
                AltManagerGui.status = "§aLogged in as " + username;

                // 3. Token Login 帳號不寫入 alt 清單、不儲存到檔案，僅切換 session
                this.mc.displayGuiScreen(parent);
            });
        } catch (Exception e) {
            e.printStackTrace();
            mc.addScheduledTask(() -> AltManagerGui.status = "§cSession Switch Failed");
        }
    }

    // --- 重点修复部分 ---
    private String[] getProfileInfo(String token) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("https://api.minecraftservices.com/minecraft/profile");
            request.setHeader("Authorization", "Bearer " + token);

            try (CloseableHttpResponse response = client.execute(request)) {
                // 1. 获取 HTTP 状态码
                int statusCode = response.getStatusLine().getStatusCode();
                String jsonString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                // 2. 如果不是 200 OK，说明 Token 无效
                if (statusCode != 200) {
                    throw new IOException("API returned " + statusCode + ": " + jsonString);
                }

                JsonParser parser = new JsonParser();
                JsonObject json = parser.parse(jsonString).getAsJsonObject();

                // 3. 安全检查：确保字段存在
                if (!json.has("name") || !json.has("id")) {
                    throw new IOException("Invalid JSON response (Missing name/id): " + jsonString);
                }

                String username = json.get("name").getAsString();
                String uuid = json.get("id").getAsString();
                return new String[]{username, uuid};
            }
        }
    }
}
