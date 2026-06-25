package myau.command.commands;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import myau.Myau;
import myau.command.Command;
import myau.enums.ChatColors;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;

public class DenickCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public DenickCommand() {
        super(new ArrayList<>(Collections.singletonList("denick")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted(String.format("%sUsage: .%s <player> | .%s all&r", 
                Myau.clientName, args.get(0).toLowerCase(), args.get(0).toLowerCase()));
            return;
        }

        String target = args.get(1);
        if (target.equalsIgnoreCase("all")) {
            denickAllPlayers();
        } else {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(ChatColors.formatColor(target));
            if (playerInfo != null) {
                denickPlayer(playerInfo);
            } else {
                ChatUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%sNo entity with name &o%s&r"),
                                ChatColors.formatColor(Myau.clientName),
                                target
                        )
                );
            }
        }
    }

    private void denickAllPlayers() {
        Collection<NetworkPlayerInfo> playerInfoMap = mc.getNetHandler().getPlayerInfoMap();
        if (playerInfoMap.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sNo players online&r", Myau.clientName));
            return;
        }

        ChatUtil.sendRaw(ChatColors.formatColor(Myau.clientName + "&fDenicking all players:"));
        for (NetworkPlayerInfo playerInfo : playerInfoMap) {
            denickPlayer(playerInfo);
        }
    }

    private void denickPlayer(NetworkPlayerInfo playerInfo) {
        GameProfile gameProfile = playerInfo.getGameProfile();
        Property property = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
        String displayName = gameProfile.getName().replace("§", "&");

        if (property != null) {
            try {
                String code = new String(Base64.getDecoder().decode(property.getValue().getBytes(StandardCharsets.UTF_8)));
                String name = code.contains("profileName\" : \"") ? code.split("profileName\" : \"")[1].split("\"")[0] : "?";
                String uuid = code.contains("profileId\" : \"") ? code.split("profileId\" : \"")[1].split("\"")[0] : "?";

                // 判斷是否需要紅色
                boolean shouldRed = !displayName.equalsIgnoreCase(name) || name.equals("?");

                String clientPrefix = ChatColors.formatColor(Myau.clientName);

                if (shouldRed) {
                    // 使用兩個 sendRaw：前半部 + 紅色顯示名稱 + 後半部
                    ChatUtil.sendRaw(clientPrefix + ChatColors.formatColor("&c" + displayName + "&r &f-> " + name + " (&o" + uuid + "&r)"));
                } else {
                    // 正常顏色：單一 sendRaw
                    ChatUtil.sendRaw(clientPrefix + ChatColors.formatColor(displayName + "&r &f-> " + name + " (&o" + uuid + "&r)"));
                }

                // Only copy UUID in single player mode
                if (!uuid.isEmpty() && !uuid.equals("?")) {
                    if (!isAllMode()) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uuid), null);
                    }
                }
            } catch (Exception e) {
                ChatUtil.sendRaw(ChatColors.formatColor(Myau.clientName + "&cError decoding textures for &o" + displayName));
            }
        } else {
            ChatUtil.sendRaw(
                    String.format(
                            ChatColors.formatColor("%sNo textures for entity with name &o%s&r"),
                            ChatColors.formatColor(Myau.clientName),
                            displayName
                    )
            );
        }
    }

    private boolean isAllMode() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if (element.getMethodName().contains("denickAllPlayers")) {
                return true;
            }
        }
        return false;
    }
}
