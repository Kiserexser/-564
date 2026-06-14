package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import sweetie.ewaware.client.ui.clickgui.ScreenClickGUI;

public class SpeedMod implements ModInitializer {
    private boolean lastShiftPressed = false;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long window = client.getWindow().getHandle();
            boolean shiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (shiftPressed && !lastShiftPressed) {
                if (client.currentScreen == null) {
                    client.setScreen(ScreenClickGUI.getInstance());
                } else if (client.currentScreen == ScreenClickGUI.getInstance()) {
                    client.setScreen(null);
                }
            }
            lastShiftPressed = shiftPressed;
        });
    }
}
