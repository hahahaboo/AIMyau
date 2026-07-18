package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.*;
import myau.module.Category;
import myau.module.Module;
import myau.module.modules.velocity.VelocityMode;
import myau.module.modules.velocity.VanillaVelocity;
import myau.module.modules.velocity.DelayVelocity;
import myau.module.modules.velocity.ReverseVelocity;
import myau.property.Property;
import myau.property.properties.ModeProperty;
import java.util.ArrayList;
import java.util.List;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public int chanceCounter = 0;
    public int delayChanceCounter = 0;
    public boolean pendingExplosion = false;
    public boolean allowNext = true;
    public boolean jumpFlag = false;
    public boolean reverseFlag = false;
    public boolean delayActive = false;

    public boolean shouldJump = false;
    public int jumpCooldown = 0;
    public boolean hasReceivedVelocity = false;
    public int legitSmartJumpCount = 0;
    public int intaveTick = 0;
    public int intaveDamageTick = 0;

    public final List<VelocityMode> modes = new ArrayList<>();

    public final ModeProperty mode =
        new ModeProperty(
            "mode",
            0,
            new String[] {
              register(new VanillaVelocity("VANILLA", this)),
              register(new DelayVelocity("DELAY", this)),
              register(new ReverseVelocity("REVERSE", this))
            });

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private String register(VelocityMode m) {
      this.modes.add(m);
      return m.getName();
    }

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.COMBAT, 0, false, false);
    }

    @Override
    public void onEnabled() {
        getActiveMode().onEnable();
    }

    @Override
    public void onDisabled() {
        getActiveMode().onDisable();
        this.pendingExplosion = false;
        this.allowNext = true;
        this.shouldJump = false;
        this.jumpCooldown = 0;
        this.hasReceivedVelocity = false;
        this.legitSmartJumpCount = 0;
        this.intaveTick = 0;
        this.intaveDamageTick = 0;
        this.reverseFlag = false;
        this.delayActive = false;
    }

    @Override
    public List<myau.property.Property<?>> getAdditionalProperties() {
      List<myau.property.Property<?>> props = new ArrayList<>();
      for (VelocityMode m : modes) {
        for (java.lang.reflect.Field field : m.getClass().getDeclaredFields()) {
          field.setAccessible(true);
          try {
            Object obj = field.get(m);
            if (obj instanceof myau.property.Property<?>) {
              myau.property.Property<?> prop = (myau.property.Property<?>) obj;
              java.util.function.BooleanSupplier original = prop.getVisibleChecker();
              prop.setVisibleChecker(
                  () -> this.getActiveMode() == m && (original == null || original.getAsBoolean()));
              props.add(prop);
            }
          } catch (Exception e) {
          }
        }
      }
      return props;
    }

    public VelocityMode getActiveMode() {
      return modes.stream()
          .filter(m -> m.getName().equals(mode.getModeString()))
          .findFirst()
          .orElse(modes.get(0));
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled()) {
            getActiveMode().onKnockback(event);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled()) {
            getActiveMode().onUpdate(event);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            getActiveMode().onPacket(event);
        }
    }

        @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}
