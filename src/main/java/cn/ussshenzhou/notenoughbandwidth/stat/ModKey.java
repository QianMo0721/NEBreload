package cn.ussshenzhou.notenoughbandwidth.stat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class ModKey {
    public static final KeyBinding STAT = new KeyBinding(
            "key.neb.stat",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            Keyboard.KEY_N,
            "key.categories.notenoughbandwidth.stat"
    );
    public static final KeyBinding DEBUG = new KeyBinding(
            "key.neb.debug",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            Keyboard.KEY_P,
            "key.categories.notenoughbandwidth.stat"
    );

    private static boolean initialized;

    private static void ensureRegistered() {
        if (!initialized) {
            ClientRegistry.registerKeyBinding(STAT);
            ClientRegistry.registerKeyBinding(DEBUG);
            initialized = true;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        ensureRegistered();
        if (STAT.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new StatScreen());
        }
        if (DEBUG.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new DebugStatScreen());
        }
    }

    @SubscribeEvent
    public static void onGuiKeyboard(GuiScreenEvent.KeyboardInputEvent.Post event) {
        ensureRegistered();
    }
}
