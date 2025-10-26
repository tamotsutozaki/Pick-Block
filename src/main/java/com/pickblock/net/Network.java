package com.pickblock.net;

import com.pickblock.PickBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PickBlockMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.registerMessage(
                id++,
                RequestItemPacket.class,
                RequestItemPacket::encode,
                RequestItemPacket::decode,
                RequestItemPacket::handle
        );
    }
}
