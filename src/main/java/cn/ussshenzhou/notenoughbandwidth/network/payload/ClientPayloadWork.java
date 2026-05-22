package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class ClientPayloadWork {
    private ClientPayloadWork() {
    }

    public static void enqueue(Runnable runnable) {
        Client.enqueue(runnable);
    }

    public static EntityPlayer getClientPlayer() {
        return Client.getClientPlayer();
    }

    @SideOnly(Side.CLIENT)
    private static final class Client {
        private Client() {
        }

        private static void enqueue(Runnable runnable) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
            if (minecraft != null) {
                minecraft.addScheduledTask(runnable);
                return;
            }
            runnable.run();
        }

        private static EntityPlayer getClientPlayer() {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
            return minecraft == null ? null : minecraft.player;
        }
    }
}
