package cn.ussshenzhou.notenoughbandwidth.stat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * @author USS_Shenzhou
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModKey {
    public static final KeyMapping STAT = new KeyMapping(
            "key.neb.stat", KeyConflictContext.UNIVERSAL, KeyModifier.ALT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.neb"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (STAT.consumeClick()) {
            Minecraft.getInstance().setScreen(new StatScreen());
        }
    }
}
