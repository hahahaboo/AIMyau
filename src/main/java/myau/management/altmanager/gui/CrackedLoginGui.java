package myau.management.altmanager.gui;

import myau.management.altmanager.Alt;
import myau.management.altmanager.AltManagerGui;
import myau.management.altmanager.SessionChanger;
import myau.management.altmanager.util.AltJsonHandler;
import myau.management.altmanager.util.NameGenerator;
import myau.ui.impl.gui.BackgroundRenderer;
import myau.util.font.FontManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;

public class CrackedLoginGui extends GuiScreen {
    private AltManagerGui parent;
    private GuiButton loginButton, randomButton, backButton;
    private GuiTextField usernameField;

    public CrackedLoginGui(AltManagerGui parent) {
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
        int rowY1 = baseY + fieldHeight + 35;
        int rowY2 = rowY1 + buttonHeight + 5;
        int halfButtonWidth = buttonWidth / 2;


        this.buttonList.clear();
        this.usernameField = new GuiTextField(0, this.fontRendererObj, centerX - (fieldWidth / 2), baseY, fieldWidth, fieldHeight);
        this.usernameField.setMaxStringLength(14);

        this.loginButton = new GuiButton(0, centerX - halfButtonWidth, rowY1, halfButtonWidth - 3, buttonHeight, "Login");
        this.randomButton = new GuiButton(2, centerX + 3, rowY1, halfButtonWidth - 3, buttonHeight, "Random");
        this.backButton = new GuiButton(1, centerX - (buttonWidth / 4), rowY2, buttonWidth / 2, buttonHeight, "Back");

        this.buttonList.add(loginButton);
        this.buttonList.add(randomButton);
        this.buttonList.add(backButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        BackgroundRenderer.draw(this.width, this.height);
        // Use custom font renderer if available
        if (mc.fontRendererObj != null) {
            mc.fontRendererObj.drawString("Cracked Login", (int) (this.width / 2.0f - FontManager.productSans20.getStringWidth("Cracked Login") / 2.0f), 20, 0xFFFFFF);
            mc.fontRendererObj.drawString("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            mc.fontRendererObj.drawString("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        } else {
            // Fallback to standard Minecraft font renderer
            FontRenderer fontRenderer = mc.fontRendererObj;
            mc.fontRendererObj.drawStringWithShadow("Cracked Login", this.width / 2.0f - fontRenderer.getStringWidth("Cracked Login") / 2.0f, 20, 0xFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("Current Alt: §a" + mc.getSession().getUsername(), 5, 5, 0xAAAAAA);
            mc.fontRendererObj.drawStringWithShadow("Status: " + AltManagerGui.status, 5, 20, 0xAAAAAA);
        }
        this.usernameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            if (!usernameField.getText().equals("")) {
                String username = usernameField.getText();
                Alt alt = new Alt(null, null, username, true);
                parent.alts.add(alt);
                SessionChanger.instance().loginCracked(username);
                AltJsonHandler.start();
                AltJsonHandler.saveAlts();
                AltJsonHandler.loadAlts();
                AltManagerGui.status = "§aAdded cracked alt " + username;
                this.mc.displayGuiScreen(parent);
            }
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            usernameField.setText(NameGenerator.generateRandomUsername());
        }
    }


    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        this.usernameField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.usernameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }


}

