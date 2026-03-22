package myau.module.modules;

import myau.enums.ChatColors;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;

public class NickHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final TextProperty protectName;
    public final BooleanProperty scoreboard = new BooleanProperty("scoreboard", true);
    public final BooleanProperty level = new BooleanProperty("level", true);

    public NickHider() {
        super("NickHider", "Custom ur IGN", Category.MISC, 0, false, true);
        this.protectName = new TextProperty("name", "You");
    }

    /**
     * Replaces player's nickname in chat messages with the protected name
     * @param input The original string that might contain the player's name
     * @return The string with the player's name replaced with the protected name
     */
    public String replaceNick(String input) {
        if (input != null && mc.thePlayer != null) {
            if (this.scoreboard.getValue() && input.matches("§7\\d{2}/\\d{2}/\\d{2}(?:\\d{2})?  ?§8.*")) {
                input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
            }
            String replacementText = this.protectName.getValue().equals("You") ?
                mc.thePlayer.getName() : ChatColors.formatColor(this.protectName.getValue());
            return input.replaceAll(
                    mc.thePlayer.getName(), Matcher.quoteReplacement(replacementText)
            );
        } else {
            return input;
        }
    }
}
