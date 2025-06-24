package com.blocklogic.realfilingreborn.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class FluidHelper {
    private static final Map<ResourceLocation, Boolean> VALID_FLUID_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ItemStack> FLUID_BUCKET_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> FLUID_NAME_CACHE = new ConcurrentHashMap<>();

    private static final int MAX_CACHE_SIZE = 1000;

    public static boolean isValidFluid(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }

        ResourceLocation fluidId = getFluidId(fluid);
        if (fluidId == null) {
            return false;
        }

        if (VALID_FLUID_CACHE.size() > MAX_CACHE_SIZE) {
            VALID_FLUID_CACHE.clear();
        }

        return VALID_FLUID_CACHE.computeIfAbsent(fluidId, id -> {
            if (!BuiltInRegistries.FLUID.containsKey(id)) {
                return false;
            }

            try {
                FluidType fluidType = fluid.getFluidType();
                return fluidType != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public static boolean isValidFluid(ResourceLocation fluidId) {
        if (fluidId == null) {
            return false;
        }

        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
            return isValidFluid(fluid);
        } catch (Exception e) {
            return false;
        }
    }

    public static ResourceLocation getFluidId(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }

        try {
            return fluid.builtInRegistryHolder().key().location();
        } catch (Exception e) {
            return null;
        }
    }

    public static Fluid getFluidFromId(ResourceLocation fluidId) {
        if (fluidId == null) {
            return Fluids.EMPTY;
        }

        try {
            Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
            return fluid != null ? fluid : Fluids.EMPTY;
        } catch (Exception e) {
            return Fluids.EMPTY;
        }
    }

    public static ItemStack getBucketForFluid(ResourceLocation fluidId) {
        if (fluidId == null || !isValidFluid(fluidId)) {
            return ItemStack.EMPTY;
        }

        if (FLUID_BUCKET_CACHE.size() > MAX_CACHE_SIZE) {
            FLUID_BUCKET_CACHE.clear();
        }

        return FLUID_BUCKET_CACHE.computeIfAbsent(fluidId, id -> {
            if (id.equals(Fluids.WATER.builtInRegistryHolder().key().location())) {
                return new ItemStack(Items.WATER_BUCKET);
            } else if (id.equals(Fluids.LAVA.builtInRegistryHolder().key().location())) {
                return new ItemStack(Items.LAVA_BUCKET);
            }

            try {
                Fluid fluid = BuiltInRegistries.FLUID.get(id);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    for (Item item : BuiltInRegistries.ITEM) {
                        if (item instanceof BucketItem bucketItem && bucketItem.content == fluid) {
                            return new ItemStack(item);
                        }
                    }
                }
            } catch (Exception e) {

            }

            return ItemStack.EMPTY;
        });
    }

    public static String getFluidDisplayName(ResourceLocation fluidId) {
        if (fluidId == null) {
            return "Unknown Fluid";
        }

        if (FLUID_NAME_CACHE.size() > MAX_CACHE_SIZE) {
            FLUID_NAME_CACHE.clear();
        }

        return FLUID_NAME_CACHE.computeIfAbsent(fluidId, id -> {
            if (id.equals(Fluids.WATER.builtInRegistryHolder().key().location())) {
                return "Water";
            } else if (id.equals(Fluids.LAVA.builtInRegistryHolder().key().location())) {
                return "Lava";
            }

            try {
                Fluid fluid = BuiltInRegistries.FLUID.get(id);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    try {
                        return fluid.getFluidType().getDescription().getString();
                    } catch (Exception e) {
                        return formatFluidName(id.getPath());
                    }
                }
            } catch (Exception e) {

            }

            return formatFluidName(id.getPath());
        });
    }

    private static String formatFluidName(String path) {
        return path.replace("_", " ")
                .replace("flowing", "")
                .trim()
                .substring(0, 1).toUpperCase() +
                path.replace("_", " ")
                        .replace("flowing", "")
                        .trim()
                        .substring(1);
    }

    public static boolean areFluidsCompatible(ResourceLocation fluidId1, ResourceLocation fluidId2) {
        if (fluidId1 == null || fluidId2 == null) {
            return false;
        }

        if (fluidId1.equals(fluidId2)) {
            return true;
        }

        String path1 = fluidId1.getPath().replace("flowing_", "");
        String path2 = fluidId2.getPath().replace("flowing_", "");

        return fluidId1.getNamespace().equals(fluidId2.getNamespace()) &&
                path1.equals(path2);
    }

    public static ResourceLocation getStillFluid(ResourceLocation fluidId) {
        if (fluidId == null) {
            return null;
        }

        String path = fluidId.getPath();
        if (path.startsWith("flowing_")) {
            return ResourceLocation.fromNamespaceAndPath(
                    fluidId.getNamespace(),
                    path.substring(8)
            );
        }

        return fluidId;
    }

    public static void clearCaches() {
        VALID_FLUID_CACHE.clear();
        FLUID_BUCKET_CACHE.clear();
        FLUID_NAME_CACHE.clear();
    }
}