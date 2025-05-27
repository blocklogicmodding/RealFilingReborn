package com.blocklogic.realfilingreborn.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormattingCache {
    private static final Map<Integer, String> ITEM_COUNT_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, String> FLUID_AMOUNT_CACHE = new ConcurrentHashMap<>();

    private static final int MAX_CACHE_SIZE = 10000;

    public static String getFormattedItemCount(int count) {
        if (ITEM_COUNT_CACHE.size() > MAX_CACHE_SIZE) {
            ITEM_COUNT_CACHE.clear();
        }

        return ITEM_COUNT_CACHE.computeIfAbsent(count, c -> {
            if (c >= 1000000) {
                return String.format("%.1fM", c / 1000000.0);
            } else if (c >= 1000) {
                return String.format("%.1fK", c / 1000.0);
            } else {
                return String.valueOf(c);
            }
        });
    }

    public static String getFormattedFluidAmount(int amount) {
        if (FLUID_AMOUNT_CACHE.size() > MAX_CACHE_SIZE) {
            FLUID_AMOUNT_CACHE.clear();
        }

        return FLUID_AMOUNT_CACHE.computeIfAbsent(amount, a -> {
            if (a >= 1000000000) {
                float mega = a / 1000000f;
                return String.format("%.1fM", mega);
            } else if (a >= 1000000) {
                float kilo = a / 1000000f;
                return String.format("%.1fK", kilo);
            } else if (a >= 1000) {
                float buckets = a / 1000f;
                if (buckets == (int) buckets) {
                    return String.format("%d", (int) buckets);
                } else {
                    return String.format("%.1f", buckets);
                }
            } else {
                return String.valueOf(a);
            }
        });
    }

    public static void clearCaches() {
        ITEM_COUNT_CACHE.clear();
        FLUID_AMOUNT_CACHE.clear();
    }
}