package com.apcraft;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registers the Apache as a distinct Minecraft entity rather than a tagged vanilla boat. */
public final class ApacheAircraft {
    private ApacheAircraft() {}
    public static final EntityType<ApacheEntity> APACHE = Registry.register(
        Registries.ENTITY_TYPE, new Identifier("apcraft", "apache"),
        EntityType.Builder.<ApacheEntity>create(ApacheEntity::new, SpawnGroup.MISC)
            .setDimensions(2.6f, 1.7f).maxTrackingRange(12).trackingTickInterval(2)
            .build("apcraft:apache"));
    public static void initialize() { /* force static registration during mod startup */ }
}
