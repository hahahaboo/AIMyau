package myau.util;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class BlockUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isReplaceable(BlockPos blockPos) {
        return BlockUtil.isReplaceable(BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock());
    }

    public static boolean isReplaceable(Block block) {
        if (!block.getMaterial().isReplaceable()) return false;
        if (!(block instanceof BlockSnow)) return true;
        return !(block.getBlockBoundsMaxY() > 0.125);
    }

    public static boolean isInteractable(BlockPos blockPos) {
        return BlockUtil.isInteractable(BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock());
    }

    public static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer) return true;
        if (block instanceof BlockWorkbench) return true;
        if (block instanceof BlockAnvil) return true;
        if (block instanceof BlockBed) return true;
        if (block instanceof BlockDoor) {
            if (block.getMaterial() != Material.iron) return true;
        }
        if (block instanceof BlockTrapDoor) return true;
        if (block instanceof BlockFenceGate) return true;
        if (block instanceof BlockFence) return true;
        if (block instanceof BlockButton) return true;
        if (block instanceof BlockLever) return true;
        return block instanceof BlockJukebox;
    }

    public static boolean isSolid(Block block) {
        if (block instanceof BlockStairs) return false;
        if (block instanceof BlockSlab) return false;
        if (block instanceof BlockEndPortalFrame) return false;
        if (block instanceof BlockEndPortal) return false;
        if (block instanceof BlockVine) return false;
        if (block instanceof BlockPumpkin) return false;
        if (block instanceof BlockCactus) return false;
        if (block instanceof BlockBush) return false;
        if (block instanceof BlockFalling) return false;
        if (block instanceof BlockWeb) return false;
        if (block instanceof BlockPane) return false;
        if (block instanceof BlockCarpet) return false;
        if (block instanceof BlockSnow) return false;
        if (block instanceof BlockFence) return false;
        if (block instanceof BlockFenceGate) return false;
        if (block instanceof BlockWall) return false;
        if (block instanceof BlockLadder) return false;
        if (block instanceof BlockTorch) return false;
        if (block instanceof BlockRedstoneWire) return false;
        if (block instanceof BlockRedstoneDiode) return false;
        if (block instanceof BlockBasePressurePlate) return false;
        if (block instanceof BlockTripWire) return false;
        if (block instanceof BlockTripWireHook) return false;
        if (block instanceof BlockRailBase) return false;
        if (block instanceof BlockSlime) return false;
        return !(block instanceof BlockTNT);
    }

    public static Vec3 getHitVec(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        MovingObjectPosition movingObjectPosition = RotationUtil.rayTrace(yaw, pitch, BlockUtil.mc.playerController.getBlockReachDistance(), 1.0f);
        if (movingObjectPosition != null) {
            if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (movingObjectPosition.getBlockPos().equals(blockPos)) {
                    if (movingObjectPosition.sideHit == enumFacing) {
                        return movingObjectPosition.hitVec;
                    }
                }
            }
        }
        return BlockUtil.getClickVec(blockPos, enumFacing);
    }

    public static Vec3 getClickVec(BlockPos blockPos, EnumFacing enumFacing) {
        Block block = BlockUtil.mc.theWorld.getBlockState(blockPos).getBlock();
        Vec3 vec3 = new Vec3((double) blockPos.getX() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinX()), block.getBlockBoundsMaxX()), (double) blockPos.getY() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinY()), block.getBlockBoundsMaxY()), (double) blockPos.getZ() + Math.min(Math.max(RandomUtil.nextDouble(0.0, 1.0), block.getBlockBoundsMinZ()), block.getBlockBoundsMaxZ()));
        switch (enumFacing) {
            default: {
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMinY(), vec3.zCoord);
            }
            case UP: {
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMaxY(), vec3.zCoord);
            }
            case NORTH: {
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMinZ());
            }
            case EAST: {
                return new Vec3((double) blockPos.getX() + block.getBlockBoundsMaxX(), vec3.yCoord, vec3.zCoord);
            }
            case SOUTH: {
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMaxZ());
            }
            case WEST:
        }
        return new Vec3((double) blockPos.getX() + block.getBlockBoundsMinX(), vec3.yCoord, vec3.zCoord);
    }

    public static boolean isContainer(BlockPos pos) {
        return isContainer(mc.theWorld.getBlockState(pos).getBlock());
    }

    public static boolean isContainer(Block block) {
        return block instanceof BlockContainer;
    }

        /**
     * 更安全的優化 raytrace（解決少數 flag 問題）
     */
    public static Vec3 getOptimizedHitVec(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        // 第一次直接用當前角度 raytrace
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0f);
        if (isValidMop(mop, blockPos, enumFacing)) {
            return mop.hitVec;
        }

        // 第二次：使用安全內縮的 ClickVec
        Vec3 clickVec = getSafeClickVec(blockPos, enumFacing);
        double dx = clickVec.xCoord - mc.thePlayer.posX;
        double dy = clickVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double dz = clickVec.zCoord - mc.thePlayer.posZ;

        float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, yaw, pitch);
        mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0f);

        if (isValidMop(mop, blockPos, enumFacing)) {
            return mop.hitVec;
        }

        // 最終 fallback
        return getClickVec(blockPos, enumFacing);
    }

    private static boolean isValidMop(MovingObjectPosition mop, BlockPos pos, EnumFacing facing) {
        return mop != null 
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(pos)
                && mop.sideHit == facing;
    }

    /**
     * 更安全的 ClickVec（內縮避免邊緣被 flag）
     */
    public static Vec3 getSafeClickVec(BlockPos pos, EnumFacing facing) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        double offset = 0.1; // 安全內縮

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        switch (facing) {
            case NORTH: z = pos.getZ() + block.getBlockBoundsMinZ() + offset; break;
            case SOUTH: z = pos.getZ() + block.getBlockBoundsMaxZ() - offset; break;
            case WEST:  x = pos.getX() + block.getBlockBoundsMinX() + offset; break;
            case EAST:  x = pos.getX() + block.getBlockBoundsMaxX() - offset; break;
            case UP:    y = pos.getY() + block.getBlockBoundsMaxY() - offset; break;
            case DOWN:  y = pos.getY() + block.getBlockBoundsMinY() + offset; break;
        }

        // 少量隨機防檢測
        x += RandomUtil.nextDouble(-0.03, 0.03);
        z += RandomUtil.nextDouble(-0.03, 0.03);

        return new Vec3(x, y, z);
    }
}
