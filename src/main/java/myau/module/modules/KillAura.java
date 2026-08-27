package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private final TimerUtil timer = new TimerUtil();
    private AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private int hypixel3Asw = 0;
    private boolean blinkReset = false;
    private long attackDelayMS = 0L;
    private int blockTick = 0;
    private int lastTickProcessed;
    private int lagNewPreStage = 0;
    private boolean lagNewPrePrepare = false;
    private long lagNewPreTimer = 0L;
    private int smartUnblockTicksLeft = 0;
    private int lagNewPreSlot = -1;
    public final ModeProperty mode;
    public final ModeProperty sort;
    public final ModeProperty autoBlock;
    public final BooleanProperty autoBlockRequirePress;
    public final FloatProperty autoBlockMinCPS;
    public final FloatProperty autoBlockMaxCPS;
    public final BooleanProperty smartUnblock;
    public final IntProperty smartUnblockTicks;
    public final PercentProperty smartUnblockChance;
    public final ModeProperty lagNewPreAps;
    public final ModeProperty lagNewPreUnblock;
    public final FloatProperty autoBlockRange;
    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final ModeProperty showTarget;

    private long getAttackDelay() {
        return this.isBlocking ? (long) (1000.0F / RandomUtil.nextLong(this.autoBlockMinCPS.getValue().longValue(), this.autoBlockMaxCPS.getValue().longValue())) : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                        && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
                    AttackEvent event = new AttackEvent(this.target.getEntity());
                    EventManager.call(event);
                    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                    PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                        PlayerUtil.attackEntity(this.target.getEntity());
                    }
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                this.target.getEntity(),
                                new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            
        } else {
            return false;
        }
    }
    
        private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }
    
    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && RotationUtil.rayTrace(entityLivingBase) != null) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && !stack.hasDisplayName()) {
                    return i;
                }
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    private int findSwordSlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
                if (item != null && item.getItem() instanceof ItemSword) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 對應 Expo 的 o$r2()：重置計時器 */
    private void lagNewPreResetTimer() {
        this.lagNewPreTimer = System.currentTimeMillis();
    }

    /** 對應 Expo 的 W()：準備標記 */
    private void lagNewPreMarkPrepare() {
        this.lagNewPrePrepare = true;
    }

    /** 對應 Expo 的解除（優先切換物品） */
    private void lagNewPreUnblock() {
        if (!this.isPlayerBlocking()) return;

        if (this.lagNewPreUnblock.getValue() == 1) { // SWAP
            int current = mc.thePlayer.inventory.currentItem;
            int empty = this.findEmptySlot(current); // 你原本 INTERACT 已有類似方法
            if (empty != -1) {
                this.lagNewPreSlot = current;
                PacketUtil.sendPacket(new C09PacketHeldItemChange(empty));
                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(empty);
                this.blockingState = false;
            } else {
                this.stopBlock(); // 找不到空位就退回 C07
            }
        } else {
            this.stopBlock();
        }
    }

    /** 對應 Expo 的重新格擋 Q() */
    private void lagNewPreReblock(float yaw, float pitch, boolean withInteract) {
        // 先切回原本欄位（如果有用 SWAP 解除）
        if (this.lagNewPreSlot != -1) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(this.lagNewPreSlot));
            ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(this.lagNewPreSlot);
            this.lagNewPreSlot = -1;
        }

        if (withInteract && this.target != null) {
            this.interactAttack(yaw, pitch);
        } else {
            this.sendUseItem();
        }
    }

    /** 根據 APS 模式決定要不要在這個 tick 重新格擋 */
    private boolean lagNewPreShouldReblock() {
        // 簡化對應 Expo 不同 APS 的時序差異
        switch (this.lagNewPreAps.getValue()) {
            case 0: // 3APS
            case 1: // 5APS
                return this.lagNewPreStage == 9;
            case 2: // 7APS
                return this.lagNewPreStage == 9 || this.lagNewPreStage == 8;
            case 3: // 10APS
            case 4: // 14APS
                return true;
            default:
                return this.lagNewPreStage == 9;
        }
    }

    public KillAura() {
        super("KillAura", "Attacks entities around you", Category.COMBAT, 0, false, false);
        this.lastTickProcessed = 0;
        this.mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
        this.sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
        this.autoBlock = new ModeProperty(
                "auto-block", 1, new String[]{"NONE", "VANILLA", "INTERACT", "LEGIT", "FAKE", "Morden", "expo"}
        );
        this.smartUnblock = new BooleanProperty("smart-unblock", false);
        this.smartUnblockTicks = new IntProperty("smart-unblock-ticks", 8, 0, 15);
        this.smartUnblockChance = new PercentProperty("smart-unblock-chance", 100);
        this.lagNewPreAps = new ModeProperty(
            "lag-new-pre-aps", 0, new String[]{"3APS", "5APS", "7APS", "10APS", "14APS"}
        );
        this.lagNewPreUnblock = new ModeProperty(
            "lag-new-pre-unblock", 1, new String[]{"RELEASE", "SWAP"}
        );
        this.autoBlockRequirePress = new BooleanProperty("auto-block-require-press", false);
        this.autoBlockMinCPS = new FloatProperty("auto-block-min-aps", 8.0F, 1.0F, 20.0F);
        this.autoBlockMaxCPS = new FloatProperty("auto-block-max-aps", 10.0F, 1.0F, 20.0F);
        this.autoBlockRange = new FloatProperty("auto-block-range", 6.0F, 1.0F, 8.0F);
        this.swingRange = new FloatProperty("swing-range", 4.0F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("fov", 360, 30, 360);
        this.minCPS = new IntProperty("min-aps", 12, 1, 20);
        this.maxCPS = new IntProperty("max-aps", 14, 1, 20);
        this.switchDelay = new IntProperty("switch-delay", 0, 0, 1000);
        this.rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT", "LOCK_VIEW"});
        this.moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
        this.smoothing = new PercentProperty("smoothing", 0);
        this.angleStep = new IntProperty("angle-step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("through-walls", true);
        this.requirePress = new BooleanProperty("require-press", false);
        this.allowMining = new BooleanProperty("allow-mining", true);
        this.weaponsOnly = new BooleanProperty("weapons-only", false);
        this.allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("inventory-check", true);
        this.botCheck = new BooleanProperty("bot-check", true);
        this.players = new BooleanProperty("players", true);
        this.bosses = new BooleanProperty("bosses", false);
        this.mobs = new BooleanProperty("mobs", false);
        this.animals = new BooleanProperty("animals", false);
        this.golems = new BooleanProperty("golems", false);
        this.silverfish = new BooleanProperty("silverfish", false);
        this.teams = new BooleanProperty("teams", true);
        this.showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
    }

    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == 2  // INTERACT
                    || this.autoBlock.getValue() == 3); // LEGIT
        } else {
            return false;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST && this.blinkReset) {
            this.blinkReset = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = this.target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
                this.hypixel3Asw = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0: // NONE
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1: // VANILLA
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    swap = true;
                                }
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2: // INTERACT
                            if (this.hasValidTarget()) {
                                int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                                if (mc.thePlayer.inventory.currentItem == item && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blinkReset = true;
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                int slot = this.findEmptySlot(item);
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                                                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 3: // LEGIT
                            if (this.hasValidTarget()) {
                                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 4: // FAKE
                            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !Myau.playerStateManager.digging
                                    && !Myau.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                        case 5:
                            // Morden / Hypixel3 (ported from Cryptix KillAura): 3-tick blink-batched block -> attack -> block cycle
                            if (this.hasValidTarget()) {
                                Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                    switch (this.hypixel3Asw) {
                                        case 0:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.hypixel3Asw = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.hypixel3Asw = 2;
                                            break;
                                        case 2:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            blocked = true;
                                            this.hypixel3Asw = 0;
                                            break;
                                        default:
                                            this.hypixel3Asw = 0;
                                    }
                                } else {
                                    attack = false;
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                                this.hypixel3Asw = 0;
                            }
                            break;
                        case 6: // LAG_NEW_PRE
                        if (this.smartUnblockTicksLeft > 0) {
                            // Smart Unblock 強制解除中
                            this.lagNewPreUnblock();
                            this.smartUnblockTicksLeft--;
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            attack = false;
                            break;
                        }

                        if (this.hasValidTarget() && this.canAutoBlock()) {
                            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);

                            // 準備標記處理（對應 Expo J）
                            if (this.lagNewPrePrepare) {
                                this.lagNewPreResetTimer();
                                this.lagNewPrePrepare = false;
                                this.lagNewPreStage = 0;
                            }

                            if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                                switch (this.lagNewPreStage) {
                                    case 0:
                                    case 8:
                                        // PRE：攻擊當下先解除 + 標記準備
                                        this.lagNewPreUnblock();
                                        this.lagNewPreResetTimer();
                                        this.lagNewPreMarkPrepare();
                                        attack = false;                 // 這 tick 先不打
                                        this.lagNewPreStage = 9;
                                        break;

                                    case 9:
                                        // 重新格擋階段
                                        if (this.lagNewPreShouldReblock()) {
                                            // 讓後面的 swap / blocked 邏輯去發格擋封包
                                            swap = true;
                                            blocked = true;
                                        }
                                        this.lagNewPreStage = 0;
                                        break;

                                    default:
                                        this.lagNewPreStage = 0;
                                }
                            } else {
                                attack = false;
                            }

                            this.isBlocking = true;
                            this.fakeBlockState = true;
                        } else {
                            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = false;
                            this.lagNewPreStage = 0;
                            this.lagNewPrePrepare = false;
                            this.lagNewPreSlot = -1;
                        }
                        break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.target.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                        float[] rotations = RotationUtil.getRotationsToBox(
                                this.target.getBox(),
                                event.getYaw(),
                                event.getPitch(),
                                (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                                (float) this.smoothing.getValue() / 100.0F
                        );
                        event.setRotation(rotations[0], rotations[1], 1);
                        if (this.rotations.getValue() == 3) {
                            Myau.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                        }
                        if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                            event.setPervRotation(rotations[0], 1);
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        this.sendUseItem();
                    }
                }
                if (blocked) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.target == null
                            || !this.isValidTarget(this.target.getEntity())
                            || !this.isBoxInAttackRange(this.target.getBox())
                            || !this.isBoxInSwingRange(this.target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            this.target = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            this.target = new AttackData(targets.get(this.switchTick));
                        }
                    }
                    if (this.target != null) {
                        this.target = new AttackData(this.target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null && mc.theWorld != null) {
            // ===== 原本 SEND 邏輯 =====
            if (event.getType() == EventType.SEND) {
                if (event.getPacket() instanceof C07PacketPlayerDigging) {
                    C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                    if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                        this.blockingState = false;
                    }
                }
                if (event.getPacket() instanceof C09PacketHeldItemChange) {
                    this.blockingState = false;
                    if (this.isBlocking) {
                        mc.thePlayer.stopUsingItem();
                    }
                }
            }

            // ===== LAG_NEW_PRE Smart Unblock（RECEIVE）=====
            if (event.getType() == EventType.RECEIVE
                    && this.smartUnblock.getValue()
                    && this.autoBlock.getValue() == 6
                    && event.getPacket() instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                if (packet.getEntity(mc.theWorld) == mc.thePlayer && packet.getOpCode() == 2) {
                    if (RandomUtil.nextInt(0, 100) <= this.smartUnblockChance.getValue()) {
                        this.smartUnblockTicksLeft = this.smartUnblockTicks.getValue();
                    }
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.shouldAutoBlock()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && target != null) {
            if (this.showTarget.getValue() != 0
                    && TeamUtil.isEntityLoaded(this.target.getEntity())
                    && this.isAttackAllowed()) {
                Color color = new Color(-1);
                switch (this.showTarget.getValue()) {
                    case 1:
                        if (this.target.getEntity().hurtTime > 0) {
                            color = new Color(16733525);
                        } else {
                            color = new Color(5635925);
                        }
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                RenderUtil.enableRenderState();
                RenderUtil.drawEntityBox(this.target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockTick = 0;
    }

    @Override
    public void onDisabled() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
    }

    @Override
    public void verifyValue(String value) {
        boolean badCps = this.autoBlock.getValue() == 2  // INTERACT
                || this.autoBlock.getValue() == 3;        // LEGIT
        if (!this.autoBlock.getName().equals(value)) {
            if (this.swingRange.getName().equals(value)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(value)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(value)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else if (this.autoBlockMinCPS.getName().equals(value)) {
                if (this.autoBlockMinCPS.getValue() > this.autoBlockMaxCPS.getValue()) {
                    this.autoBlockMaxCPS.setValue(this.autoBlockMinCPS.getValue());
                }
                if(autoBlockMinCPS.getValue() > 10.0F && badCps){
                    autoBlockMinCPS.setValue(10.0F);
                }
            } else if (this.autoBlockMaxCPS.getName().equals(value)) {
                if (this.autoBlockMinCPS.getValue() > this.autoBlockMaxCPS.getValue()) {
                    this.autoBlockMinCPS.setValue(this.autoBlockMaxCPS.getValue());
                }
                if(autoBlockMaxCPS.getValue() > 10.0F && badCps){
                    autoBlockMaxCPS.setValue(10.0F);
                }
            } else {
                if (this.maxCPS.getName().equals(value) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        } else {
            if (badCps && (this.autoBlockMinCPS.getValue() > 10.0F || this.autoBlockMaxCPS.getValue() > 10.0F)) {
                this.autoBlockMinCPS.setValue(8.0F);
                this.autoBlockMaxCPS.setValue(10.0F);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
