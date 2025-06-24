package com.blocklogic.realfilingreborn.component;

import com.blocklogic.realfilingreborn.RealFilingReborn;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, RealFilingReborn.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LedgerData>> LEDGER_DATA =
            DATA_COMPONENT_TYPES.register("ledger_data", () -> DataComponentType.<LedgerData>builder()
                    .persistent(LedgerData.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(LedgerData.CODEC))
                    .cacheEncoding()
                    .build());

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}