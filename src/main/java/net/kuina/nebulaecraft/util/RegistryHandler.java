package net.kuina.nebulaecraft.util;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.block.*;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.kuina.nebulaecraft.entities.*;
import net.kuina.nebulaecraft.tileentity.TileEntityTdt;
import net.kuina.nebulaecraft.util.ServerHandler;
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
        for (int i = 1; i <= 4; i++) {
            registerBlock(new BlockScreenDoorBig.BlockCustom(), "screen_door_" + i);
        }
        for (int i = 5; i <= 8; i++) {
            registerBlock(new BlockScreenDoorMedium.BlockCustom(), "screen_door_" + i);
        }
        for (int i = 9; i <= 12; i++) {
            registerBlock(new BlockScreenDoorSmall.BlockCustom(), "screen_door_" + i);

        }
        for (int i = 1; i <= 5; i++) {
            registerBlock(new BlockInfoScreen.BlockCustom(), "info_screen_" + i);

        }
        for (int i = 1; i <= 81; i++) {
            registerBlock(new BlockRoadsignSmall.BlockCustom(), "roadsign_" + i);
        }
        for (int i = 82; i <= 84; i++) {
            registerBlock(new BlockRoadsignBig.BlockCustom(), "roadsign_" + i);

        }
        for (int i = 1; i <= 8; i++) {
            registerBlock(new BlockRoadmarkSpecial.BlockCustom(), "roadmark_special_" + i);

        }
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_white_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_white_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_white_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_white_line_diagonal_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_white_line_l");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_white_line_l_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_white_line_t");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_white_line_t_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_white_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_white_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_white_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_white_line_diagonal_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_white_line_wye");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_white_line_wye_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_white_line_lean");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_white_line_lean_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_yellow_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_yellow_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_yellow_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_yellow_line_diagonal_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_yellow_line_lean");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_yellow_line_lean_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_yellow_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_yellow_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_double_yellow_line_diagonal");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_double_yellow_line_diagonal_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_stop_line_full");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_crosswalk");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bus_half");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bus_half_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bus_half_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bus_half_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bus_half_line_diagonal_right");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bus_half_line_diagonal_right_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bus_half_line_diagonal_left");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bus_half_line_diagonal_left_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bicycle_half");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bicycle_half_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bicycle_half_line");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bicycle_half_line_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bicycle_half_line_diagonal_right");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bicycle_half_line_diagonal_right_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_bicycle_half_line_diagonal_left");
        registerBlock(new BlockAsphaltSlabAddon.BlockCustom(), "asphalt_bicycle_half_line_diagonal_left_slab");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_full");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_half");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_fork_right");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_fork_left");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_merge_right");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_parallel_merge_left");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_white_line_small");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_yellow_line_small");
        registerBlock(new BlockAsphaltAddon.BlockCustom(), "asphalt_light_red");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_straight");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_right");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_straight_right");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_straight_left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_right_left");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_back");
        registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_halfright");
    	registerBlock(new BlockRoadmarkArrow.BlockCustom(), "roadmark_arrow_halfleft");

        registerItem(new CameraCart.ItemCameraCart(), "cameracart");

        for(int i = 0; i <= 40; i++) {
        	registerBlock(new BlockTdt.BlockCustom(), "tdt_" + i);
        }
        registerBlock(new BlockTdt.BlockCustom().setCreativeTab(TabNebulaecraftMetro.tab), "tdt");

        registerTileEntity(TileEntityTdt.class, "nebulaecraft:tdt");
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