package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientPayloadBridge {
    private ClientPayloadBridge() {
    }

    public static NetworkManager getClientNetworkManager() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getConnection() == null) {
            return null;
        }
        return minecraft.getConnection().getNetworkManager();
    }
}
