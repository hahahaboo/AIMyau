package myau.module.modules;

import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.IAccessorEntity;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.BlockUtil;
import myau.util.ChatUtil;
import myau.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int chanceCounter = 0;
    private int delayChanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean jumpFlag = false;
    private boolean reverseFlag = false;
    private boolean delayActive = false;

    // === PLACE BLOCK MODE 專用變數（僅此 mode 使用）===
    private boolean placingBlocks = false;
    private int blocksPlaced = 0;
    private final BlockPos[] placePositions = new BlockPos[2];
    private int originalSlot = -1;
    private boolean kaWasEnabled = false;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "JUMP", "DELAY", "REVERSE", "BLOCK"});
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, () -> this.mode.getValue() == 2);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, () -> this.mode.getValue() == 2);
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> this.mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                return i;
            }
        }
        return -1;
    }

    private void placeBlock(BlockPos pos) {
        if (pos == null || !BlockUtil.isReplaceable(pos)) return;

        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return;

        EnumFacing facing = null;
        BlockPos playerFeet = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        for (EnumFacing f : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(f);
            if (!BlockUtil.isReplaceable(neighbor) && !BlockUtil.isContainer(neighbor)) {
                facing = f.getOpposite();
                break;
            }
        }
        if (facing == null) facing = EnumFacing.UP;

        Vec3 hitVec = BlockUtil.getClickVec(pos, facing);

        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(
                pos, facing.getIndex(), stack,
                (float) hitVec.xCoord, (float) hitVec.yCoord, (float) hitVec.zCoord
        ));

        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
    }

    private void startBlockPlacement(double kbX, double kbZ) {
        if (Math.abs(kbX) < 0.5 && Math.abs(kbZ) < 0.5) return;

        Vec3 dir = new Vec3(kbX, 0, kbZ).normalize();
        int ox = (int) Math.signum(dir.xCoord);
        int oz = (int) Math.signum(dir.zCoord);
        if (ox == 0 && oz == 0) return;

        BlockPos playerFeet = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        BlockPos lower = playerFeet.add(ox, 0, oz);

        if (!BlockUtil.isReplaceable(lower)) return;
        BlockPos upper = lower.up();
        if (!BlockUtil.isReplaceable(upper)) return;

        this.placePositions[0] = lower;
        this.placePositions[1] = upper;
        this.blocksPlaced = 0;
        this.placingBlocks = true;

        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        this.kaWasEnabled = ka.isEnabled();
        if (this.kaWasEnabled) ka.setEnabled(false);

        this.originalSlot = mc.thePlayer.inventory.currentItem;
        int blockSlot = this.getBlockSlot();
        if (blockSlot != -1 && blockSlot != this.originalSlot) {
            mc.thePlayer.inventory.currentItem = blockSlot;
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }

        ChatUtil.sendFormatted(String.format(
                "%s §e[BLOCK MODE] KB triggered! (kbX: %.2f, kbZ: %.2f) → placing at %s",
                Myau.clientName, kbX, kbZ, lower
        ));
    }

    public Velocity() {
        super("Velocity", "Reduces knockback", Category.COMBAT, 0, false, false);
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }

        if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
            this.allowNext = true;

            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.mode.getValue() == 0) {
                    if (this.explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            } else {
                if (this.mode.getValue() == 0) {
                    this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                    if (this.chanceCounter >= 100) {
                        if (this.horizontal.getValue() > 0) {
                            event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (this.vertical.getValue() > 0) {
                            event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                } else {
                    this.jumpFlag = (this.mode.getValue() == 1) && event.getY() > 0.0;
                    this.delayActive = this.mode.getValue() == 3;
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            if (this.reverseFlag && (
                    this.canDelay() ||
                            this.isInLiquidOrWeb() ||
                            Myau.delayManager.getDelay() >= (long) this.delayTicks.getValue()
            )) {
                Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.reverseFlag = false;
            }

            if (this.delayActive) {
                MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                this.delayActive = false;
            }

            if (this.placingBlocks) {
                if (this.blocksPlaced < 2) {
                    BlockPos pos = this.placePositions[this.blocksPlaced];
                    if (pos != null && BlockUtil.isReplaceable(pos)) {
                        this.placeBlock(pos);
                        this.blocksPlaced++;
                    } else {
                        this.placingBlocks = false;
                    }
                }
                if (this.blocksPlaced >= 2) {
                    this.placingBlocks = false;

                    if (this.originalSlot != -1 && this.originalSlot != mc.thePlayer.inventory.currentItem) {
                        mc.thePlayer.inventory.currentItem = this.originalSlot;
                        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                    }

                    if (this.kaWasEnabled) {
                        KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                        if (!ka.isEnabled()) ka.setEnabled(true);
                    }
                    this.kaWasEnabled = false;
                    this.originalSlot = -1;
                    this.placePositions[0] = null;
                    this.placePositions[1] = null;
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {

                // === BLOCK mode 觸發點（改到這裡，因為 onKnockback 完全沒執行）===
                if (this.mode.getValue() == 4) {
                    double kbX = (double) packet.getMotionX() / 8000.0;
                    double kbZ = (double) packet.getMotionZ() / 8000.0;
                    this.startBlockPlacement(kbX, kbZ);
                }

                LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);

                if (this.mode.getValue() == 2
                        && !this.reverseFlag
                        && !this.canDelay()
                        && !this.isInLiquidOrWeb()
                        && !this.pendingExplosion
                        && (!this.allowNext || !(Boolean) this.fakeCheck.getValue())
                        && (!longJump.isEnabled() || !longJump.canStartJump())) {

                    this.delayChanceCounter = this.delayChanceCounter % 100 + this.delayChance.getValue();
                    if (this.delayChanceCounter >= 100) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        this.reverseFlag = true;
                        return;
                    }
                }

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format(
                                    "%sVelocity (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    (double) packet.getMotionX() / 8000.0,
                                    (double) packet.getMotionY() / 8000.0,
                                    (double) packet.getMotionZ() / 8000.0
                            )
                    );
                }
            }
        } 
        else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                if (this.mode.getValue() == 0) {
                    this.pendingExplosion = true;
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }

                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format(
                                    "%sExplosion (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    mc.thePlayer.motionX + (double) packet.func_149149_c(),
                                    mc.thePlayer.motionY + (double) packet.func_149144_d(),
                                    mc.thePlayer.motionZ + (double) packet.func_149147_e()
                            )
                    );
                }
            }
        } 
        else if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                this.allowNext = false;
            }
        }
    }
}
