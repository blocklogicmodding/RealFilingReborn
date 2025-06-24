package com.blocklogic.realfilingreborn.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public record LedgerData(
        OperationMode operationMode,
        SelectionMode selectionMode,
        @Nullable BlockPos selectedController,
        @Nullable BlockPos firstMultiPos
) {

    public static final Codec<LedgerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    OperationMode.CODEC.optionalFieldOf("operationMode", OperationMode.ADD).forGetter(LedgerData::operationMode),
                    SelectionMode.CODEC.optionalFieldOf("selectionMode", SelectionMode.SINGLE).forGetter(LedgerData::selectionMode),
                    BlockPos.CODEC.optionalFieldOf("selectedController").forGetter(data ->
                            java.util.Optional.ofNullable(data.selectedController())),
                    BlockPos.CODEC.optionalFieldOf("firstMultiPos").forGetter(data ->
                            java.util.Optional.ofNullable(data.firstMultiPos()))
            ).apply(instance, (opMode, selMode, controller, multiPos) ->
                    new LedgerData(opMode, selMode, controller.orElse(null), multiPos.orElse(null)))
    );

    public static final LedgerData DEFAULT = new LedgerData(
            OperationMode.ADD,
            SelectionMode.SINGLE,
            null,
            null
    );

    public LedgerData withOperationMode(OperationMode operationMode) {
        return new LedgerData(operationMode, this.selectionMode, this.selectedController, this.firstMultiPos);
    }

    public LedgerData withSelectionMode(SelectionMode selectionMode) {
        return new LedgerData(this.operationMode, selectionMode, this.selectedController, this.firstMultiPos);
    }

    public LedgerData withSelectedController(@Nullable BlockPos selectedController) {
        return new LedgerData(this.operationMode, this.selectionMode, selectedController, this.firstMultiPos);
    }

    public LedgerData withFirstMultiPos(@Nullable BlockPos firstMultiPos) {
        return new LedgerData(this.operationMode, this.selectionMode, this.selectedController, firstMultiPos);
    }

    public enum OperationMode {
        ADD,
        REMOVE;

        public static final Codec<OperationMode> CODEC = Codec.stringResolver(
                OperationMode::name,
                name -> {
                    try {
                        return OperationMode.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return ADD;
                    }
                }
        );
    }

    public enum SelectionMode {
        SINGLE,
        MULTI;

        public static final Codec<SelectionMode> CODEC = Codec.stringResolver(
                SelectionMode::name,
                name -> {
                    try {
                        return SelectionMode.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return SINGLE;
                    }
                }
        );
    }
}