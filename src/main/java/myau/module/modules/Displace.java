package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

/**
 * Port of OpenB4 DisplaceModule – knockback sideways displacement.
 * Modes: LEFT / RIGHT / RANDOM / VOID
 */
public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ===== Settings (對應原版) =====
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"LEFT", "RIGHT", "RANDOM", "VOID"});
    public final FloatProperty angle = new FloatProperty("angle", 45.0F, 5.0F, 90.0F);
    public final FloatProperty cooldown = new FloatProperty("cooldown", 0.5F, 0.0F, 5.0F);
    public final BooleanProperty hideRotations = new BooleanProperty("hide-rotations", true);
    public final BooleanProperty onlyKnockbackItems = new BooleanProperty("only-knockback-items", false);
    public final BooleanProperty overrideAttacks = new BooleanProperty("override-attacks", false);
    public final BooleanProperty playersOnly = new BooleanProperty("players-only", true);
    public final BooleanProperty renderArrow = new BooleanProperty("render-arrow", true);
    public final BooleanProperty manual = new BooleanProperty("manual", true);
    public final BooleanProperty withAutoClicker = new BooleanProperty("with-autoclicker", true);   // 原版 autoClicker
    public final BooleanProperty withKillAura = new BooleanProperty("with-killaura", true);       // 原版 killAura

    // ===== State (對應原版 static 欄位) =====
    private boolean rotate;
    private boolean fromAimAssist;
    private boolean queued;
    private long lastAt;
    private boolean holding;
    private int holdTicks;
    private int blinkTicks;
    private int lastSide = -1;
    private int side = -1;
    private EntityLivingBase lastTarget;
    private boolean arrow;
    private long arrowAt;
    private double arrowX, arrowY, arrowZ, dirX, dirZ;

    public Displace() {
        super("Displace", "Displace targets sideways with knockback", Category.COMBAT, 0, false, false);
    }

    // ===== 原版 offset() =====
    private float offset() {
        float f = this.angle.getValue();
        if (this.side != -1) {
            if (this.side == 0) {
                f = -f;
            }
            this.side = -1;
        } else if (this.mode.getValue() == 0 /* LEFT */
                || (this.mode.getValue() == 2 /* RANDOM */ && RandomUtil.nextInt(0, 1) == 0)) {
            f = -f;
        }
        // 原版 MathUtils.randomFloat(-5, 5)
        f += RandomUtil.nextFloat(-5.0F, 5.0F);
        if (this.renderArrow.getValue() && this.lastTarget != null) {
            this.setArrow(this.lastTarget, f);
        }
        return f;
    }

    // ===== 原版 startBlink() =====
    private void startBlink() {
        if (this.hideRotations.getValue()) {
            this.blinkTicks = 2;
            Myau.blinkManager.setBlinkState(true, BlinkModules.DISPLACE);
        }
    }

    // ===== 原版 rearm() =====
    public void rearm() {
        this.holdTicks = 3;
    }

    // ===== 原版 clear() =====
    public void clear() {
        this.lastAt = 0L;
    }

    // ===== 原版 canDisplace() =====
    public boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) entity;

        // 原版用 SettingsModule.hittableHurtTime，這裡先用 hurtTime > 0 簡化
        // 之後若要完全一致可再加 IntProperty
        if (checkHurtTime && living.hurtTime > 0) {
            return false;
        }
        if (this.playersOnly.getValue() && !(entity instanceof EntityPlayer)) {
            return false;
        }

        int kb = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (!mc.thePlayer.isSprinting() && kb == 0) {
            return false;
        }
        if (this.onlyKnockbackItems.getValue() && kb == 0) {
            return false;
        }
        if (checkCooldown && Math.abs(System.currentTimeMillis() - this.lastAt) < this.cooldown.getValue() * 1000.0 - 20.0) {
            return false;
        }

        // VOID mode
        if (this.mode.getValue() == 3) {
            this.side = this.lastSide = this.dropSide(entity, this.lastSide);
            if (this.side == -1) {
                return false;
            }
        }

        this.lastTarget = living;
        return true;
    }

    /**
     * 原版 WorldUtils.dropSide 的近似實作。
     * 回傳 0 = 左、1 = 右、-1 = 兩邊都不適合。
     */
    private int dropSide(Entity entity, int prefer) {
        double x = entity.posX;
        double y = entity.posY;
        double z = entity.posZ;
        float yaw = mc.thePlayer.rotationYaw;
        double rad = Math.toRadians(yaw);

        // 左右側偏移（與原版精神相同）
        double leftX = x + Math.cos(rad) * 1.5;
        double leftZ = z + Math.sin(rad) * 1.5;
        double rightX = x - Math.cos(rad) * 1.5;
        double rightZ = z - Math.sin(rad) * 1.5;

        boolean leftVoid = isVoidNear(leftX, y, leftZ);
        boolean rightVoid = isVoidNear(rightX, y, rightZ);

        if (leftVoid && rightVoid) {
            if (prefer == 0 || prefer == 1) {
                return prefer;
            }
            return RandomUtil.nextInt(0, 1);
        }
        if (leftVoid) return 0;
        if (rightVoid) return 1;
        return -1;
    }

    private boolean isVoidNear(double x, double y, double z) {
        for (int i = 0; i < 8; i++) {
            if (mc.theWorld.getBlockState(new BlockPos(x, y - i, z)).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    // ===== 原版 trigger() =====
    public void trigger(int source) {
        if (source == 0) {
            this.rotate = true;
        } else if (source == 1) {
            this.fromAimAssist = true;
            this.rotate = true;
        } else if (source == 2) {
            this.queued = true;
        }
        this.lastAt = System.currentTimeMillis();
    }

    // ===== 原版 onMouse =====
    @EventTarget
    public void onMouse(MouseEvent e) {
        if (!this.isEnabled()) return;
        if (e.getButton() != 0 || !e.isButtonstate()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        // 原版：!autoClicker.isToggled() && AutoClickerModule.clicking() → return
        AutoClicker ac = (AutoClicker) Myau.moduleManager.modules.get(AutoClicker.class);
        if (!this.withAutoClicker.getValue() && ac != null && ac.isEnabled()) {
            return;
        }
        if (!this.manual.getValue()) {
            return;
        }

        Entity entity = null;
        boolean aim = false;

        // 原版優先 AimAssist silent target，目前 AIMyau AimAssist 沒公開 target，
        // 先走 objectMouseOver；之後可再接 AimAssist
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            entity = mc.objectMouseOver.entityHit;
        }

        if (entity == null) return;

        if (this.canDisplace(entity, true, true)) {
            e.setCancelled(true);
            this.trigger(aim ? 1 : 0);
        } else if (this.overrideAttacks.getValue() && this.canDisplace(entity, false, false)) {
            e.setCancelled(true);
        }
    }

    // ===== 原版 getRotations 的效果 → 用 UpdateEvent 實作 =====
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.rotate || this.queued) {
            float off = this.offset();
            float yaw = mc.thePlayer.rotationYaw + off;
            float pitch = mc.thePlayer.rotationPitch;

            // priority 給高一點，避免被其他旋轉蓋掉
            event.setRotation(yaw, pitch, 8);
            event.setPervRotation(yaw, 8);

            this.startBlink();

            // 原版在 getRotations 裡會 KeyUtils.click(attack)
            // 這裡因為我們 cancel 了 mouse，攻擊會在下一個正常流程發生；
            // 若需要強制 click 可再接 KeyBindUtil

            this.rotate = false;
            this.queued = false;
        }
    }

    // ===== 原版 onPreUpdate =====
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        // blink 結束
        if (this.blinkTicks > 0 && --this.blinkTicks == 0) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
        }

        // 原版：killAura.isToggled() && KillAuraModule.target != null && canDisplace → trigger(2)
        if (this.withKillAura.getValue()) {
            KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            if (ka != null && ka.isEnabled()) {
                EntityLivingBase target = ka.getTarget();
                if (target != null && this.canDisplace(target, true, true)) {
                    this.trigger(2);
                }
            }
        }

        // holdTicks
        if (this.holdTicks > 0) {
            this.holding = --this.holdTicks > 0;
        }
    }

    // ===== 原版 onPostMotion 效果（清旗標）=====
    @EventTarget
    public void onUpdatePost(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            this.rotate = false;
            this.queued = false;
        }
    }

    // ===== 原版 onRenderWorld（箭頭渲染）=====
    @EventTarget
    public void onRender3D(Render3DEvent e) {
        if (!this.arrow || !this.renderArrow.getValue()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        long l = System.currentTimeMillis() - this.arrowAt;
        if (l > 600L) {
            this.arrow = false;
            return;
        }

        double d2 = (double) l / 100.0;
        d2 = d2 < 0.0 ? 0.0 : Math.min(d2, 1.0);
        double d = d2 * d2 * (3.0 - 2.0 * d2);
        double alpha = 0.08 + 0.92 * d;

        if (l > 500L) {
            d2 = (double) (l - 500L) / 100.0;
            d2 = d2 < 0.0 ? 0.0 : Math.min(d2, 1.0);
            d = d2 * d2 * (3.0 - 2.0 * d2);
            alpha *= 1.0 - 0.92 * d;
        }

        d2 = (double) l / 450.0;
        d2 = d2 < 0.0 ? 0.0 : Math.min(d2, 1.0);
        d = 1.0 - d2;
        d2 = 1.0 - d * d * d;
        d = Math.sin((double) l * 0.015) * 0.02 * (0.4 + 0.6 * alpha);
        double dist = 0.8 * d2;

        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        double x = this.arrowX - vx + this.dirX * dist;
        double y = this.arrowY - vy + d;
        double z = this.arrowZ - vz + this.dirZ * dist;
        double yaw = Math.toDegrees(Math.atan2(-this.dirZ, this.dirX));

        int fill = (int) (225.0 * alpha) << 24 | 0xFFFFFF;
        int outline = (int) (255.0 * alpha) << 24 | 0xF6F6F6;
        int shadow = (int) (140.0 * alpha) << 24 | 0x5081A;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate((float) yaw, 0.0F, 1.0F, 0.0F);

        // shadow
        GL11.glPushMatrix();
        GL11.glTranslated(0.08 * 0.48, -0.08 * 0.24, -0.02);
        color(shadow);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-0.2784 * 1.01, -0.0816 * 1.01, 0.0);
        GL11.glVertex3d(0.0 * 1.01, -0.0288 * 1.01, 0.0);
        GL11.glVertex3d(0.0 * 1.01, 0.0288 * 1.01, 0.0);
        GL11.glVertex3d(-0.2784 * 1.01, 0.0816 * 1.01, 0.0);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(0.0, -0.144 * 1.01, 0.0);
        GL11.glVertex3d(0.288 * 1.01, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.144 * 1.01, 0.0);
        GL11.glEnd();
        GL11.glPopMatrix();

        // fill
        color(fill);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-0.2784, -0.0816, 0.0);
        GL11.glVertex3d(0.0, -0.0288, 0.0);
        GL11.glVertex3d(0.0, 0.0288, 0.0);
        GL11.glVertex3d(-0.2784, 0.0816, 0.0);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(0.0, -0.144, 0.0);
        GL11.glVertex3d(0.288, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.144, 0.0);
        GL11.glEnd();

        // outline
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        color(outline);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(-0.2784, -0.0816, 0.0);
        GL11.glVertex3d(0.0, -0.0288, 0.0);
        GL11.glVertex3d(0.0, 0.0288, 0.0);
        GL11.glVertex3d(-0.2784, 0.0816, 0.0);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(0.0, -0.144, 0.0);
        GL11.glVertex3d(0.288, 0.0, 0.0);
        GL11.glVertex3d(0.0, 0.144, 0.0);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void color(int c) {
        GlStateManager.color(
                (float) (c >> 16 & 0xFF) / 255.0F,
                (float) (c >> 8 & 0xFF) / 255.0F,
                (float) (c & 0xFF) / 255.0F,
                (float) (c >> 24 & 0xFF) / 255.0F
        );
    }

    // ===== 原版 setArrow =====
    private void setArrow(EntityLivingBase entity, float f) {
        double d = Math.toRadians(mc.thePlayer.rotationYaw + f);
        this.dirX = -Math.sin(d);
        this.dirZ = Math.cos(d);
        this.arrowX = entity.posX + this.dirX * 0.8;
        this.arrowY = entity.posY + 1.2;
        this.arrowZ = entity.posZ + this.dirZ * 0.8;
        this.arrowAt = System.currentTimeMillis();
        this.arrow = true;
    }

    // ===== 原版 onDisable =====
    @Override
    public void onDisabled() {
        if (this.blinkTicks > 0) {
            this.blinkTicks = 0;
            Myau.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
        }
        this.holding = false;
        this.arrow = false;
        this.rotate = false;
        this.fromAimAssist = false;
        this.queued = false;
        this.holdTicks = 0;
        this.lastSide = -1;
        this.side = -1;
    }

    // ===== 原版 getInfo() =====
    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
