package com.apcraft.client;

import net.fabricmc.api.ClientModInitializer;
import com.apcraft.ApacheAircraft;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class APCraftClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(ApacheAircraft.APACHE, ApacheRenderer::new);
        KeyBinding menu = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.apcraft.menu", GLFW.GLFW_KEY_M, "category.apcraft"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menu.wasPressed()) if (client.player != null) client.setScreen(new MechanicMenuScreen());
        });
    }
}
