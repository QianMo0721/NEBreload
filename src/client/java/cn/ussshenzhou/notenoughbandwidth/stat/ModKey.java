package cn.ussshenzhou.notenoughbandwidth.stat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ModKey {
    private static final String CATEGORY = "key.categories.nebl.stat";

    public static final KeyBinding STAT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.nebl.stat",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY
    ));

    private ModKey() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ModKey::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        while (STAT.wasPressed()) {
            if (!Screen.hasAltDown()) {
                continue;
            }
            if (ScreenStateHolder.consumeOpenedThisTick()) {
                continue;
            }
            client.setScreen(new StatScreen());
            ScreenStateHolder.markOpenedThisTick();
        }
    }

    public static final class ScreenStateHolder {
        private static boolean openedThisTick;

        private ScreenStateHolder() {
        }

        public static void markOpenedThisTick() {
            openedThisTick = true;
        }

        public static boolean consumeOpenedThisTick() {
            boolean value = openedThisTick;
            openedThisTick = false;
            return value;
        }

        public static void reset() {
            openedThisTick = false;
        }
    }
}
