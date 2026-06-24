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
import java.util.Collections;
import java.util.Locale;

public class DenickCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public DenickCommand() {
        super(new ArrayList<>(Collections.singletonList("denick")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            ChatUtil.sendFormatted(String.format("%sUsage: .%s <&oname&r> | .%s all&r", 
                Myau.clientName, args.get(0).toLowerCase(Locale.ROOT), args.get(0).toLowerCase(Locale.ROOT)));
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
        var playerInfoMap = mc.getNetHandler().getPlayerInfoMap();
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

                ChatUtil.sendRaw(
                        String.format(
                                ChatColors.formatColor("%s%s&r -> %s (&o%s&r)&r"),
                                ChatColors.formatColor(Myau.clientName),
                                displayName,
                                name,
                                uuid
                        )
                );

                // 僅在 single mode 時複製 UUID（all 模式不複製，避免覆蓋剪貼簿）
                if (!uuid.isEmpty() && !uuid.equals("?") && !isAllMode()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uuid), null);
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
        // 簡易標記（實際上可透過 ThreadLocal 或 context 更優，但此處為簡單實現）
        return Thread.currentThread().getStackTrace()[3].getMethodName().contains("denickAllPlayers");
    }
}
