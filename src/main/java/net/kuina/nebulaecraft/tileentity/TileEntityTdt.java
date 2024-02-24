package net.kuina.nebulaecraft.tileentity;

import net.kuina.nebulaecraft.block.BlockTdt;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

public class TileEntityTdt extends TileEntity implements ITickable {

    int ticksCount = 0;
    boolean canCountdown = false;

    public TileEntityTdt() {
        super();
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            String countdown = BlockTdt.BlockCustom.getCountdown(world, pos);
            int countdownNumber = BlockTdt.BlockCustom.getCountdownInt(world, pos);
            if (!BlockTdt.BlockCustom.isPowered(world, pos)) {
                if (!countdown.equals(BlockTdt.BlockCustom.EnumTdt.DISABLED.getName())) {
                    BlockTdt.BlockCustom.setState(world, pos, BlockTdt.BlockCustom.EnumTdt.DISABLED.getName());
                }
                canCountdown = true;
                return;
            }

            // if block is newly powered, start countdown
            if (countdown.equals(BlockTdt.BlockCustom.EnumTdt.DISABLED.getName()) && canCountdown && BlockTdt.BlockCustom.isPowered(world, pos)) {
                if (ticksCount >= 10) {
                    if (BlockTdt.BlockCustom.getRange(world, pos) == BlockTdt.BlockCustom.EnumRange.TWENTY)
                        BlockTdt.BlockCustom.setState(world, pos, BlockTdt.BlockCustom.EnumTdt.TWENTY.getName());
                    else
                        BlockTdt.BlockCustom.setState(world, pos, BlockTdt.BlockCustom.EnumTdt.FORTY.getName());
                    ticksCount = 0;
                    return;
                }
            }
            // display ZERO state for 3 seconds
            // end countdown, detect isBlockPowered every tick
            if (countdown.equals(BlockTdt.BlockCustom.EnumTdt.ZERO.getName())) {
                if (ticksCount >= 3 * 20) {
                    BlockTdt.BlockCustom.setState(world, pos, BlockTdt.BlockCustom.EnumTdt.DISABLED.getName());
                    canCountdown = false;
                    ticksCount = 0;
                    return;
                }
            }

            // countdown-=1
            if (countdownNumber >= 1 && countdownNumber <= (BlockTdt.BlockCustom.getRange(world, pos) == BlockTdt.BlockCustom.EnumRange.TWENTY ? 20 : 40)) {
                if (ticksCount >= 10) {
                    BlockTdt.BlockCustom.setState(world, pos, BlockTdt.BlockCustom.EnumTdt.byMetadata(countdownNumber - 1).getName());
                    ticksCount = 0;
                    return;
                }
            }
            ticksCount++;
        }
    }
}
