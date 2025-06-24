package com.blocklogic.realfilingreborn.datagen;

import com.blocklogic.realfilingreborn.block.ModBlocks;
import com.blocklogic.realfilingreborn.item.ModItems;
import com.blocklogic.realfilingreborn.util.RFRTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
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
    }
}
