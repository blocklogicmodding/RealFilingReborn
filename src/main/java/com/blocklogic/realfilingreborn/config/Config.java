package com.blocklogic.realfilingreborn.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Config {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static ModConfigSpec COMMON_CONFIG;

    public static ModConfigSpec SPEC;

    // ========================================
    // CATEGORY CONSTANTS
    // ========================================

    public static final String CATEGORY_STORAGE_LIMITS = "storage_limits";
    public static final String CATEGORY_FILING_INDEX_RANGES = "filing_index_ranges";

    // ========================================
    // STORAGE LIMITS CONFIGURATION
    // ========================================

    public static ModConfigSpec.IntValue MAX_FOLDER_STORAGE;
    public static ModConfigSpec.IntValue MAX_NBT_FOLDER_STORAGE;
    public static ModConfigSpec.IntValue MAX_CANISTER_STORAGE;

    // ========================================
    // FILING INDEX RANGES CONFIGURATION
    // ========================================

    public static ModConfigSpec.IntValue FILING_INDEX_BASE_RANGE;
    public static ModConfigSpec.IntValue IRON_RANGE_UPGRADE;
    public static ModConfigSpec.IntValue DIAMOND_RANGE_UPGRADE;
    public static ModConfigSpec.IntValue NETHERITE_RANGE_UPGRADE;

    public static void register(ModContainer container) {
        registerCommonConfigs(container);
    }

    private static void registerCommonConfigs(ModContainer container) {
        storageLimitsConfig();
        filingIndexRangesConfig();
        COMMON_CONFIG = COMMON_BUILDER.build();
        SPEC = COMMON_CONFIG; // Legacy compatibility
        container.registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
    }

    // ========================================
    // CONFIGURATION CATEGORY METHODS
    // ========================================

    private static void storageLimitsConfig() {
        COMMON_BUILDER.comment("Storage Limits - Configure maximum storage capacities for folders and canisters").push(CATEGORY_STORAGE_LIMITS);

        MAX_FOLDER_STORAGE = COMMON_BUILDER.comment("Maximum items storable in filing folders",
                        "Default: " + Integer.MAX_VALUE + " (unlimited)",
                        "Minimum: 4096 (64 stacks)")
                .defineInRange("max_folder_storage", Integer.MAX_VALUE, 4096, Integer.MAX_VALUE);

        MAX_NBT_FOLDER_STORAGE = COMMON_BUILDER.comment("Maximum items storable in NBT filing folders",
                        "NBT folders store items with enchantments, custom names, etc.",
                        "Lower limit due to complexity of NBT data storage",
                        "WARNING: Large numbers of NBT items in a single folder could corrupt the save!",
                        "Proceed with caution!",
                        "",
                        "Default: 128 items")
                .defineInRange("max_nbt_folder_storage", 128, 16, 200);

        MAX_CANISTER_STORAGE = COMMON_BUILDER.comment("Maximum fluid storable in canisters (millibuckets)",
                        "Default: " + Integer.MAX_VALUE + " (unlimited)",
                        "Minimum: 64000 (64 buckets)")
                .defineInRange("max_canister_storage", Integer.MAX_VALUE, 64000, Integer.MAX_VALUE);

        COMMON_BUILDER.pop();
    }

    private static void filingIndexRangesConfig() {
        COMMON_BUILDER.comment("Filing Index Ranges - Configure range limits for filing indexes and upgrades").push(CATEGORY_FILING_INDEX_RANGES);

        FILING_INDEX_BASE_RANGE = COMMON_BUILDER.comment("Base filing index range (blocks)",
                        "This is the default range when no upgrades are installed",
                        "Range covers radius around the filing index")
                .defineInRange("base_range", 8, 4, 32);

        IRON_RANGE_UPGRADE = COMMON_BUILDER.comment("Iron upgrade range (blocks)",
                        "Range when iron range upgrade is installed",
                        "Replaces base range when upgrade is present")
                .defineInRange("iron_range", 16, 8, 64);

        DIAMOND_RANGE_UPGRADE = COMMON_BUILDER.comment("Diamond upgrade range (blocks)",
                        "Range when diamond range upgrade is installed",
                        "Replaces base range when upgrade is present")
                .defineInRange("diamond_range", 32, 16, 128);

        NETHERITE_RANGE_UPGRADE = COMMON_BUILDER.comment("Netherite upgrade range (blocks)",
                        "Range when netherite range upgrade is installed",
                        "Replaces base range when upgrade is present",
                        "",
                        "WARNING: Large ranges can impact server performance significantly!",
                        "A range of 64 covers a 128x128 block area - use with caution on multiplayer servers",
                        "Maximum range of 256 covers a 512x512 area and may cause severe lag!")
                .defineInRange("netherite_range", 64, 32, 256);

        COMMON_BUILDER.pop();
    }

    // ========================================
    // GETTER METHODS FOR STORAGE LIMITS
    // ========================================

    public static int getMaxFolderStorage() {
        return MAX_FOLDER_STORAGE.get();
    }

    public static int getMaxNBTFolderStorage() {
        return MAX_NBT_FOLDER_STORAGE.get();
    }

    public static int getMaxCanisterStorage() {
        return MAX_CANISTER_STORAGE.get();
    }

    // ========================================
    // GETTER METHODS FOR FILING INDEX RANGES
    // ========================================

    public static int getFilingIndexBaseRange() {
        return FILING_INDEX_BASE_RANGE.get();
    }

    public static int getIronRangeUpgrade() {
        return IRON_RANGE_UPGRADE.get();
    }

    public static int getDiamondRangeUpgrade() {
        return DIAMOND_RANGE_UPGRADE.get();
    }

    public static int getNetheriteRangeUpgrade() {
        return NETHERITE_RANGE_UPGRADE.get();
    }

    // ========================================
    // VALIDATION METHODS
    // ========================================

    public static void validateConfig() {
        if (getMaxFolderStorage() < 4096) {
            LOGGER.warn("Folder storage limit ({}) is below recommended minimum of 4096 items", getMaxFolderStorage());
        }

        if (getMaxNBTFolderStorage() < 16) {
            LOGGER.warn("NBT folder storage limit ({}) is below minimum of 16 items", getMaxNBTFolderStorage());
        }

        if (getMaxCanisterStorage() < 64000) {
            LOGGER.warn("Canister storage limit ({}) is below recommended minimum of 64000mb", getMaxCanisterStorage());
        }

        if (getIronRangeUpgrade() <= getFilingIndexBaseRange()) {
            LOGGER.warn("Iron range upgrade ({}) should be greater than base range ({})", getIronRangeUpgrade(), getFilingIndexBaseRange());
        }

        if (getDiamondRangeUpgrade() <= getIronRangeUpgrade()) {
            LOGGER.warn("Diamond range upgrade ({}) should be greater than iron range ({})", getDiamondRangeUpgrade(), getIronRangeUpgrade());
        }

        if (getNetheriteRangeUpgrade() <= getDiamondRangeUpgrade()) {
            LOGGER.warn("Netherite range upgrade ({}) should be greater than diamond range ({})", getNetheriteRangeUpgrade(), getDiamondRangeUpgrade());
        }

        if (getNetheriteRangeUpgrade() > 128) {
            LOGGER.warn("Netherite range ({}) is very large and may impact server performance! Consider reducing for multiplayer servers.", getNetheriteRangeUpgrade());
        }

        if (getNetheriteRangeUpgrade() > 200) {
            LOGGER.error("Netherite range ({}) is extremely large! This WILL cause severe performance issues on multiplayer servers!", getNetheriteRangeUpgrade());
        }
    }

    public static void loadConfig() {
        LOGGER.info("Real Filing Reborn configs reloaded");
        validateConfig();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        LOGGER.info("Real Filing Reborn configuration loaded");
        logConfigValues();
        validateConfig();
    }

    private static void logConfigValues() {
        LOGGER.info("Storage Limits Configuration:");
        LOGGER.info("  Max Folder Storage: {} items", getMaxFolderStorage() == Integer.MAX_VALUE ? "unlimited" : String.format("%,d", getMaxFolderStorage()));
        LOGGER.info("  Max NBT Folder Storage: {} items", String.format("%,d", getMaxNBTFolderStorage()));
        LOGGER.info("  Max Canister Storage: {}mb", getMaxCanisterStorage() == Integer.MAX_VALUE ? "unlimited" : String.format("%,d", getMaxCanisterStorage()));

        LOGGER.info("Filing Index Ranges Configuration:");
        LOGGER.info("  Base Range: {} blocks ({}x{} area)", getFilingIndexBaseRange(), getFilingIndexBaseRange() * 2, getFilingIndexBaseRange() * 2);
        LOGGER.info("  Iron Upgrade: {} blocks ({}x{} area)", getIronRangeUpgrade(), getIronRangeUpgrade() * 2, getIronRangeUpgrade() * 2);
        LOGGER.info("  Diamond Upgrade: {} blocks ({}x{} area)", getDiamondRangeUpgrade(), getDiamondRangeUpgrade() * 2, getDiamondRangeUpgrade() * 2);
        LOGGER.info("  Netherite Upgrade: {} blocks ({}x{} area)", getNetheriteRangeUpgrade(), getNetheriteRangeUpgrade() * 2, getNetheriteRangeUpgrade() * 2);

        int netheriteArea = (getNetheriteRangeUpgrade() * 2) * (getNetheriteRangeUpgrade() * 2);
        if (netheriteArea > 65536) {
            LOGGER.warn("Netherite upgrade covers {} blocks - this is a very large area!", String.format("%,d", netheriteArea));
        }
    }
}