
package net.kuina.nebulaecraft.util;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.block.BlockScreenDoorBig;
import net.kuina.nebulaecraft.block.BlockScreenDoorMedium;
import net.kuina.nebulaecraft.block.BlockScreenDoorSmall;
import net.kuina.nebulaecraft.block.BlockInfoScreen;
import net.kuina.nebulaecraft.block.BlockRoadsignBig;
import net.kuina.nebulaecraft.block.BlockRoadsignSmall;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;


@ElementsNebulaecraftMod.ModElement.Tag
public class RegistryHandler extends ElementsNebulaecraftMod.ModElement {

    public static final List<Block> BLOCKS = new ArrayList<Block>();


    public Block blockToRegister = null;
    public String blockRegistryName;
	private int ScreenDoorBigIDStart=1,ScreenDoorBigIDEnd=4;
	private int ScreenDoorMediumIDStart=5,ScreenDoorMediumIDEnd=8;
	private int ScreenDoorSmallIDStart=9,ScreenDoorSmallIDEnd=12;
	private int InfoScreenIDStart=1,InfoScreenIDEnd=5;
    private int RoadsignSmallIDStart=1,RoadsignSmallIDEnd=81;
    private int RoadsignBigIDStart=82,RoadsignBigIDEnd=84;
	

    public RegistryHandler(ElementsNebulaecraftMod instance) {
        super(instance, 0);
    }

    public void registerBlock(Block blockThis, String blockRegistryName){
        blockThis.setRegistryName(blockRegistryName).setUnlocalizedName(blockRegistryName);
        BLOCKS.add(blockThis);
        ForgeRegistries.BLOCKS.register(blockThis);
        ForgeRegistries.ITEMS.register(new ItemBlock(blockThis).setRegistryName(blockThis.getRegistryName()));
    }

    @Override
    public void initElements() {
		for (int i = ScreenDoorBigIDStart; i <= ScreenDoorBigIDEnd; i++) {
            registerBlock(new BlockScreenDoorBig.BlockCustom(),"screen_door_" + i);
        }
        for (int i = ScreenDoorMediumIDStart; i <= ScreenDoorMediumIDEnd; i++) {
            registerBlock(new BlockScreenDoorMedium.BlockCustom(),"screen_door_" + i);
        }
        for (int i = ScreenDoorSmallIDStart; i <= ScreenDoorSmallIDEnd; i++) {
            registerBlock(new BlockScreenDoorSmall.BlockCustom(),"screen_door_" + i);

        }
        for (int i = InfoScreenIDStart; i <= InfoScreenIDEnd; i++) {
            registerBlock(new BlockInfoScreen.BlockCustom(),"info_screen_" + i);

        }
        for (int i = RoadsignSmallIDStart; i <= RoadsignSmallIDEnd; i++) {
            registerBlock(new BlockRoadsignSmall.BlockCustom(),"roadsign_" + i);
        }
        for (int i = RoadsignBigIDStart; i <= RoadsignBigIDEnd; i++) {
            registerBlock(new BlockRoadsignBig.BlockCustom(),"roadsign_" + i);

        }
    }


    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        for (Block BLOCK :BLOCKS){
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(BLOCK), 0,
                    new ModelResourceLocation(Item.getItemFromBlock(BLOCK).getRegistryName(), "inventory"));
        }
    }
}
