package com.apcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import java.util.*;

/** Prototype entity-mounted visual rig. This does not replace the vanilla boat's physics. */
public final class ApacheVisuals {
    private ApacheVisuals() {}
    private static final String APACHE = "apcraft_model_8";
    private static final String PART = "apcraft_visual_part";
    private static final Map<UUID, List<ArmorStandEntity>> RIGS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();
    private static int ticks;

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks % 2 != 0) return;
            Set<UUID> observed = new HashSet<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                Box area = player.getBoundingBox().expand(72);
                for (BoatEntity boat : world.getEntitiesByClass(BoatEntity.class, area,
                        b -> b.getCommandTags().contains(APACHE) && !(b instanceof ApacheEntity))) {
                    observed.add(boat.getUuid());
                    LAST_SEEN.put(boat.getUuid(), (long) ticks);
                    List<ArmorStandEntity> parts = RIGS.get(boat.getUuid());
                    if (parts == null || parts.size() != 9 || parts.stream().anyMatch(e -> e.isRemoved() || e.getWorld() != world)) {
                        destroy(boat.getUuid());
                        parts = spawn(world, boat);
                        RIGS.put(boat.getUuid(), parts);
                    }
                    move(boat, parts);
                }
            }
            // Unload visual rigs away from players, avoiding persistent orphan entities.
            for (UUID id : new ArrayList<>(RIGS.keySet())) {
                if (!observed.contains(id)) destroy(id);
            }
        });
    }

    private static List<ArmorStandEntity> spawn(ServerWorld world, BoatEntity boat) {
        List<ArmorStandEntity> list = new ArrayList<>();
        for (int i=0; i<9; i++) {
            ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
            if (stand == null) continue;
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.addCommandTag(PART);
            stand.addCommandTag("apcraft_visual_owner_" + boat.getUuid());
            stand.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,
                    new ItemStack(i == 0 ? Items.GRAY_CONCRETE : i <= 4 ? Items.BLACK_CONCRETE : Items.GREEN_CONCRETE));
            stand.refreshPositionAndAngles(boat.getX(),boat.getY(),boat.getZ(),0,0);
            world.spawnEntity(stand);
            list.add(stand);
        }
        return list;
    }

    private static void move(BoatEntity boat, List<ArmorStandEntity> parts) {
        if (parts.size() != 9) return;
        double yaw = Math.toRadians(-boat.getYaw());
        double spin = ticks * 0.28;
        // Nine visual markers: fuselage, four spinning rotor markers, tail boom and tail rotor.
        double[][] local = new double[9][3];
        local[0] = new double[]{0, 0.8, 0};
        for (int i=1;i<=4;i++) {
            double a=spin+(i-1)*Math.PI/2;
            local[i] = new double[]{Math.cos(a)*1.55, 2.05, Math.sin(a)*1.55};
        }
        local[5] = new double[]{0,1.1,-1.2};
        local[6] = new double[]{0,1.1,-2.2};
        local[7] = new double[]{0,1.1,-3.2};
        local[8] = new double[]{Math.sin(spin)*0.65,1.7+Math.cos(spin)*0.65,-3.3};
        for (int i=0;i<9;i++) {
            double[] v=local[i];
            double x=v[0]*Math.cos(yaw)-v[2]*Math.sin(yaw);
            double z=v[0]*Math.sin(yaw)+v[2]*Math.cos(yaw);
            ArmorStandEntity stand=parts.get(i);
            stand.refreshPositionAndAngles(boat.getX()+x,boat.getY()+v[1],boat.getZ()+z,0,0);
        }
    }
    private static void destroy(UUID id) {
        List<ArmorStandEntity> list = RIGS.remove(id);
        LAST_SEEN.remove(id);
        if(list!=null) for(ArmorStandEntity e:list) if(!e.isRemoved()) e.discard();
    }
}
