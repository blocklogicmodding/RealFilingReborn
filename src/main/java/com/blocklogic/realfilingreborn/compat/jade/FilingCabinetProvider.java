
package com.blocklogic.realfilingreborn.compat.jade;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.item.custom.FilingFolderItem;
import com.blocklogic.realfilingreborn.item.custom.NBTFilingFolderItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.ProgressStyle;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

public enum FilingCabinetProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "filing_cabinet_info");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("folders")) {
            return;
        }

        ListTag folders = data.getList("folders", CompoundTag.TAG_COMPOUND);
        if (folders.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.realfilingreborn.jade.empty_cabinet")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // Process each folder
        for (int i = 0; i < folders.size(); i++) {
            CompoundTag folderTag = folders.getCompound(i);

            if (folderTag.contains("item_id")) {
                int slot = folderTag.getInt("slot");
                String itemName = folderTag.getString("item_name");

                if (folderTag.getBoolean("is_nbt")) {
                    int uniqueCount = folderTag.getInt("unique_count");
                    tooltip.add(Component.literal("Folder " + (slot + 1) + " (NBT): " + itemName + " (" + uniqueCount + " items)")
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    int count = folderTag.getInt("count");
                    String formattedCount = NumberFormat.getNumberInstance(Locale.US).format(count);
                    double fillPercentage = ((double) count / Integer.MAX_VALUE) * 100.0;
                    String percentText = String.format("%.2f%%", fillPercentage);

                    tooltip.add(Component.literal("Folder " + (slot + 1) + ": " + itemName + " (" + formattedCount + ", " + percentText + ")")
                            .withStyle(ChatFormatting.WHITE));
                }
            }
        }

        if (data.getBoolean("index_linked")) {
            tooltip.add(Component.literal(" ")); // Single spacer line
            tooltip.add(Component.literal("Linked to Filing Index")
                    .withStyle(ChatFormatting.GREEN));

            if (data.contains("index_pos")) {
                tooltip.add(Component.literal("at " + data.getString("index_pos"))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }


    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FilingCabinetBlockEntity cabinetEntity)) {
            return;
        }

        ListTag foldersList = new ListTag();

        // Process each folder slot (0-11 for folders)
        for (int i = 0; i < 12; i++) {
            ItemStack stackInSlot = cabinetEntity.inventory.getStackInSlot(i);
            CompoundTag folderTag = new CompoundTag();
            folderTag.putInt("slot", i);

            if (!stackInSlot.isEmpty()) {
                if (stackInSlot.getItem() instanceof FilingFolderItem && !(stackInSlot.getItem() instanceof NBTFilingFolderItem)) {
                    // Regular Filing Folder
                    FilingFolderItem.FolderContents contents = stackInSlot.get(FilingFolderItem.FOLDER_CONTENTS.value());

                    if (contents != null && contents.storedItemId().isPresent()) {
                        ResourceLocation itemId = contents.storedItemId().get();

                        folderTag.putString("item_id", itemId.toString());
                        folderTag.putString("item_name", BuiltInRegistries.ITEM.get(itemId).getDescription().getString());
                        folderTag.putInt("count", contents.count());
                        folderTag.putBoolean("is_nbt", false);
                    }
                } else if (stackInSlot.getItem() instanceof NBTFilingFolderItem) {
                    // NBT Filing Folder
                    NBTFilingFolderItem.NBTFolderContents contents =
                            stackInSlot.get(NBTFilingFolderItem.NBT_FOLDER_CONTENTS.value());

                    if (contents != null && contents.storedItemId().isPresent()) {
                        ResourceLocation itemId = contents.storedItemId().get();

                        folderTag.putString("item_id", itemId.toString());
                        folderTag.putString("item_name", BuiltInRegistries.ITEM.get(itemId).getDescription().getString());
                        folderTag.putInt("unique_count", contents.storedItems().size());
                        folderTag.putBoolean("is_nbt", true);
                    }
                }
            }

            foldersList.add(folderTag);
        }

        data.put("folders", foldersList);

        // Check for index card
        ItemStack indexCardStack = cabinetEntity.inventory.getStackInSlot(12);
        if (!indexCardStack.isEmpty()) {
            data.putBoolean("index_linked", true);

            // Get the position of the linked index if available
            var indexPos = indexCardStack.get(com.blocklogic.realfilingreborn.component.ModDataComponents.COORDINATES);
            if (indexPos != null) {
                data.putString("index_pos", indexPos.toShortString());
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}