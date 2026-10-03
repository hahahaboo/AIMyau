package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.Module;
import myau.module.Category;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125,
            0.09375,
            0.15625,
            0.21875,
            0.28125,
            0.34375,
            0.40625,
            0.46875,
            0.53125,
            0.59375,
            0.65625,
            0.71875,
            0.78125,
            0.84375,
            0.90625,
            0.96875
    };
    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private EnumFacing targetFacing = null;
    private int safeStuckTicks = 0;
    private int safeStuckDelayTicks = 0;
    private double safePrevMotionY = 0.0;
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private boolean legitWasOnGround = true;
    private int legitDelayTicks = 0;
    private int legitReleaseTicks = 0;
    private boolean legitActive = false;
    private boolean placedThisTick = false;
    private boolean safeStuckActive = false;
    private boolean snapRotating = false;
    private float lastSnapPlaceYaw = Float.NaN;
    private float lastSnapPlacePitch = Float.NaN;
    // GRIM mode: forward-45 (sprint) <-> back-45 (place) cycle
    private BlockData grimTarget = null;
    private Vec3 grimHitVec = null;
    private int grimPlaceDelayCounter = 0;
    /** 0 = forward 45 (sprint), 1 = back 45 (aim + place) */
    private int grimPhase = 0;
    private int grimPhaseTicks = 0;
    public final ModeProperty rotationMode = new ModeProperty("rotations", 1, new String[]{"None", "Default", "Smooth", "Backwards", "Sideways", "Hypixel", "Snap", "GRIM"});
        public final FloatProperty tellystartrotationminspeed = new FloatProperty("start-min-speed", 90.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
        public final FloatProperty tellystartrotationmaxspeed = new FloatProperty("start-max-speed", 95.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
        public final FloatProperty tellynormalrotationminspeed = new FloatProperty("normal-min-speed", 30.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
        public final FloatProperty tellynormalrotationmaxspeed = new FloatProperty("normal-max-speed", 35.0F, 1.0F, 180.0F, () -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
        public final IntProperty snapDelay = new IntProperty("snap-delay", 1, 0, 2, () -> this.rotationMode.getValue() == 6);
        public final IntProperty grimPlaceDelay = new IntProperty("grim-place-delay", 0, 0, 5, () -> this.rotationMode.getValue() == 7);
        public final BooleanProperty grimDiagonal = new BooleanProperty("grim-diagonal", true, () -> this.rotationMode.getValue() == 7);
        public final IntProperty grimForwardTicks = new IntProperty("grim-forward-ticks", 2, 1, 8, () -> this.rotationMode.getValue() == 7);
        public final IntProperty grimBackTicks = new IntProperty("grim-back-ticks", 2, 1, 8, () -> this.rotationMode.getValue() == 7);
        public final FloatProperty grimForwardPitch = new FloatProperty("grim-forward-pitch", 35.0F, 0.0F, 90.0F, () -> this.rotationMode.getValue() == 7);
        public final FloatProperty grimBackPitch = new FloatProperty("grim-back-pitch", 80.0F, 50.0F, 90.0F, () -> this.rotationMode.getValue() == 7);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0, new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
        public final BooleanProperty hypixeltower = new BooleanProperty("hypixeltower", false, () -> this.tower.getValue() == 3);
        public final BooleanProperty safe = new BooleanProperty("safe", false, () -> this.tower.getValue() == 3);
            public final ModeProperty safeMode = new ModeProperty("safe-mode", 0, new String[]{"STUCK", "LEGIT"}, () -> this.tower.getValue() == 3 && this.safe.getValue());
            public final IntProperty safeStuckDelayTicksProperty = new IntProperty("safe-delay-ticks", 1, 1, 3, () -> this.tower.getValue() == 3 && this.safe.getValue() && this.safeMode.getValue() == 0);
            public final IntProperty safeUnmoveTicks = new IntProperty("unmove-ticks", 1, 1, 5, () -> this.tower.getValue() == 3 && this.safe.getValue() && this.safeMode.getValue() == 1);
    public final ModeProperty keepY = new ModeProperty("keep-y", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY", "EXTRATELLY"});
        public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
        public final BooleanProperty disableWhileJumpActive = new BooleanProperty("not-on-jump-potion", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty eagle = new BooleanProperty("eagle", false);
        public final FloatProperty edgeDistance = new FloatProperty("edge-distance", 0.13F, 0.0F, 0.5F, () -> this.eagle.getValue());
        public final IntProperty sneakDelay = new IntProperty("sneak-delay", 80, 0, 500, () -> this.eagle.getValue());
        public final IntProperty blocksPerSneak = new IntProperty("blocks-per-sneak", 1, 1, 5, () -> this.eagle.getValue());
    public final BooleanProperty biggestStack = new BooleanProperty("biggest-stack", true);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", false);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", false);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", true);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    private boolean eagleSneaking = false;
    private int eagleSneakTicks = 0;
    private long eagleLastSneakTime = 0L;
    private int eagleBlocksPlaced = 0;

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        }
        // GRIM forward phase: allow sprint
        if (this.rotationMode.getValue() == 7 && this.grimPhase == 0) {
            return false;
        }
        boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2 || this.keepY.getValue() == 4;
        return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    /** GRIM: snap yaw to nearest 45 degrees (Souvenir snap45) */
    private static float snap45(float yaw) {
        return MathHelper.wrapAngleTo180_float((float) Math.round(yaw / 45.0F) * 45.0F);
    }

    /**
     * GRIM searchTarget — fixed for scaffold use:
     * Prefer the cell under feet / same layer extension, not "closest to eye" side walls.
     * Score prioritizes: under-player cell, same Y layer, movement direction, then eye distance.
     */
    private BlockData searchGrimTarget() {
        this.grimHitVec = null;
        int feetY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        // Respect keep-y / stage like getBlockData
        if (this.stage != 0 && !this.shouldKeepY) {
            feetY = Math.min(feetY, this.startY - 1);
        }
        BlockPos origin = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                feetY,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        BlockData best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double reach = mc.playerController.getBlockReachDistance();
        double maxDistSq = reach * reach;
        float moveYaw = this.getCurrentYaw();
        double moveX = -MathHelper.sin(moveYaw * (float) Math.PI / 180.0F);
        double moveZ = MathHelper.cos(moveYaw * (float) Math.PI / 180.0F);
        boolean moving = MoveUtil.isForwardPressed() || mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ > 0.001;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos place = origin.add(x, 0, z);
                if (!BlockUtil.isReplaceable(place)) {
                    continue;
                }
                // Only same scaffold layer (do not tower / dig around)
                if (place.getY() != origin.getY()) {
                    continue;
                }
                for (EnumFacing d : EnumFacing.VALUES) {
                    // Prefer horizontal expansion + under-feet UP; skip DOWN (place above head)
                    if (d == EnumFacing.DOWN) {
                        continue;
                    }
                    BlockPos support = place.offset(d);
                    if (BlockUtil.isReplaceable(support) || BlockUtil.isInteractable(support)) {
                        continue;
                    }
                    EnumFacing face = d.getOpposite();
                    // Disallow placing "on top of neighbor" which builds side pillars around you
                    // unless the place cell is exactly under the player
                    if (face == EnumFacing.UP && (x != 0 || z != 0)) {
                        continue;
                    }
                    Vec3 hit = new Vec3(
                            (double) support.getX() + 0.5 + (double) face.getFrontOffsetX() * 0.5,
                            (double) support.getY() + 0.5 + (double) face.getFrontOffsetY() * 0.5,
                            (double) support.getZ() + 0.5 + (double) face.getFrontOffsetZ() * 0.5
                    );
                    if (eye.squareDistanceTo(hit) > maxDistSq) {
                        continue;
                    }
                    double dx = hit.xCoord - eye.xCoord;
                    double dy = hit.yCoord - eye.yCoord;
                    double dz = hit.zCoord - eye.zCoord;
                    float[] rot = RotationUtil.getRotationsTo(dx, dy, dz, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                    // After snap45 the ray may miss; validate with unsnapped aim first
                    MovingObjectPosition ray = RotationUtil.rayTrace(rot[0], rot[1], reach, 1.0F);
                    if (ray == null
                            || ray.typeOfHit != MovingObjectType.BLOCK
                            || !ray.getBlockPos().equals(support)
                            || ray.sideHit != face) {
                        continue;
                    }

                    // --- scaffold scoring (NOT pure eye distance) ---
                    double score = 0.0;
                    // 1) Prefer under feet (0,0)
                    if (x == 0 && z == 0) {
                        score += 0.0;
                    } else {
                        score += 8.0 + (x * x + z * z) * 4.0;
                    }
                    // 2) Prefer in movement direction
                    if (moving && (x != 0 || z != 0)) {
                        double placeDx = (place.getX() + 0.5) - mc.thePlayer.posX;
                        double placeDz = (place.getZ() + 0.5) - mc.thePlayer.posZ;
                        double len = MathHelper.sqrt_double(placeDx * placeDx + placeDz * placeDz);
                        if (len > 1.0E-3) {
                            double dot = (placeDx / len) * moveX + (placeDz / len) * moveZ;
                            score += (1.0 - dot) * 6.0; // behind = higher score
                        }
                    }
                    // 3) Slight preference for closer hit (tie-break)
                    score += eye.squareDistanceTo(hit) * 0.15;
                    // 4) Prefer UP under feet / horizontal faces for bridge
                    if (face == EnumFacing.UP && x == 0 && z == 0) {
                        score -= 2.0;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockData(support, face);
                        this.grimHitVec = hit;
                    }
                }
            }
        }
        return best;
    }

    /** Pick stable +45 / -45 offset from movement so diagonal stays consistent. */
    private float grimSideSign(float moveYaw) {
        float mod = MathHelper.wrapAngleTo180_float(moveYaw);
        // keep same diagonal side while moving
        float m = ((mod % 90.0F) + 90.0F) % 90.0F;
        return m < 45.0F ? -1.0F : 1.0F;
    }

    /**
     * GRIM cycle rotation:
     * phase 0 forward-45 (sprint) / phase 1 back-45 (place).
     */
    private float[] getGrimCycleRotation(float moveYaw, Vec3 hit) {
        float side = this.grimDiagonal.getValue() ? this.grimSideSign(moveYaw) * 45.0F : 0.0F;
        if (this.grimPhase == 0) {
            // Forward 45° relative to movement
            float yaw = snap45(moveYaw + side);
            float pitch = this.grimForwardPitch.getValue();
            return new float[]{RotationUtil.quantizeAngle(yaw), RotationUtil.quantizeAngle(pitch)};
        }
        // Back 45°: face opposite movement on the same diagonal
        float backBase = moveYaw + 180.0F;
        float yaw = snap45(backBase + side);
        float pitch = this.grimBackPitch.getValue();
        // If we have a place hit, blend pitch toward real aim (still keep yaw on 45 grid)
        if (hit != null) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
            double dx = hit.xCoord - eye.xCoord;
            double dy = hit.yCoord - eye.yCoord;
            double dz = hit.zCoord - eye.zCoord;
            double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
            float aimPitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
            pitch = MathHelper.clamp_float(aimPitch, 55.0F, 90.0F);
            // Prefer snap45 of pure aim yaw only if still roughly backward
            float aimYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            float aimBackDiff = Math.abs(MathHelper.wrapAngleTo180_float(aimYaw - backBase));
            if (aimBackDiff < 50.0F) {
                yaw = snap45(aimYaw);
            }
        }
        return new float[]{RotationUtil.quantizeAngle(yaw), RotationUtil.quantizeAngle(pitch)};
    }

    private void tickGrimPhase(boolean placed) {
        this.grimPhaseTicks++;
        if (this.grimPhase == 0) {
            if (this.grimPhaseTicks >= this.grimForwardTicks.getValue()) {
                this.grimPhase = 1;
                this.grimPhaseTicks = 0;
            }
        } else {
            // Back phase: switch after place, or after hold ticks even if no place
            if (placed || this.grimPhaseTicks >= this.grimBackTicks.getValue()) {
                this.grimPhase = 0;
                this.grimPhaseTicks = 0;
            }
        }
    }

    private boolean isGrimPlacePhase() {
        return this.rotationMode.getValue() == 7 && this.grimPhase == 1;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!BlockUtil.isReplaceable(pos)
                                && !BlockUtil.isInteractable(pos)
                                && !(
                                mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                        > (double) mc.playerController.getBlockReachDistance()
                        )
                                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                if (facing != EnumFacing.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (BlockUtil.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                        )
                );
                BlockPos blockPos = positions.get(0);
                EnumFacing facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                this.placedThisTick = true;
                this.eagleBlocksPlaced++;
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }

                if (this.biggestStack.getValue()) {
                    int bestSlot = -1;
                    int maxStack = 0;
                    for (int i = 0; i < 9; ++i) {
                        ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
                        if (ItemUtil.isBlock(itemStack) && itemStack.stackSize > maxStack) {
                            maxStack = itemStack.stackSize;
                            bestSlot = i;
                        }
                    }
                    if (bestSlot != -1) {
                        mc.thePlayer.inventory.currentItem = bestSlot;
                    }
                }
            }
        }
    }

    private MovingObjectPosition getPlacementMop(BlockData blockData, float yaw, float pitch) {
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null
                || mop.typeOfHit != MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(blockData.blockPos())
                || mop.sideHit != blockData.facing()) {
            return null;
        }
        return mop;
    }

    private boolean isDuplicateSnapRotation(float yaw, float pitch) {
        return !Float.isNaN(this.lastSnapPlaceYaw)
                && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.lastSnapPlaceYaw)) < 0.35F;
    }

    private float[] getSnapRotation(BlockData blockData, float yaw, float pitch) {
        float baseYaw = RotationUtil.quantizeAngle(yaw);
        float basePitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F));

        if (!this.isDuplicateSnapRotation(baseYaw, basePitch)) {
            return new float[]{baseYaw, basePitch};
        }

        for (int i = 0; i < 24; i++) {
            float yawStep = 0.35F + 0.075F * (float) (i / 2);
            float pitchStep = 0.025F + 0.01F * (float) (i / 3);
            float testYaw = RotationUtil.quantizeAngle(baseYaw + (i % 2 == 0 ? yawStep : -yawStep));
            float testPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(basePitch + (i % 4 < 2 ? pitchStep : -pitchStep), -90.0F, 90.0F));

            if (!this.isDuplicateSnapRotation(testYaw, testPitch) && this.getPlacementMop(blockData, testYaw, testPitch) != null) {
                return new float[]{testYaw, testPitch};
            }
        }

        return null;
    }

    private void rememberSnapRotation() {
        this.lastSnapPlaceYaw = this.yaw;
        this.lastSnapPlacePitch = this.pitch;
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private boolean isNearEdge() {
        if (!mc.thePlayer.onGround) {
            return false;
        }
        double fracX = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        double fracZ = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
        double threshold = this.edgeDistance.getValue();
        double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
        return minDist <= threshold;
    }

    private boolean shouldSneak() {
        if (!this.eagle.getValue() || !mc.thePlayer.onGround) {
            return false;
        }
        if (this.eagleBlocksPlaced < this.blocksPerSneak.getValue()) {
            return false;
        }
        if (System.currentTimeMillis() - this.eagleLastSneakTime < (long) this.sneakDelay.getValue().intValue()) {
            return false;
        }
        return this.isNearEdge();
    }

    private void updateEagle() {
        if (!this.eagle.getValue()) {
            this.eagleSneaking = false;
            this.eagleSneakTicks = 0;
            return;
        }
        if (this.eagleSneakTicks > 0) {
            this.eagleSneakTicks--;
            if (this.eagleSneakTicks == 0) {
                this.eagleSneaking = false;
            }
            return;
        }
        if (this.shouldSneak()) {
            this.eagleSneaking = true;
            this.eagleSneakTicks = 2;
            this.eagleLastSneakTime = System.currentTimeMillis();
            this.eagleBlocksPlaced = 0;
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3 || this.keepY.getValue() == 4;
            boolean tower = this.tower.getValue() == 3;
            return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
        } else {
            return false;
        }
    }

    public Scaffold() {
        super("Scaffold", "Auto rotation and place.", Category.PLAYER, 0, false, false);
    }

    public int getSlot() {
        return this.lastSlot;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.placedThisTick = false;
            if (this.safeStuckDelayTicks > 0) {
                this.safeStuckDelayTicks--;
                if (this.safeStuckDelayTicks <= 0) {
                    this.safeStuckTicks = 1;
                }
            }
            if (this.safeStuckTicks > 0) {
                if (!this.safeStuckActive) {
                    this.savedMotionX = mc.thePlayer.motionX;
                    this.savedMotionY = mc.thePlayer.motionY;
                    this.savedMotionZ = mc.thePlayer.motionZ;
                    this.safeStuckActive = true;
                }
                Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
            } else if (this.safeStuckActive) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                mc.thePlayer.motionX = this.savedMotionX;
                mc.thePlayer.motionY = this.savedMotionY;
                mc.thePlayer.motionZ = this.savedMotionZ;
                this.safeStuckActive = false;
            }
            if (this.legitDelayTicks > 0) {
                this.legitDelayTicks--;
                if (this.legitDelayTicks <= 0) {
                    this.legitReleaseTicks = this.safeUnmoveTicks.getValue();
                    this.legitActive = true;
                }
            }
            if (this.legitReleaseTicks > 0) {
                this.legitReleaseTicks--;
                if (this.legitReleaseTicks <= 0) {
                    this.legitActive = false;
                }
            }
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (this.grimPlaceDelayCounter > 0) {
                this.grimPlaceDelayCounter--;
            }
            this.updateEagle();
            if (hypixeltower.getValue() && mc.thePlayer.motionY <= 0.0 && Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ) <= 0.02D && mc.thePlayer.motionY >= -0.09 && !(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ||
                    Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                mc.thePlayer.motionY = -0.38;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0
                        && this.keepY.getValue() != 0
                        && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                boolean snapMode = this.rotationMode.getValue() == 6;
                boolean grimMode = this.rotationMode.getValue() == 7;
                this.snapRotating = false;
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1: // Default
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2: // Smooth
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                float targetYaw = this.isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
                                float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.yaw);
                                float pitchDiff = MathHelper.wrapAngleTo180_float(85.0F - this.pitch);
                                float yawTolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(tellystartrotationminspeed.getValue(), tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(tellynormalrotationminspeed.getValue(), tellynormalrotationmaxspeed.getValue());
                                float pitchTolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(tellystartrotationminspeed.getValue(), tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(tellynormalrotationminspeed.getValue(), tellynormalrotationmaxspeed.getValue());
                                this.yaw = RotationUtil.quantizeAngle(this.yaw + RotationUtil.clampAngle(yawDiff, yawTolerance));
                                this.pitch = RotationUtil.quantizeAngle(this.pitch + RotationUtil.clampAngle(pitchDiff, pitchTolerance));
                            }
                            break;
                        case 3: // Backwards
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 4: // Sideways
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 5: // Hypixel
                            //idk what to put here so imma just do the same as sideways for now
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 6: // Snap
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            this.pitch = RotationUtil.quantizeAngle(85.0F);
                            break;
                        case 7: // GRIM forward-45 / back-45 cycle
                            {
                                float[] grimRot = this.getGrimCycleRotation(currentYaw, this.grimHitVec);
                                this.yaw = grimRot[0];
                                this.pitch = grimRot[1];
                            }
                            break;
                    }
                }
                BlockData blockData;
                if (grimMode) {
                    this.grimTarget = this.searchGrimTarget();
                    blockData = this.grimTarget;
                    // Advance forward phase even when no place happens this tick
                    if (this.grimPhase == 0) {
                        this.tickGrimPhase(false);
                    }
                } else {
                    this.grimTarget = null;
                    this.grimHitVec = null;
                    this.grimPhase = 0;
                    this.grimPhaseTicks = 0;
                    blockData = this.getBlockData();
                }

                Vec3 hitVec = null;
                if (blockData != null) {
                    double[] x = placeOffsets;
                    double[] y = placeOffsets;
                    double[] z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop != null
                                        && mop.typeOfHit == MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos())
                                        && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        if (grimMode) {
                            float[] grimRot = this.getGrimCycleRotation(currentYaw, this.grimHitVec);
                            this.yaw = grimRot[0];
                            this.pitch = grimRot[1];
                        } else {
                            this.yaw = bestYaw;
                            this.pitch = bestPitch;
                        }
                        this.canRotate = true;
                    }
                }
                boolean towerRotating = this.towering || this.isTowering();
                boolean snapAlreadyLooking = false;
                boolean snapCanPlace = true;
                if (snapMode && !towerRotating && blockData != null) {
                    MovingObjectPosition currentMop = this.getPlacementMop(blockData, event.getYaw(), event.getPitch());
                    if (currentMop != null) {
                        float[] snapRotation = this.getSnapRotation(blockData, event.getYaw(), event.getPitch());
                        if (snapRotation == null) {
                            snapCanPlace = false;
                            hitVec = null;
                        } else {
                            this.yaw = snapRotation[0];
                            this.pitch = snapRotation[1];
                            this.canRotate = true;
                            MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
                            hitVec = snapMop != null ? snapMop.hitVec : currentMop.hitVec;
                            this.snapRotating = true;
                            if (this.rotationTick > this.snapDelay.getValue()) {
                                this.rotationTick = this.snapDelay.getValue();
                            }
                        }
                    } else if (hitVec != null && this.canRotate) {
                        float[] snapRotation = this.getSnapRotation(blockData, this.yaw, this.pitch);
                        if (snapRotation == null) {
                            snapCanPlace = false;
                            hitVec = null;
                        } else {
                            this.yaw = snapRotation[0];
                            this.pitch = snapRotation[1];
                            MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
                            if (snapMop != null) {
                                hitVec = snapMop.hitVec;
                            }
                            this.snapRotating = true;
                            if (this.rotationTick > this.snapDelay.getValue()) {
                                this.rotationTick = this.snapDelay.getValue();
                            }
                        }
                    }
                }
                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch (this.rotationMode.getValue()) {
                        case 3: // Backwards
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            break;
                        case 4: // Sideways
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                    }
                }
                if (this.rotationMode.getValue() != 0 && (!snapMode || this.snapRotating || towerRotating)) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(tellystartrotationminspeed.getValue(), tellystartrotationmaxspeed.getValue()) : RandomUtil.nextFloat(tellynormalrotationminspeed.getValue(), tellynormalrotationmaxspeed.getValue());
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (towerRotating && this.isTowering()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (grimMode && this.grimHitVec != null) {
                    hitVec = this.grimHitVec;
                }
                boolean canPlaceNow;
                if (grimMode) {
                    // Only place during back-45 phase
                    canPlaceNow = this.isGrimPlacePhase()
                            && blockData != null && hitVec != null
                            && this.grimPlaceDelayCounter <= 0 && !this.placedThisTick;
                } else {
                    canPlaceNow = blockData != null && hitVec != null && snapCanPlace && (this.rotationTick <= 0 || snapAlreadyLooking);
                }
                if (canPlaceNow) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    if (grimMode) {
                        this.grimPlaceDelayCounter = this.grimPlaceDelay.getValue();
                        this.grimTarget = null;
                        this.grimHitVec = null;
                        this.tickGrimPhase(this.placedThisTick);
                    }
                    if (snapMode) {
                        this.rememberSnapRotation();
                    }
                    if (this.multiplace.getValue() && !snapMode && !grimMode) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop == null
                                        || mop.typeOfHit != MovingObjectType.BLOCK
                                        || !mop.getBlockPos().equals(blockData.blockPos())
                                        || mop.sideHit != blockData.facing()) {
                                    break;
                                }
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
                // GRIM back phase: advance cycle even if place failed this tick
                if (grimMode && this.grimPhase == 1 && !this.placedThisTick) {
                    this.tickGrimPhase(false);
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0 && !this.placedThisTick) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if ((this.keepY.getValue() == 2 || this.keepY.getValue() == 4) && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0 && !this.placedThisTick) {
                            MovingObjectPosition mop = this.getPlacementMop(blockData, this.yaw, this.pitch);
                            if (mop != null) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (this.safeStuckTicks > 0) {
                event.setForward(0.0F);
                event.setStrafe(0.0F);
                return;
            }
            if (this.safe.getValue() && this.safeMode.getValue() == 1 && this.legitActive) {
                event.setForward(0.0F);
                event.setStrafe(0.0F);
                return;
            }
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.towerTick = 2;
                                            this.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.towerTick = 2;
                                        this.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.towerTick = 0;
                                    this.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.towerDelay >= 4) {
                                    this.towerTick = 4;
                                    this.towerDelay = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.towerTick = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.towerTick = 0;
                                this.towerDelay = 0;
                                return;
                        }
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.safeStuckTicks > 0) {
                mc.thePlayer.movementInput.moveForward = 0.0f;
                mc.thePlayer.movementInput.moveStrafe = 0.0f;
                mc.thePlayer.movementInput.jump = false;
                mc.thePlayer.movementInput.sneak = false;
                return;
            }
            if (this.safe.getValue() && this.safeMode.getValue() == 1 && this.legitActive) {
                // 純粹放開移動鍵（前後左右），不動 jump/sneak，也不凍結封包
                mc.thePlayer.movementInput.moveForward = 0.0f;
                mc.thePlayer.movementInput.moveStrafe = 0.0f;
                return;
            }
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed() && !this.snapRotating) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.eagleSneaking && !mc.thePlayer.movementInput.sneak) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
            }
        }
    }


    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            if (this.safeStuckTicks > 0) {
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
                this.safeStuckTicks--;
            }
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }

            if (this.safe.getValue() && this.tower.getValue() == 3 && mc.gameSettings.keyBindJump.isKeyDown()) {
                float moveYaw = this.getCurrentYaw();
                boolean diagonal = this.isDiagonal(moveYaw);
                if (this.safeMode.getValue() == 0) {
                    // ===== STUCK 模式（原本邏輯）=====
                    if (diagonal && !mc.thePlayer.onGround) {
                        double motionY = mc.thePlayer.motionY;
                        if (this.safePrevMotionY > 0.0 && motionY <= 0.0) {
                            double motionXZ = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
                            double motionXZSpeedBps = motionXZ * 20.0;
                            if (this.safeStuckDelayTicks <= 0 && this.safeStuckTicks <= 0 && motionXZSpeedBps >= 4.67) {
                                this.safeStuckDelayTicks = this.safeStuckDelayTicksProperty.getValue();
                            }
                        }
                        this.safePrevMotionY = motionY;
                    } else {
                        this.safePrevMotionY = mc.thePlayer.motionY;
                    }
                        this.legitWasOnGround = mc.thePlayer.onGround;
                } else {
                    // ===== LEGIT 模式（新增）=====
                    this.safePrevMotionY = mc.thePlayer.motionY;
                    if (diagonal) {
                        boolean onGroundNow = mc.thePlayer.onGround;
                        // 偵測「剛起跳」：上一tick在地面，這一tick離地
                        if (this.legitWasOnGround && !onGroundNow
                                && this.legitDelayTicks <= 0
                                && this.legitReleaseTicks <= 0
                                && !this.legitActive) {
                            this.legitDelayTicks = 1; // 起跳後等 1 tick
                        }
                        this.legitWasOnGround = onGroundNow;
                    } else {
                        this.legitWasOnGround = mc.thePlayer.onGround;
                    }
                }
            } else {
                this.safePrevMotionY = mc.thePlayer.motionY;
                this.legitWasOnGround = mc.thePlayer.onGround;
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.blockCounter.getValue()) {
                int count = 0;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null && stack.stackSize > 0) {
                        Item item = stack.getItem();
                        if (item instanceof ItemBlock) {
                            Block block = ((ItemBlock) item).getBlock();
                            if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                                count += stack.stackSize;
                            }
                        }
                    }
                }
                HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                float scale = hud.scale.getValue();
                GlStateManager.pushMatrix();
                GlStateManager.scale(scale, scale, 0.0F);
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                mc.fontRendererObj
                        .drawString(
                                String.format("%d block%s left", count, count != 1 ? "s" : ""),
                                ((float) new ScaledResolution(mc).getScaledWidth() / 2.0F + (float) mc.fontRendererObj.FONT_HEIGHT * 1.5F) / scale,
                                (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / scale - (float) mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                                (count > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | -1090519040,
                                hud.shadow.getValue()
                        );
                GlStateManager.disableBlend();
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.lastSlot = event.setSlot(this.lastSlot);
            event.setCancelled(true);
        }
    }

    /**
     * EarlyPlace (Souvenir Grim / Leader-aligned).
     * Fired from MixinEntityPlayerSP just before onUpdateWalkingPlayer,
     * after UpdateEvent PRE has applied GRIM rotation this tick.
     */
    @EventTarget(Priority.HIGH)
    public void onEarlyPlace(EarlyPlaceEvent event) {
        if (!this.isEnabled() || this.rotationMode.getValue() != 7) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.grimPlaceDelayCounter > 0 || this.placedThisTick) {
            return;
        }
        // Only during back-45 place phase
        if (!this.isGrimPlacePhase()) {
            return;
        }
        if (!this.canPlace()) {
            return;
        }
        if (!ItemUtil.isHoldingBlock() || this.blockCount <= 0) {
            return;
        }
        if (this.grimTarget == null) {
            return;
        }

        float useYaw = event.getYaw();
        float usePitch = event.getPitch();
        MovingObjectPosition mop = this.getPlacementMop(this.grimTarget, useYaw, usePitch);
        Vec3 hit = mop != null ? mop.hitVec : this.grimHitVec;
        if (hit == null) {
            return;
        }
        this.place(this.grimTarget.blockPos(), this.grimTarget.facing(), hit);
        if (this.placedThisTick) {
            event.markPlaced();
            this.grimPlaceDelayCounter = this.grimPlaceDelay.getValue();
            this.grimTarget = null;
            this.grimHitVec = null;
            this.tickGrimPhase(true);
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
        this.safeStuckTicks = 0;
        this.safeStuckDelayTicks = 0;
        this.safePrevMotionY = 0.0;
        this.safeStuckActive = false;
        this.legitWasOnGround = true;
        this.legitDelayTicks = 0;
        this.legitReleaseTicks = 0;
        this.legitActive = false;
        this.eagleSneaking = false;
        this.eagleSneakTicks = 0;
        this.eagleBlocksPlaced = 0;
        this.eagleLastSneakTime = 0L;
        this.snapRotating = false;
        this.lastSnapPlaceYaw = Float.NaN;
        this.lastSnapPlacePitch = Float.NaN;
        this.grimTarget = null;
        this.grimHitVec = null;
        this.grimPlaceDelayCounter = 0;
        this.grimPhase = 0;
        this.grimPhaseTicks = 0;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        if (this.safeStuckActive && mc.thePlayer != null) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionY = this.savedMotionY;
            mc.thePlayer.motionZ = this.savedMotionZ;
        }
        this.safeStuckTicks = 0;
        this.safeStuckDelayTicks = 0;
        this.safePrevMotionY = 0.0;
        this.safeStuckActive = false;
        this.legitWasOnGround = true;
        this.legitDelayTicks = 0;
        this.legitReleaseTicks = 0;
        this.legitActive = false;
        this.eagleSneaking = false;
        this.eagleSneakTicks = 0;
        this.grimTarget = null;
        this.grimHitVec = null;
        this.grimPlaceDelayCounter = 0;
        this.grimPhase = 0;
        this.grimPhaseTicks = 0;
    }

    public int getBlockCount() {
        return this.blockCount;
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}
