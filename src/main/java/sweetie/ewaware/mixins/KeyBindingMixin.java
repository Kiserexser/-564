package sweetie.ewaware.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sweetie.ewaware.client.ui.clickgui.ScreenClickGUI;

@Mixin(MinecraftClient.class)
public class KeyBindingMixin {
    private long lastShiftPress = 0;

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void onHandleInput(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long window = mc.getWindow().getHandle();

        boolean shiftPressed = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (shiftPressed) {
            long now = System.currentTimeMillis();
            if (now - lastShiftPress > 150) {
                lastShiftPress = now;
                if (mc.currentScreen == null) {
                    mc.setScreen(ScreenClickGUI.getInstance());
                } else if (mc.currentScreen == ScreenClickGUI.getInstance()) {
                    mc.setScreen(null);
                }
            }
        }
    }
}
