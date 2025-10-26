package com.pickblock.net;

import com.pickblock.PickBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel; // <- IMPORT CERTO

public class Network {

    private static final String PROTOCOL_VERSION = "1";

    // pode ser final; o canal é imutável após criado
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PickBlockMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.registerMessage(
                id++,                           // id incremental
                RequestItemPacket.class,        // classe da mensagem
                RequestItemPacket::encode,      // encoder (FriendlyByteBuf)
                RequestItemPacket::decode,      // decoder
                RequestItemPacket::handle       // handler (lado servidor)
        );
    }
}
