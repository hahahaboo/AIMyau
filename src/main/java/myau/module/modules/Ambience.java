package myau.module.modules;

import java.awt.Color;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class Ambience extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Time（保持不變）
    public final IntProperty time = new IntProperty("Time", 0, 0, 24000);
    public final IntProperty speed = new IntProperty("Time Speed", 0, 0, 20);

    // Weather 重寫
    public final ModeProperty weather = new ModeProperty("Weather", 0, 
            new String[] {"Unchanged", "Clear", "Rain", "Heavy Snow", "Light Snow", "Nether Particles"});
    
    public final ColorProperty snowColor = new ColorProperty("Snow Color", 
            Color.WHITE.getRGB(), () -> isSnowMode());

    private String lastWeatherMode = "";
    private long lastWeatherUpdate = 0;

    public Ambience() {
        super("Ambience", "改變玩家端的天空時間 (client-side only)", Category.RENDER, 0, false, true);
    }

    @Override
    public void onDisabled() {
        resetToServerWeather();
    }

    /** 重置為讓 server 控制的狀態 */
    private void resetToServerWeather() {
        if (mc.theWorld == null) return;
        World world = mc.theWorld;
        world.setRainStrength(0.0F);
        world.getWorldInfo().setCleanWeatherTime(Integer.MAX_VALUE);
        world.getWorldInfo().setRainTime(0);
        world.getWorldInfo().setThunderTime(0);
        world.getWorldInfo().setRaining(false);
        world.getWorldInfo().setThundering(false);
        lastWeatherMode = "";
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.theWorld != null) {
            // Time 邏輯（輕微優化避免 overflow）
            long worldTime = time.getValue() + (System.currentTimeMillis() / 50L * speed.getValue());
            mc.theWorld.setWorldTime(worldTime % 24000);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || mc.theWorld == null || mc.thePlayer == null) return;

        String currentMode = weather.getModeString();
        boolean modeChanged = !currentMode.equals(lastWeatherMode);
        long now = System.currentTimeMillis();

        // 模式改變或每 200ms 更新一次（平衡穩定與效能）
        if (modeChanged || now - lastWeatherUpdate > 200) {
            applyWeather(currentMode);
            lastWeatherMode = currentMode;
            lastWeatherUpdate = now;
        }
    }

    /** 核心 Weather 應用邏輯（完全重寫） */
    private void applyWeather(String mode) {
        if (mc.theWorld == null) return;
        World world = mc.theWorld;

        switch (mode) {
            case "Unchanged":
                // 完全不干涉，讓 server packet 正常生效
                return;

            case "Clear":
                world.setRainStrength(0.0F);
                world.getWorldInfo().setCleanWeatherTime(Integer.MAX_VALUE);
                world.getWorldInfo().setRainTime(0);
                world.getWorldInfo().setThunderTime(0);
                world.getWorldInfo().setRaining(false);
                world.getWorldInfo().setThundering(false);
                break;

            case "Rain":
                applyPrecipitation(world, 1.0F, false); // 雨
                break;

            case "Heavy Snow":
            case "Light Snow":
                applyPrecipitation(world, 0.8F, true); // 雪（較低強度更像雪）
                break;

            case "Nether Particles":
                applyNetherParticles(world); // 特殊 Nether 效果
                break;
        }
    }

    /** 統一降水處理（Rain / Snow） */
    private void applyPrecipitation(World world, float strength, boolean isSnow) {
        world.setRainStrength(strength);
        world.getWorldInfo().setCleanWeatherTime(0);
        world.getWorldInfo().setRainTime(Integer.MAX_VALUE);
        world.getWorldInfo().setThunderTime(0);
        world.getWorldInfo().setRaining(true);
        world.getWorldInfo().setThundering(false);
    }

    /** Nether Particles 特殊處理 */
    private void applyNetherParticles(World world) {
        // Nether 通常無雨，但我們強制粒子效果 + 低溫
        world.setRainStrength(0.3F); // 微弱 "雨" 強度觸發粒子
        world.getWorldInfo().setCleanWeatherTime(0);
        world.getWorldInfo().setRainTime(Integer.MAX_VALUE);
        world.getWorldInfo().setRaining(true);
        world.getWorldInfo().setThundering(false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        String mode = weather.getModeString();
        if (mode.equals("Unchanged")) return;

        if (event.getPacket() instanceof S03PacketTimeUpdate) {
            event.setCancelled(true);
        } else if (event.getPacket() instanceof S2BPacketChangeGameState) {
            S2BPacketChangeGameState packet = (S2BPacketChangeGameState) event.getPacket();
            int state = packet.getGameState();
            // 取消 server 的雨/雷/天气改變 packet
            if (state == 1 || state == 2 || state == 7 || state == 8) {
                event.setCancelled(true);
            }
        }
    }

    // === Hook 方法（供 Mixin 呼叫）===
    public float getFloatTemperature(BlockPos blockPos, BiomeGenBase biomeGenBase) {
        if (!isEnabled()) return biomeGenBase.getFloatTemperature(blockPos);

        String mode = weather.getModeString();
        if (mode.equals("Heavy Snow") || mode.equals("Light Snow") || mode.equals("Nether Particles")) {
            return -0.5F; // 強制極低溫確保下雪 / Nether 效果
        }
        if (mode.equals("Rain")) {
            return 0.3F;
        }
        return biomeGenBase.getFloatTemperature(blockPos);
    }

    public boolean skipRainParticles() {
        if (!isEnabled()) return false;
        String mode = weather.getModeString();
        return mode.equals("Heavy Snow") || mode.equals("Light Snow") || mode.equals("Nether Particles");
    }

    private boolean isSnowMode() {
        String mode = weather.getModeString();
        return mode.equals("Heavy Snow") || mode.equals("Light Snow");
    }
}
