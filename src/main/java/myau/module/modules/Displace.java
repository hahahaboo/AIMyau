package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LeftClickMouseEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

/**
 * Port of OpenB4 DisplaceModule – sideways knockback displacement.
 * Modes: LEFT / RIGHT / RANDOM / VOID
 */
public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // ===== Settings =====
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"LEFT", "RIGHT", "RANDOM", "VOID"});
    public final FloatProperty angle = new FloatProperty("angle", 45.0F, 5.0F, 90.0F);
    public final FloatProperty cooldown = new FloatProperty("cooldown", 0.5F, 0.0F, 5.0F);
    public final BooleanProperty hideRotations = new BooleanProperty("hide-rotations", false);
    public final BooleanProperty onlyKnockbackItems = new BooleanProperty("only-knockback-items", false);
    public final BooleanProperty playersOnly = new BooleanProperty("players-only", true);
    public final BooleanProperty renderArrow = new BooleanProperty("render-arrow", true);
    public final BooleanProperty manual = new BooleanProperty("manual", true);
    public final BooleanProperty killAura = new BooleanProperty("killaura", true);

    // ===== State =====
    private boolean rotate;
    private boolean fromAimAssist;
    private boolean queued;
    private boolean pendingClick;
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

    // ===== offset (原版) =====
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
        f += RandomUtil.nextFloat(-5.0F, 5.0F);
        if (this.renderArrow.getValue() && this.lastTarget != null) {
            this.setArrow(this.lastTarget, f);
        }
        return f;
    }

    private void startBlink() {
        if (this.hideRotations.getValue()) {
            this.blinkTicks = 2;
            Myau.blinkManager.setBlinkState(true, BlinkModules.DISPLACE);
        }
    }

    public void rearm() {
        this.holdTicks = 3;
    }

    public void clear() {
        this.lastAt = 0L;
    }

    // ===== canDisplace (原版過濾) =====
    public boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) entity;

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

        if (this.mode.getValue() == 3) { // VOID
            this.side = this.lastSide = PlayerUtil.dropSide(entity, this.lastSide);
            if (this.side == -1) {
                return false;
            }
        }

        this.lastTarget = living;
        return true;
    }

    public void trigger(int source) {
        if (source == 0) {
            this.rotate = true;
            this.pendingClick = true;
        } else if (source == 1) {
            this.fromAimAssist = true;
            this.rotate = true;
            this.pendingClick = true;
        } else if (source == 2) {
            this.queued = true;
        }
        this.lastAt = System.currentTimeMillis();
    }

    // ===== 手動：左鍵觸發 =====
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent e) {
        if (!this.isEnabled()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        if (!this.manual.getValue()) return;

        Entity entity = null;
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            entity = mc.objectMouseOver.entityHit;
        }
        if (entity == null) return;

        if (this.canDisplace(entity, true, true)) {
            e.setCancelled(true);
            this.trigger(0);
        }
    }

    // ===== 轉頭：預設 MEDIUM，早於 KillAura LOW（對齊 OpenB4 takeRotations）=====
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        // 手動
        if (this.rotate) {
            float off = this.offset();
            float yaw = mc.thePlayer.rotationYaw + off;
            float pitch = mc.thePlayer.rotationPitch;
            event.setRotation(yaw, pitch, 8);
            event.setPervRotation(yaw, 8);
            this.startBlink();
            this.rotate = false;
            return;
        }

        // KillAura：queued → offset + forceNextAttacks(1)
        if (this.queued) {
            this.queued = false;
            KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            EntityLivingBase target = ka != null ? ka.getTarget() : null;

            if (ka == null || !ka.isEnabled() || target == null) {
                this.clear();
                return;
            }

            float off = this.offset();
            float yaw = mc.thePlayer.rotationYaw + off;
            float pitch = mc.thePlayer.rotationPitch;
            event.setRotation(yaw, pitch, 8);
            event.setPervRotation(yaw, 8);
            this.startBlink();

            // 對齊 ++forcedAttacks；成功 performAttack 後才 --remaining
            ka.unsmoothRotationNextHit();
            ka.forceNextAttacks(1);
        }
    }

    @EventTarget
    public void onUpdatePost(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            this.rotate = false;
            this.queued = false;
        }
    }

    // ===== tick：blink / 手動補打 / KA 有 target 則 queued =====
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.blinkTicks > 0 && --this.blinkTicks == 0) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
        }

        // 手動：轉頭後補攻擊
        if (this.pendingClick) {
            this.pendingClick = false;
            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
        }

        // 對齊 OpenB4 onPreUpdate
        if (this.killAura.getValue()) {
            KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            if (ka != null && ka.isEnabled()) {
                EntityLivingBase target = ka.getTarget();
                if (target != null && this.canDisplace(target, true, true)) {
                    this.trigger(2);
                }
            }
        }

        if (this.holdTicks > 0) {
            this.holding = --this.holdTicks > 0;
        }
    }

    // ===== 箭頭渲染 =====
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
        this.pendingClick = false;
        this.holdTicks = 0;
        this.lastSide = -1;
        this.side = -1;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
