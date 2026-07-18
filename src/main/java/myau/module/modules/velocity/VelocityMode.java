package myau.module.modules.velocity;

import myau.events.*;   // 正確 import（AIMyau 使用 myau.events）
import myau.module.modules.Velocity;
import net.minecraft.client.Minecraft;

public abstract class VelocityMode {
  protected final String name;
  protected final Velocity parent;
  protected static final Minecraft mc = Minecraft.getMinecraft();

  public VelocityMode(String name, Velocity parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public Velocity getParent() {
    return parent;
  }

  public void onEnable() {}
  public void onDisable() {}
  public void onUpdate(UpdateEvent event) {}
  public void onPacket(PacketEvent event) {}
  public void onKnockback(KnockbackEvent event) {}
  public void onLivingUpdate(LivingUpdateEvent event) {}
  public void onMoveInput(MoveInputEvent event) {}
  public void onAttack(AttackEvent event) {}
  public void onStrafe(StrafeEvent event) {}
  public void onJump(JumpEvent event) {}
  public void onRender3D(Render3DEvent event) {}
}
