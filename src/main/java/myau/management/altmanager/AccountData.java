package myau.management.altmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountData {
    private static Map<String, Boolean> names = new HashMap<>();

    private static String[] getLines() throws Exception {
        String rawUrl = "https://pastebin.com/raw/XDyRmgiE";
        List<String> lines = new ArrayList<>();
        URL url = new URL(rawUrl);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines.toArray(new String[0]);
    }

    public static void start() {
        try {
            names.clear();
            for (String name : getLines()) {
                names.put(name, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Boolean> getNames() {
        return names;
    }
}
