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

                // 新增邏輯：如果顯示名稱與真實名稱不同，則將顯示名稱標記為紅色
                String coloredDisplayName = displayName;
                if (!displayName.equalsIgnoreCase(name) && !name.equals("?")) {
                    coloredDisplayName = "&c" + displayName + "&r";
                }

                ChatUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%s%s&r -> %s (&o%s&r)&r"),
                                ChatColors.formatColor(Myau.clientName),
                                coloredDisplayName,
                                name,
                                uuid
                        )
                );

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
        // Simple stack trace check to avoid copying UUID for every player in 'all' mode
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if (element.getMethodName().contains("denickAllPlayers")) {
                return true;
            }
        }
        return false;
    }
}
