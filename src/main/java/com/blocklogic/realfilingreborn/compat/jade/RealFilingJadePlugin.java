package com.blocklogic.realfilingreborn.compat.jade;

import com.blocklogic.realfilingreborn.block.custom.FilingCabinetBlock;
import com.blocklogic.realfilingreborn.block.custom.FilingIndexBlock;
import com.blocklogic.realfilingreborn.block.entity.FilingCabinetBlockEntity;
import com.blocklogic.realfilingreborn.block.entity.FilingIndexBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class RealFilingJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FilingCabinetProvider.INSTANCE, FilingCabinetBlockEntity.class);
        registration.registerBlockDataProvider(FilingIndexProvider.INSTANCE, FilingIndexBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FilingCabinetProvider.INSTANCE, FilingCabinetBlock.class);
        registration.registerBlockComponent(FilingIndexProvider.INSTANCE, FilingIndexBlock.class);
    }
}