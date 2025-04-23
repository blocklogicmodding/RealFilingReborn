package com.blocklogic.realfilingreborn.compat.jade;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.text.NumberFormat;
import java.util.Locale;

public enum FilingIndexProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RealFilingReborn.MODID, "filing_index_info");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("folders")) {
            return;
        }

        ListTag folders = data.getList("folders", CompoundTag.TAG_COMPOUND);
        if (folders.isEmpty()) {
            tooltip.append(Component.translatable("tooltip.realfilingreborn.jade.empty_cabinet")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.append(Component.translatable("tooltip.realfilingreborn.jade.cabinet_title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (int i = 0; i < folders.size(); i++) {
            CompoundTag folderTag = folders.getCompound(i);

            if (folderTag.contains("item_id")) {
                String itemId = folderTag.getString("item_id");
                int count = folderTag.getInt("count");
                boolean isNBT = folderTag.getBoolean("is_nbt");
                int slot = folderTag.getInt("slot");
                String itemName = folderTag.getString("item_name");

                if (isNBT) {
                    int uniqueCount = folderTag.getInt("unique_count");
                    tooltip.append(Component.literal("")
                            .append(Component.translatable("tooltip.realfilingreborn.jade.nbt_folder_short",
                                            slot + 1, itemName, uniqueCount)
                                    .withStyle(ChatFormatting.AQUA)));
                } else {
                    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
                    String formattedCount = formatter.format(count);
                    double fillPercentage = ((double) count / Integer.MAX_VALUE) * 100.0;
                    String percentText = String.format("%.2f%%", fillPercentage);

                    tooltip.append(Component.literal("")
                            .append(Component.translatable("tooltip.realfilingreborn.jade.folder_short",
                                            slot + 1, itemName, formattedCount, percentText)
                                    .withStyle(ChatFormatting.WHITE)));
                }
            }
        }

        if (data.contains("index_linked") && data.getBoolean("index_linked")) {
            tooltip.append(Component.literal("")
                    .append(Component.translatable("tooltip.realfilingreborn.jade.index_linked")
                            .withStyle(ChatFormatting.GREEN)));

            if (data.contains("index_pos")) {
                String pos = data.getString("index_pos");
                tooltip.append(Component.literal("")
                        .append(Component.translatable("tooltip.realfilingreborn.jade.index_pos", pos)
                                .withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FilingIndexBlockEntity indexEntity)) {
            return;
        }

        int cabinetCount = indexEntity.getCabinetCount();
        data.putInt("cabinet_count", cabinetCount);

        if (cabinetCount > 0) {
            int folderCount = 0;
            long totalItems = 0;

            var handlers = indexEntity.getCabinetItemHandlers();
            for (var handler : handlers) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!handler.getStackInSlot(i).isEmpty()) {
                        folderCount++;
                        totalItems += handler.getStackInSlot(i).getCount();
                    }
                }
            }

            data.putInt("folder_count", folderCount);
            data.putLong("total_items", totalItems);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}