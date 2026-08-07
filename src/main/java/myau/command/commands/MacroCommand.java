package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.util.ChatUtil;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MacroCommand extends Command {
    public MacroCommand() {
        super(new ArrayList<>(Arrays.asList("macro", "m")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() < 2) {
            sendUsage(args.get(0));
            return;
        }

        String sub = args.get(1).toLowerCase(Locale.ROOT);

        // .macro list
        if (sub.equals("list")) {
            Map<Integer, List<String>> all = Myau.macroManager.getAll();
            if (all.isEmpty()) {
                ChatUtil.sendFormatted(String.format("%sNo macros&r", Myau.clientName));
                return;
            }
            ChatUtil.sendFormatted(String.format("%sMacros:&r", Myau.clientName));
            for (Map.Entry<Integer, List<String>> entry : all.entrySet()) {
                String keyName = KeyBindUtil.getKeyName(entry.getKey());
                for (String cmd : entry.getValue()) {
                    ChatUtil.sendFormatted(String.format("&7»&r &l[%s]&r %s&r", keyName, cmd));
                }
            }
            return;
        }

        // 解析 key
        String keyInput = args.get(1).toUpperCase(Locale.ROOT);
        int keyIndex = Keyboard.getKeyIndex(keyInput);
        if (keyIndex == 0) {
            int buttonIndex = Mouse.getButtonIndex(keyInput);
            if (buttonIndex != -1) {
                keyIndex = buttonIndex - 100;
            }
        }

        if (keyIndex == 0) {
            ChatUtil.sendFormatted(String.format("%sInvalid key (&o%s&r)&r", Myau.clientName, args.get(1)));
            return;
        }

        // .macro <key> clear
        if (args.size() >= 3 && args.get(2).equalsIgnoreCase("clear")) {
            Myau.macroManager.clear(keyIndex);
            ChatUtil.sendFormatted(String.format("%sCleared macros for &l[%s]&r", Myau.clientName, KeyBindUtil.getKeyName(keyIndex)));
            return;
        }

        // .macro <key> <command...>
        if (args.size() < 3) {
            sendUsage(args.get(0));
            return;
        }

        // 後面整串（含空格）當 command
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.size(); i++) {
            if (i > 2) sb.append(' ');
            sb.append(args.get(i));
        }
        String command = sb.toString().trim();

        if (!command.startsWith(".")) {
            ChatUtil.sendFormatted(String.format("%sCommand must start with &o.&r (&o%s&r)&r", Myau.clientName, command));
            return;
        }

        Myau.macroManager.add(keyIndex, command);
        ChatUtil.sendFormatted(String.format("%sAdded macro &l[%s]&r → &o%s&r", Myau.clientName, KeyBindUtil.getKeyName(keyIndex), command));
    }

    private void sendUsage(String name) {
        String cmd = name.toLowerCase(Locale.ROOT);
        ChatUtil.sendFormatted(
                String.format(
                        "%sUsage: .%s &olist&r/&ol&r | .%s <&okey&r> &oclear&r | .%s <&okey&r> <&ocommand&r>&r",
                        Myau.clientName, cmd, cmd, cmd
                )
        );
    }
}
