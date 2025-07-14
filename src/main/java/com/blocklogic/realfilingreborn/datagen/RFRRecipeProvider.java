package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.item.ModItems;
import com.blocklogic.realfilingreborn.util.RFRTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.concurrent.CompletableFuture;

public class RFRRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public RFRRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // Folders
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FILING_FOLDER.get(), 8)
                .pattern("PPP")
                .pattern("PG ")
                .pattern("PPP")
                .define('P', Items.PAPER)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NBT_FILING_FOLDER.get(), 4)
                .pattern("PFA")
                .pattern("FGF")
                .pattern("SFX")
                .define('A', Items.IRON_AXE)
                .define('P', Items.IRON_PICKAXE)
                .define('S', Items.IRON_SHOVEL)
                .define('X', Items.IRON_SWORD)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('F', ModItems.FILING_FOLDER.get())
                .unlockedBy("has_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FLUID_CANISTER.get(), 4)
                .pattern("   ")
                .pattern("BGB")
                .pattern(" B ")
                .define('B', Items.BUCKET)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_bucket", has(Items.BUCKET))
                .save(recipeOutput);

        //Archive Items
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ERASER.get())
                .pattern("  Q")
                .pattern("QR ")
                .pattern("QQ ")
                .define('Q', Items.QUARTZ)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABINET_CONVERSION_KIT.get(), 3)
                .pattern("C C")
                .pattern("BIB")
                .pattern("RBR")
                .define('C', Items.COPPER_INGOT)
                .define('B', ModItems.FLUID_CANISTER.get())
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_BLOCK)
                .unlockedBy("has_fluid_canister", has(ModItems.FLUID_CANISTER.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LEDGER.get())
                .pattern("RQR")
                .pattern("QBQ")
                .pattern("RQR")
                .define('R', Items.REDSTONE)
                .define('B', Items.BOOK)
                .define('Q', Items.QUARTZ)
                .unlockedBy("has_quartz", has(Items.QUARTZ))
                .save(recipeOutput);

        //  Range Upgrades
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.IRON_RANGE_UPGRADE.get())
                .pattern("IRI")
                .pattern("RGR")
                .pattern("IRI")
                .define('R', Items.REDSTONE)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DIAMOND_RANGE_UPGRADE.get())
                .pattern("DRD")
                .pattern("RGR")
                .pattern("DRD")
                .define('R', Items.REDSTONE)
                .define('G', ModItems.IRON_RANGE_UPGRADE.get())
                .define('D', Items.IRON_INGOT)
                .unlockedBy("has_iron_upgrade", has(ModItems.IRON_RANGE_UPGRADE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NETHERITE_RANGE_UPGRADE.get())
                .pattern("NRN")
                .pattern("RGR")
                .pattern("NRN")
                .define('R', Items.REDSTONE)
                .define('G', ModItems.DIAMOND_RANGE_UPGRADE.get())
                .define('N', Items.NETHERITE_INGOT)
                .unlockedBy("has_diamond_upgrade", has(ModItems.DIAMOND_RANGE_UPGRADE.get()))
                .save(recipeOutput);


        // Cabinets
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.FILING_CABINET.get())
                .pattern("CFC")
                .pattern("FIF")
                .pattern("CFC")
                .define('C', Items.COPPER_INGOT)
                .define('F', ModItems.FILING_FOLDER.get())
                .define('I', Items.IRON_BLOCK)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.FLUID_CABINET.get())
                .pattern("CFC")
                .pattern("FIF")
                .pattern("CFC")
                .define('C', Items.COPPER_INGOT)
                .define('F', ModItems.FLUID_CANISTER.get())
                .define('I', Items.IRON_BLOCK)
                .unlockedBy("has_fluid_canister", has(ModItems.FLUID_CANISTER.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.FILING_INDEX.get())
                .pattern("IXI")
                .pattern("RFR")
                .pattern("ICI")
                .define('R', Items.REDSTONE)
                .define('F', RFRTags.Items.CABINET_AS_ITEM)
                .define('X', Items.REPEATER)
                .define('C', Items.COMPARATOR)
                .define('I', Items.COPPER_INGOT)
                .unlockedBy("has_cabinet", has(RFRTags.Items.CABINET_AS_ITEM))
                .save(recipeOutput);

        // Dyed Folders
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.WHITE_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.WHITE_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ORANGE_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.ORANGE_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MAGENTA_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.MAGENTA_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIGHT_BLUE_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.LIGHT_BLUE_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.YELLOW_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.YELLOW_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIME_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.LIME_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.PINK_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.PINK_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GRAY_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.GRAY_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIGHT_GRAY_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.LIGHT_GRAY_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CYAN_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.CYAN_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.PURPLE_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.PURPLE_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BLUE_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.BLUE_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BROWN_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.BROWN_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GREEN_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.GREEN_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.RED_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.RED_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BLACK_FILING_FOLDER.get())
                .requires(ModItems.FILING_FOLDER.get())
                .requires(Items.BLACK_DYE)
                .unlockedBy("has_filing_folder", has(ModItems.FILING_FOLDER.get()))
                .save(recipeOutput);

        // Dyed NBT Folders
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.WHITE_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.WHITE_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ORANGE_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.ORANGE_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.MAGENTA_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.MAGENTA_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIGHT_BLUE_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.LIGHT_BLUE_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.YELLOW_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.YELLOW_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIME_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.LIME_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.PINK_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.PINK_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GRAY_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.GRAY_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIGHT_GRAY_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.LIGHT_GRAY_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CYAN_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.CYAN_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.PURPLE_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.PURPLE_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BLUE_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.BLUE_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BROWN_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.BROWN_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GREEN_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.GREEN_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.RED_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.RED_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BLACK_NBT_FILING_FOLDER.get())
                .requires(ModItems.NBT_FILING_FOLDER.get())
                .requires(Items.BLACK_DYE)
                .unlockedBy("has_nbt_filing_folder", has(ModItems.NBT_FILING_FOLDER.get()))
                .save(recipeOutput);
    }
}
