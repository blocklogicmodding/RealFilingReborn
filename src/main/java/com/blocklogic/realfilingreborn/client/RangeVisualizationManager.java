package com.blocklogic.realfilingreborn.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class RangeVisualizationManager {
    private static final Map<BlockPos, Integer> ACTIVE_VISUALIZATIONS = new HashMap<>();

    public static void toggleRangeVisualization(BlockPos pos, int range) {
        if (ACTIVE_VISUALIZATIONS.containsKey(pos)) {
            ACTIVE_VISUALIZATIONS.remove(pos);
        } else {
            ACTIVE_VISUALIZATIONS.put(pos, range);
        }
    }

    public static void clearAll() {
        ACTIVE_VISUALIZATIONS.clear();
    }

    public static Map<BlockPos, Integer> getActiveVisualizations() {
        return ACTIVE_VISUALIZATIONS;
    }

    // Optional: Automatic cleanup handlers
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        ACTIVE_VISUALIZATIONS.remove(event.getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        ACTIVE_VISUALIZATIONS.keySet().removeIf(pos ->
                event.getLevel().getChunk(pos).getPos().equals(event.getChunk().getPos())
        );
    }

    public static boolean isVisualizationActive(BlockPos pos) {
        return ACTIVE_VISUALIZATIONS.containsKey(pos);
    }
}
