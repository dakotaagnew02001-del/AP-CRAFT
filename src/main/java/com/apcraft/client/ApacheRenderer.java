package com.apcraft.client;

import com.apcraft.ApacheEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.MathHelper;

/** Dedicated client renderer; no boat mesh or armor-stand visual rig. */
public final class ApacheRenderer extends EntityRenderer<ApacheEntity> {
    private static final Identifier TEXTURE = new Identifier("apcraft", "textures/entity/apache.png");
    private final ApacheModel model;
    public ApacheRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        model = new ApacheModel(ApacheModel.mesh().createModel());
        shadowRadius = 1.6f;
    }
    @Override public Identifier getTexture(ApacheEntity entity) { return TEXTURE; }
    @Override public void render(ApacheEntity entity,float yaw,float tickDelta,MatrixStack matrices,
                                 VertexConsumerProvider consumers,int light) {
        matrices.push();
        matrices.translate(0,1.35,0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f-yaw));
        matrices.scale(0.075f,-0.075f,-0.075f);
        boolean rotorDamaged = entity.getCommandTags().stream().anyMatch(s -> s.startsWith("apcraft_ah64_main_rotor_") &&
            parseCondition(s) < 30);
        float speed = rotorDamaged ? 0.1f : entity.hasPassengers() ? 1.0f : 0.12f;
        float age = entity.age + tickDelta;
        VertexConsumer vertex = consumers.getBuffer(RenderLayer.getEntityCutout(TEXTURE));
        model.render(matrices,vertex,light,OverlayTexture.DEFAULT_UV,age,speed);
        matrices.pop();
        super.render(entity,yaw,tickDelta,matrices,consumers,light);
    }
    private static int parseCondition(String tag) {
        try { return Integer.parseInt(tag.substring(tag.lastIndexOf('_')+1)); }
        catch (NumberFormatException ex) { return 100; }
    }
}
