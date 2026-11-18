package com.example.chronorelic;

import com.example.chronorelic.item.ChronoWatchItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ChronoRelicMod implements ModInitializer {
    public static final String MOD_ID = "chronorelic";

    public static final ResourceKey<Item> CHRONO_WATCH_KEY = ResourceKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath(MOD_ID, "chrono_watch")
    );

    public static final Item CHRONO_WATCH = Registry.register(
        BuiltInRegistries.ITEM,
        CHRONO_WATCH_KEY,
        new ChronoWatchItem(new Item.Properties()
            .setId(CHRONO_WATCH_KEY)
            .stacksTo(1)
            .rarity(Rarity.EPIC))
    );

    @Override
    public void onInitialize() {
    }
}