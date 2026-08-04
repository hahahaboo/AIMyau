package myau.management.altmanager.auth.refresh.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftProfileResponse {

    private String username;
    private UUID uuid;
    private Set<String> capes;

    public static MinecraftProfileResponse fromJson(JsonObject json) {
        MinecraftProfileResponse profile = new MinecraftProfileResponse();

        profile.setUsername(json.get("name").getAsString());
        profile.setUuid(UUID.fromString(dashedUUID(json.get("id").getAsString())));

        if (json.has("capes") && json.get("capes").isJsonArray()) {
            Set<String> capeSet = new HashSet<>();
            JsonArray capesArray = json.get("capes").getAsJsonArray();
            for (JsonElement element : capesArray) {
                capeSet.add(element.getAsJsonObject().get("alias").getAsString());
            }
            profile.setCapes(capeSet);
        }

        return profile;
    }

    private static String dashedUUID(String input) {
        return input.replaceAll("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
    }

}
