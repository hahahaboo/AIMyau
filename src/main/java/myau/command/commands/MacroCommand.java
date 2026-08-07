package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.util.ChatUtil;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public class MacroCommand extends Command {
    public MacroCommand() {
        super(new ArrayList<>(Arrays.asList("macro")));
    }

    private int parseKey(String input) {
        String keyInput = input.toUpperCase(Locale.ROOT);
        int keyIndex = Keyboard.getKeyIndex(keyInput);
        if (keyIndex == 0) {
            int buttonIndex = Mouse.getButtonIndex(keyInput);
            if (buttonIndex != -1) {
                keyIndex = buttonIndex - 100;
            }
        }
        return keyIndex;
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        String label = args.get(0).toLowerCase(Locale.ROOT);

        if (args.size() < 2) {
            ChatUtil.sendFormatted(
                    String.format(
                            "%sUsage: .%s <&olist&r/&ol&r> | .%s <&okey&r> &oclear&r | .%s <&okey&r> <&ocommand&r>&r",
                            Myau.clientName, label, label, label
                    )
            );
            return;
        }

        String sub = args.get(1);

        if (sub.equalsIgnoreCase("list") || sub.equalsIgnoreCase("l")) {
            if (Myau.macroManager.macros.isEmpty()) {
                ChatUtil.sendFormatted(String.format("%sNo macros&r", Myau.clientName));
                return;
            }
            ChatUtil.sendFormatted(String.format("%sMacros:&r", Myau.clientName));
            for (Map.Entry<Integer, ArrayList<String>> entry : Myau.macroManager.macros.entrySet()) {
                for (String command : entry.getValue()) {
                    ChatUtil.sendRaw(
                            String.format("&7%s&r: &f%s&r", KeyBindUtil.getKeyName(entry.getKey()), command).replace("&", "§")
                    );
                }
            }
            return;
        }

        int key = this.parseKey(sub);
        if (key == 0) {
            ChatUtil.sendFormatted(String.format("%sInvalid key (&o%s&r)&r", Myau.clientName, sub));
            return;
        }

        if (args.size() < 3) {
            ChatUtil.sendFormatted(
                    String.format(
                            "%sUsage: .%s <&okey&r> &oclear&r | .%s <&okey&r> <&ocommand&r>&r",
                            Myau.clientName, label, label
                    )
            );
            return;
        }

        if (args.get(2).equalsIgnoreCase("clear")) {
            boolean had = Myau.macroManager.clearMacros(key);
            if (had) {
                ChatUtil.sendFormatted(
                        String.format("%sCleared macros for &o%s&r&r", Myau.clientName, KeyBindUtil.getKeyName(key))
                );
            } else {
                ChatUtil.sendFormatted(
                        String.format("%sNo macros for &o%s&r&r", Myau.clientName, KeyBindUtil.getKeyName(key))
                );
            }
            return;
        }

        String command = String.join(" ", args.subList(2, args.size()));
        if (!command.startsWith(".")) {
            ChatUtil.sendFormatted(String.format("%sCommand must start with '&o.&r'&r", Myau.clientName));
            return;
        }

        Myau.macroManager.addMacro(key, command);
        ChatUtil.sendFormatted(
                String.format(
                        "%sAdded macro on &o%s&r&r: &f%s&r".replace("&", "§").replace("§r&r", "&r"),
                        Myau.clientName, KeyBindUtil.getKeyName(key), command
                )
        );
    }
}
