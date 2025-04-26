package com.blocklogic.realfilingreborn.client;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.component.ModDataComponents;
import com.blocklogic.realfilingreborn.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

public class ModItemProperties {
    public static void  addCustomItemProperties() {
        ItemProperties.register(ModItems.INDEX_CARD.get(), ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "linked"),
               (itemStack, clientLevel, livingEntity, seed) ->  itemStack.get(ModDataComponents.COORDINATES) != null ? 1f : 0f);
    }
}
