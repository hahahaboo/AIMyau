package myau.management.altmanager.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.management.altmanager.AltManagerGui;
import myau.management.altmanager.auth.MicrosoftAuthResult;
import myau.management.altmanager.auth.MicrosoftAuthenticator;
import myau.ui.impl.gui.BackgroundRenderer;
import myau.util.font.FontManager;
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
        if (FontManager.productSans20 != null) {
            FontManager.productSans20.drawCenteredString("Token Login", this.width / 2.0f, 20, 0xFFFFFF);
            FontManager.productSans20.drawString("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            FontManager.productSans20.drawString("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        } else {
            drawCenteredString(this.fontRendererObj, "Token Login", this.width / 2, 20, 0xFFFFFF);
            this.fontRendererObj.drawStringWithShadow("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            this.fontRendererObj.drawStringWithShadow("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        }
        this.tokenField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw status text again to ensure it's on top of everything
        if (FontManager.productSans20 != null) {
            FontManager.productSans20.drawString("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            FontManager.productSans20.drawString("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        } else {
            this.fontRendererObj.drawStringWithShadow("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            this.fontRendererObj.drawStringWithShadow("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        }
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
                }

                // 第二步：如果是你提供的这种 mCgg... 格式，尝试作为 Refresh Token 刷新
                // 改用與 MicrosoftLogin.login(refreshToken) 相同的多 Client 嘗試邏輯
                // （先試 Tenacity 專用 client，失敗再退回 Vanilla 官方 client）
                AltManagerGui.status = "§eRefreshing Microsoft Session...";
                MicrosoftAuthenticator auth = new MicrosoftAuthenticator();

                try {
                    MicrosoftAuthResult result = auth.loginWithRefreshTokenNew(cleanToken);
                    if (result != null) {
                        handleLoginSuccess(result.getAccessToken(), result.getProfile().getName(), result.getProfile().getId());
                    }
                } catch (Exception e) {
                    // 捕获 400 错误（順帶修正 e.getMessage() 可能為 null 造成的 NPE）
                    if (e.getMessage() != null && e.getMessage().contains("400")) {
                        mc.addScheduledTask(() -> AltManagerGui.status = "§cBad Request (400): Token Incompatible");
                    } else {
                        throw e;
                    }
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

                // 3. 更新账号列表逻辑
                myau.management.altmanager.Alt existingAlt = null;
                for (myau.management.altmanager.Alt alt : AltManagerGui.alts) {
                    if (alt.getName().equals(username)) {
                        existingAlt = alt;
                        break;
                    }
                }

                if (existingAlt != null) {
                    existingAlt.setUuid(uuid);
                    existingAlt.setRefreshToken(token);
                } else {
                    myau.management.altmanager.Alt alt = new myau.management.altmanager.Alt(username, "", username, false);
                    alt.setUuid(uuid);
                    alt.setRefreshToken(token);
                    AltManagerGui.alts.add(alt);
                }

                // 4. 保存到文件并返回主界面
                myau.management.altmanager.util.AltJsonHandler.saveAlts();
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
