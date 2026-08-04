package myau.management.altmanager.auth.refresh.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import myau.management.altmanager.auth.refresh.exception.AuthenticationException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementsResponse {

    private static final Set<String> SOURCES = new HashSet<>(Arrays.asList("GAMEPASS", "PURCHASE", "MC_PURCHASE"));
    private Map<String, String> entitlements;

    public static EntitlementsResponse fromJson(JsonObject json) {
        EntitlementsResponse entitlementsResponse = new EntitlementsResponse();

        if (!json.has("items")) {
            throw new AuthenticationException("Couldn't receive entitlements");
        }

        Map<String, String> entitlementMap = new HashMap<>();
        for (JsonElement element : json.getAsJsonArray("items")) {
            JsonObject obj = element.getAsJsonObject();
            entitlementMap.put(obj.get("name").getAsString(), obj.get("source").getAsString());
        }
        entitlementsResponse.setEntitlements(entitlementMap);

        return entitlementsResponse;
    }

    public boolean checkOwnership() {
        for (Map.Entry<String, String> entry : entitlements.entrySet()) {
            if (entry.getKey().contains("minecraft") && SOURCES.contains(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

}
