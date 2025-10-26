package com.pickblock.client;

import com.pickblock.PickBlockMod;
import com.pickblock.net.Network;
import com.pickblock.net.RequestItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEvents {

    private static boolean wasPickDown = false;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        // tecla "Pick Block"
        var key = mc.options.keyPickItem;
        boolean isDown = key.isDown();
        boolean pressedNow = isDown && !wasPickDown;
        wasPickDown = isDown;

        if (!pressedNow) return;
        if (mc.player.isCreative()) return; // no criativo deixa o vanilla agir

        // estamos no survival → tenta identificar o item do bloco mirado
        if (mc.hitResult instanceof BlockHitResult bhr) {
            var state = mc.level.getBlockState(bhr.getBlockPos());
            ItemStack targeted = state.getBlock().asItem().getDefaultInstance();
            if (!targeted.isEmpty()) {
                // manda pro servidor: “tenta me dar esse item (stack cheio)”
                Network.CHANNEL.sendToServer(new RequestItemPacket(targeted));
                // dica: não cancelamos o comportamento vanilla — no survival o vanilla
                // só pega se já tiver no inventário; nosso servidor vai completar a pilha.
            }
        }
    }
}
