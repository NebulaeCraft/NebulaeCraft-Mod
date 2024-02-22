package net.kuina.nebulaecraft.util;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.block.*;
import net.kuina.nebulaecraft.entities.*;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;


@ElementsNebulaecraftMod.ModElement.Tag
public class RegistryHandler extends ElementsNebulaecraftMod.ModElement {

    public static final List<Block> BLOCKS = new ArrayList<Block>();
    public static final List<Item> ITEMS = new ArrayList<Item>();

    public Block blockToRegister = null;
    public String blockRegistryName;
    private final int ScreenDoorBigIDStart = 1;
    private final int ScreenDoorBigIDEnd = 4;
    private final int ScreenDoorMediumIDStart = 5;
    private final int ScreenDoorMediumIDEnd = 8;
    private final int ScreenDoorSmallIDStart = 9;
    private final int ScreenDoorSmallIDEnd = 12;
    private final int InfoScreenIDStart = 1;
    private final int InfoScreenIDEnd = 5;
    private final int RoadsignSmallIDStart = 1;
    private final int RoadsignSmallIDEnd = 81;
    private final int RoadsignBigIDStart = 82;
    private final int RoadsignBigIDEnd = 84;
    private final int RoadmarkSpecialIDStart = 1;
    private final int RoadmarkSpecialIDEnd = 8;


    public RegistryHandler(ElementsNebulaecraftMod instance) {
        super(instance, 100);
    }

    public void registerBlock(Block blockThis, String blockRegistryName) {
        blockThis.setRegistryName(blockRegistryName).setUnlocalizedName(blockRegistryName);
        BLOCKS.add(blockThis);
        ForgeRegistries.BLOCKS.register(blockThis);
        ForgeRegistries.ITEMS.register(new ItemBlock(blockThis).setRegistryName(blockThis.getRegistryName()));
    }

    public void registerItem(Item itemThis, String itemRegistryName) {
        itemThis.setRegistryName(itemRegistryName).setUnlocalizedName(itemRegistryName);
        ITEMS.add(itemThis);
        ForgeRegistries.ITEMS.register(itemThis);
    }

    public void registerTileEntity(Class tileEntityClass, String tileEntityName) {
        GameRegistry.registerTileEntity(tileEntityClass, tileEntityName);
    }

    ServerHandler handler = new ServerHandler();

    @Override
    public void initElements() {
        System.out.println(ServerHandler.CornField);
        for (int i = ScreenDoorBigIDStart; i <= ScreenDoorBigIDEnd; i++) {
            registerBlock(new BlockScreenDoorBig.BlockCustom(), "screen_door_" + i);
        }
        for (int i = ScreenDoorMediumIDStart; i <= ScreenDoorMediumIDEnd; i++) {
            registerBlock(new BlockScreenDoorMedium.BlockCustom(), "screen_door_" + i);
        }
        for (int i = ScreenDoorSmallIDStart; i <= ScreenDoorSmallIDEnd; i++) {
            registerBlock(new BlockScreenDoorSmall.BlockCustom(), "screen_door_" + i);

        }
        for (int i = InfoScreenIDStart; i <= InfoScreenIDEnd; i++) {
            registerBlock(new BlockInfoScreen.BlockCustom(), "info_screen_" + i);

        }
        for (int i = RoadsignSmallIDStart; i <= RoadsignSmallIDEnd; i++) {
            registerBlock(new BlockRoadsignSmall.BlockCustom(), "roadsign_" + i);
        }
        for (int i = RoadsignBigIDStart; i <= RoadsignBigIDEnd; i++) {
            registerBlock(new BlockRoadsignBig.BlockCustom(), "roadsign_" + i);

        }
        for (int i = RoadmarkSpecialIDStart; i <= RoadmarkSpecialIDEnd; i++) {
            registerBlock(new BlockRoadmarkSpecial.BlockCustom(), "roadmark_special_" + i);

        }
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "white_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "white_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "white_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "white_line_diagonal" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "white_line_l");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "white_line_l" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "white_line_t");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "white_line_t" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_white_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_white_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_white_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_white_line_diagonal" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_white_line_wye");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_white_line_wye" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_white_line_lean");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_white_line_lean" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "yellow_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "yellow_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "yellow_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "yellow_line_diagonal" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "yellow_line_lean");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "yellow_line_lean" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_yellow_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_yellow_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "double_yellow_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "double_yellow_line_diagonal" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "stop_line_full");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "crosswalk");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bus_half");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bus_half" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bus_half_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bus_half_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bus_half_line_diagonal_right");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bus_half_line_diagonal_right" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bus_half_line_diagonal_left");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bus_half_line_diagonal_left" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bicycle_half");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bicycle_half" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bicycle_half_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bicycle_half_line" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bicycle_half_line_diagonal_right");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bicycle_half_line_diagonal_right" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "bicycle_half_line_diagonal_left");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_" + "bicycle_half_line_diagonal_left" + "_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_full");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_half");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_fork_right");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_fork_left");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_merge_right");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "parallel_merge_left");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "white_line_small");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "yellow_line_small");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_" + "light_red");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "straight");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "right");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "straight_right");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "straight_left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "right_left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "back");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "halfright");
    	registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_" + "halfleft");

        registerItem(new CameraCart.ItemCameraCart(), "cameracart");
    }


    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        for (Block BLOCK : BLOCKS) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(BLOCK), 0,
                    new ModelResourceLocation(Item.getItemFromBlock(BLOCK).getRegistryName(), "inventory"));
        }

        for (Item ITEM : ITEMS) {
            ModelLoader.setCustomModelResourceLocation(ITEM, 0,
                    new ModelResourceLocation(ITEM.getRegistryName(), "inventory"));
        }
    }
}