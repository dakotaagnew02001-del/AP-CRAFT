package com.apcraft;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.world.World;

/** Dedicated Apache entity type. Reuses boat movement until custom flight physics are implemented. */
public class ApacheEntity extends BoatEntity {
    public ApacheEntity(EntityType<? extends BoatEntity> type, World world) {
        super(type, world);
    }
}
