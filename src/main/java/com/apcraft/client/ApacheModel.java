package com.apcraft.client;

import com.apcraft.ApacheEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** Low-poly textured helicopter mesh. Dimensions are stylized, not engineering drawings. */
public final class ApacheModel {
    private final ModelPart root;
    private final ModelPart mainRotor;
    private final ModelPart tailRotor;
    public ApacheModel(ModelPart root) {
        this.root = root;
        this.mainRotor = root.getChild("main_rotor");
        this.tailRotor = root.getChild("tail_rotor");
    }
    public static TexturedModelData mesh() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("fuselage", ModelPartBuilder.create()
            .uv(0,0).cuboid(-8,-10,-18,16,12,34)
            .uv(0,48).cuboid(-6,-14,-11,12,5,15)
            .uv(64,0).cuboid(-6,-9,-27,12,7,10), ModelTransform.NONE);
        root.addChild("canopy", ModelPartBuilder.create()
            .uv(64,24).cuboid(-5,-13,-23,10,5,15),ModelTransform.NONE);
        root.addChild("tail_boom", ModelPartBuilder.create()
            .uv(0,75).cuboid(-3,-8,16,6,6,29)
            .uv(70,45).cuboid(-2,-18,40,4,13,5),ModelTransform.NONE);
        root.addChild("left_wing", ModelPartBuilder.create()
            .uv(0,112).cuboid(-23,-4,-5,16,2,11),ModelTransform.NONE);
        root.addChild("right_wing", ModelPartBuilder.create()
            .uv(0,112).cuboid(7,-4,-5,16,2,11),ModelTransform.NONE);
        root.addChild("landing_gear", ModelPartBuilder.create()
            .uv(64,65).cuboid(-9,1,-7,3,7,3)
            .uv(64,65).cuboid(6,1,-7,3,7,3)
            .uv(64,65).cuboid(-2,1,12,4,7,4),ModelTransform.NONE);
        root.addChild("mast", ModelPartBuilder.create()
            .uv(90,65).cuboid(-2,-20,-2,4,9,4),ModelTransform.NONE);
        root.addChild("main_rotor",ModelPartBuilder.create()
            .uv(0,95).cuboid(-42,-1,-2,84,2,4)
            .uv(0,101).cuboid(-2,-1,-42,4,2,84),ModelTransform.pivot(0,-21,0));
        root.addChild("tail_rotor",ModelPartBuilder.create()
            .uv(0,95).cuboid(-1,-11,-1,2,22,2)
            .uv(0,95).cuboid(-1,-1,-11,2,2,22),ModelTransform.pivot(3,-13,43));
        return TexturedModelData.of(data,128,128);
    }
    public void render(MatrixStack matrices,VertexConsumer consumer,int light,int overlay,float age,float rpmFactor) {
        mainRotor.yaw = age * rpmFactor * 0.65f;
        tailRotor.roll = age * rpmFactor * 1.6f;
        root.render(matrices,consumer,light,overlay);
    }
}
