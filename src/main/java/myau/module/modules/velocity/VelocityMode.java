package myau.module.modules.velocity;

import myau.event.impl.AttackEvent;
import myau.event.impl.JumpEvent;
import myau.event.impl.KnockbackEvent;
import myau.event.impl.LivingUpdateEvent;
import myau.event.impl.MoveInputEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.Render3DEvent;
import myau.event.impl.StrafeEvent;
import myau.event.impl.UpdateEvent;
// HitSlowDownEvent may not exist, removed if not needed
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

  public void onHitSlowDown(HitSlowDownEvent event) {}
}
