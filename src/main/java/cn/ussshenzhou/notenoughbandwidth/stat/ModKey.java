package cn.ussshenzhou.notenoughbandwidth.stat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
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
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            Keyboard.KEY_N,
            "key.categories.notenoughbandwidth.stat"
    );
    public static final KeyBinding DEBUG = new KeyBinding(
            "key.neb.debug",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            Keyboard.KEY_P,
            "key.categories.notenoughbandwidth.stat"
    );

    private static boolean initialized;

    public static void register() {
        if (!initialized) {
            ClientRegistry.registerKeyBinding(STAT);
            ClientRegistry.registerKeyBinding(DEBUG);
            initialized = true;
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (STAT.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new StatScreen());
        }
        if (DEBUG.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new DebugStatScreen());
        }
    }
}
