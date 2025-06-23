package com.blocklogic.realfilingreborn.util;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class RFRTags {
    public static class Blocks {
        public static final TagKey<Block> CABINETS = createTag("cabinets");

        private static TagKey<Block> createTag (String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> FOLDERS = createTag("folders");
        public static final TagKey<Item> ARCHIVE_TOOLS = createTag("archive_tools");

        private static TagKey<Item> createTag (String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, name));
        }
    }
}
