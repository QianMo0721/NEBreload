package cn.ussshenzhou.notenoughbandwidth.client;

import cn.ussshenzhou.notenoughbandwidth.CommonProxy;
import cn.ussshenzhou.notenoughbandwidth.stat.ModKey;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new ModKey());
    }
}
