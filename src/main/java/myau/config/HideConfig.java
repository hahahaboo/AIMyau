package myau.config;

import com.google.gson.*;
import myau.Myau;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class HideConfig {
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public String name;
    public File file;

    public HideConfig(String name, boolean newConfig) {
        this.name = name;
        this.file = new File("./config/AIMyau/", String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                ((IAccessorMinecraft) mc).getLogger().info("Created: {}", this.file.getName());
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
        }
    }

    public void load() {
        try {
            if (!file.exists()) {
                // 如果文件不存在，创建一个空的配置文件
                save();
                return;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(file)));
            JsonObject jsonObject = parsed.getAsJsonObject();

            // 获取隐藏模块列表
            JsonElement hiddenModulesElement = jsonObject.get("hidden_modules");
            Set<String> hiddenModules = new HashSet<>();

            if (hiddenModulesElement != null && hiddenModulesElement.isJsonArray()) {
                for (JsonElement element : hiddenModulesElement.getAsJsonArray()) {
                    hiddenModules.add(element.getAsString());
                }
            }

            // 应用隐藏设置到模块
            for (Module module : Myau.moduleManager.modules.values()) {
                if (hiddenModules.contains(module.getName())) {
                    module.setHidden(true);
                }
            }

            ChatUtil.sendFormatted(String.format("%sHidden modules config has been loaded (&a&o%s&r)&r", Myau.clientName, file.getName()));
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error loading hidden modules config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sHidden modules config couldn't be loaded (&c&o%s&r)&r", Myau.clientName, file.getName()));
        }
    }

    public void save() {
        try {
            JsonObject object = new JsonObject();

            // 收集所有隐藏的模块
            Set<String> hiddenModules = new HashSet<>();
            for (Module module : Myau.moduleManager.modules.values()) {
                if (module.isHidden()) {
                    hiddenModules.add(module.getName());
                }
            }

            // 创建隐藏模块数组
            JsonArray hiddenModulesArray = new JsonArray();
            for (String moduleName : hiddenModules) {
                hiddenModulesArray.add(new JsonPrimitive(moduleName));
            }

            object.add("hidden_modules", hiddenModulesArray);

            file.getParentFile().mkdirs();
            PrintWriter printWriter = new PrintWriter(new FileWriter(file));
            printWriter.println(gson.toJson(object));
            printWriter.close();
            ChatUtil.sendFormatted(String.format("%sHidden modules config has been saved (&a&o%s&r)&r", Myau.clientName, file.getName()));
        } catch (IOException e) {
            ChatUtil.sendFormatted(String.format("%sHidden modules config couldn't be saved (&c&o%s&r)&r", Myau.clientName, file.getName()));
        }
    }
}
