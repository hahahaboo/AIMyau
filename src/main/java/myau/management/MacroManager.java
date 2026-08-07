package myau.management;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.KeyEvent;
import myau.util.KeyBindUtil;

import java.util.*;

public class MacroManager {
    // keyCode -> list of commands (each starts with ".")
    public final Map<Integer, List<String>> macros = new LinkedHashMap<>();

    public void add(int key, String command) {
        macros.computeIfAbsent(key, k -> new ArrayList<>()).add(command);
    }

    public void clear(int key) {
        macros.remove(key);
    }

    public void clearAll() {
        macros.clear();
    }

    public List<String> get(int key) {
        return macros.getOrDefault(key, Collections.emptyList());
    }

    public Map<Integer, List<String>> getAll() {
        return macros;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        List<String> cmds = macros.get(event.getKey());
        if (cmds == null || cmds.isEmpty()) return;

        for (String cmd : cmds) {
            if (cmd != null && !cmd.isEmpty()) {
                Myau.commandManager.handleCommand(cmd);
            }
        }
    }
}
