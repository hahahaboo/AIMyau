package myau.util.font;

import myau.util.font.impl.FontRenderer;
import myau.util.font.impl.FontUtil;
import myau.util.font.impl.MinecraftFontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import java.util.HashMap;
import java.util.Map;

import static myau.config.Config.mc;

public class FontManager {
    public static FontRenderer
            productSans16, productSans18, productSans20,
            tenacity12, tenacity16, tenacity20,
            tahomaBold12, tahomaBold16, tahomaBold20,
            noti20,
            nunitoBold20, nunitoBold48, nunitoBold80,
            harmonyOS_Sans20;

    private static int prevScale;

    static {
        initializeFonts();
    }

    public static void initializeFonts() {
        Map<String, java.awt.Font> locationMap = new HashMap<>();

        ScaledResolution sr = new ScaledResolution(mc);

        int scale = sr.getScaleFactor();

        if (scale != prevScale) {
            prevScale = scale;

            releaseAllFonts();

            // Product Sans (Google Style)
            productSans16 = new FontRenderer(FontUtil.getResource(locationMap, "product_sans_regular.ttf", 16));
            productSans18 = new FontRenderer(FontUtil.getResource(locationMap, "product_sans_regular.ttf", 18));
            productSans20 = new FontRenderer(FontUtil.getResource(locationMap, "product_sans_regular.ttf", 20));

            // Tenacity Fonts
            tenacity12 = new FontRenderer(FontUtil.getResource(locationMap, "tenacity.ttf", 12));
            tenacity16 = new FontRenderer(FontUtil.getResource(locationMap, "tenacity.ttf", 16));
            tenacity20 = new FontRenderer(FontUtil.getResource(locationMap, "tenacity.ttf", 20));

            // Tahoma Bold
            tahomaBold12 = new FontRenderer(FontUtil.getResource(locationMap, "tahomabold.ttf", 12));
            tahomaBold16 = new FontRenderer(FontUtil.getResource(locationMap, "tahomabold.ttf", 16));
            tahomaBold20 = new FontRenderer(FontUtil.getResource(locationMap, "tahomabold.ttf", 20));

            // Notification Icons
            noti20 = new FontRenderer(FontUtil.getResource(locationMap, "noti.ttf", 20));

            // Nunito Bold
            nunitoBold20 = new FontRenderer(FontUtil.getResource(locationMap, "Nunito-Bold.ttf", 20));
            nunitoBold48 = new FontRenderer(FontUtil.getResource(locationMap, "Nunito-Bold.ttf", 48));
            nunitoBold80 = new FontRenderer(FontUtil.getResource(locationMap, "Nunito-Bold.ttf", 80));

            harmonyOS_Sans20 = new FontRenderer(FontUtil.getResource(locationMap, "harmonyOS_Sans.ttf", 20));
        }
    }

    public static void releaseAllFonts() {
        if (productSans16 != null) {
            productSans16.destroy();
            productSans16 = null;
        }
        if (productSans18 != null) {
            productSans18.destroy();
            productSans18 = null;
        }
        if (productSans20 != null) {
            productSans20.destroy();
            productSans20 = null;
        }
        if (tenacity12 != null) {
            tenacity12.destroy();
            tenacity12 = null;
        }
        if (tenacity16 != null) {
            tenacity16.destroy();
            tenacity16 = null;
        }
        if (tenacity20 != null) {
            tenacity20.destroy();
            tenacity20 = null;
        }
        if (tahomaBold12 != null) {
            tahomaBold12.destroy();
            tahomaBold12 = null;
        }
        if (tahomaBold16 != null) {
            tahomaBold16.destroy();
            tahomaBold16 = null;
        }
        if (tahomaBold20 != null) {
            tahomaBold20.destroy();
            tahomaBold20 = null;
        }
        if (noti20 != null) {
            noti20.destroy();
            noti20 = null;
        }
        if (nunitoBold20 != null) {
            nunitoBold20.destroy();
            nunitoBold20 = null;
        }
        if (nunitoBold48 != null) {
            nunitoBold48.destroy();
            nunitoBold48 = null;
        }
        if (nunitoBold80 != null) {
            nunitoBold80.destroy();
            nunitoBold80 = null;
        }
        if (harmonyOS_Sans20 != null) {
            harmonyOS_Sans20.destroy();
            harmonyOS_Sans20 = null;
        }
    }

    public static float getStringWidth(FontRenderer font, String text) {
        return (float) font.getStringWidth(text);
    }

    public static float getHeight(FontRenderer font) {
        return (float) font.getHeight();
    }

    public static MinecraftFontRenderer getMinecraft() {
        return MinecraftFontRenderer.INSTANCE;
    }
}
