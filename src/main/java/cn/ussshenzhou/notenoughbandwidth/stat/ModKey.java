package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

/**
 * @author USS_Shenzhou
 */
@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID, value = Side.CLIENT)
public class ModKey {
    public static final KeyBinding STAT = new KeyBinding(
            "key.neb.stat",
            Keyboard.KEY_N,
            "notenoughbandwidth.stat"
    );

    static {
        ClientRegistry.registerKeyBinding(STAT);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (STAT.isPressed() && isAltPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new StatScreen());
        }
    }

    private static boolean isAltPressed() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }
}
