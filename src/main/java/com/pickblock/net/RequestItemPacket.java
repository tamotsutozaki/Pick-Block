package com.pickblock.net;

import com.pickblock.PickBlockMod;
import com.pickblock.util.InvUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.Supplier;

public class RequestItemPacket {
    private final ItemStack requested;

    public RequestItemPacket(ItemStack req) {
        this.requested = req.copy();
    }

    public static void encode(RequestItemPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.requested);
    }

    public static RequestItemPacket decode(FriendlyByteBuf buf) {
        return new RequestItemPacket(buf.readItem());
    }

    public static void handle(RequestItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack prototype = msg.requested.copy();
            if (prototype.isEmpty()) return;

            // 1) Achar o terminal sem fio do Tom's no inventário do jogador
            ItemStack wireless = ItemStack.EMPTY;
            for (ItemStack s : player.getInventory().items) {
                if (s == null || s.isEmpty()) continue;
                String cls = s.getItem().getClass().getName(); // ex.: com.tom.storagemod.item.AdvWirelessTerminalItem
                if (cls.equals("com.tom.storagemod.item.AdvWirelessTerminalItem")
                        || cls.equals("com.tom.storagemod.item.WirelessTerminalItem")) {
                    wireless = s;
                    break;
                }
            }
            if (wireless.isEmpty()) {
                player.displayClientMessage(Component.literal("Terminal sem fio não encontrado no inventário."), true);
                ctx.get().setPacketHandled(true);
                return;
            }

            // 2) Tentar ler posição linkada do terminal (chaves comuns)
            int linkedX = Integer.MIN_VALUE, linkedY = Integer.MIN_VALUE, linkedZ = Integer.MIN_VALUE;
            if (wireless.hasTag()) {
                var tag = wireless.getTag();
                String[][] candidates = new String[][]{
                        {"linkPosX","linkPosY","linkPosZ"},
                        {"linkedPosX","linkedPosY","linkedPosZ"},
                        {"x","y","z"},
                        {"posX","posY","posZ"}
                };
                outer:
                for (String[] t : candidates) {
                    if (tag.contains(t[0]) && tag.contains(t[1]) && tag.contains(t[2])) {
                        linkedX = tag.getInt(t[0]);
                        linkedY = tag.getInt(t[1]);
                        linkedZ = tag.getInt(t[2]);
                        break outer;
                    }
                }
            }

            // 3) Centro da busca: posição linkada (se achou) ou o jogador
            net.minecraft.core.BlockPos center = (linkedX != Integer.MIN_VALUE)
                    ? new net.minecraft.core.BlockPos(linkedX, linkedY, linkedZ)
                    : player.blockPosition();

            // 4) Meta: stack cheio (até 64 ou max do item)
            int want = Math.min(prototype.getMaxStackSize(), 64);

            // 5) Varrer TileEntities com IItemHandler num raio
            int radius = 24; // ajuste conforme sua base
            int got = 0;
            ItemStack giving = ItemStack.EMPTY;

            var level = player.level();
            for (int dx = -radius; dx <= radius && got < want; dx++) {
                for (int dz = -radius; dz <= radius && got < want; dz++) {
                    for (int dy = -5; dy <= 5 && got < want; dy++) {
                        var pos = center.offset(dx, dy, dz);
                        var be = level.getBlockEntity(pos);
                        if (be == null) continue;

                        var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                        IItemHandler handler = cap.orElse(null);
                        if (handler == null) continue;

                        int slots = handler.getSlots();
                        for (int i = 0; i < slots && got < want; i++) {
                            ItemStack inSlot = handler.getStackInSlot(i);
                            if (inSlot.isEmpty()) continue;

                            // compara item + NBT
                            if (ItemStack.isSameItemSameTags(inSlot, prototype)) {
                                int canTake = Math.min(want - got, inSlot.getCount());
                                if (canTake <= 0) break;

                                ItemStack extracted = handler.extractItem(i, canTake, false);
                                if (extracted.isEmpty()) continue;

                                if (giving.isEmpty()) {
                                    giving = extracted.copy();
                                } else if (ItemHandlerHelper.canItemStacksStack(giving, extracted)) {
                                    giving.grow(extracted.getCount());
                                } else {
                                    InvUtil.giveToHandOrHotbar(player, giving);
                                    giving = extracted.copy();
                                }
                                got += extracted.getCount();
                            }
                        }
                    }
                }
            }

            if (got > 0 && !giving.isEmpty()) {
                InvUtil.giveToHandOrHotbar(player, giving);
                player.displayClientMessage(
                        Component.literal("Puxado da rede: " + giving.getCount() + "x " + giving.getHoverName().getString()),
                        true
                );

                // som sutil de "pegar item"
                player.level().playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS,
                        0.25f,
                        1.10f
                );
            } else {
                player.displayClientMessage(
                        Component.literal("Item não encontrado na rede/almoxarifado conectado."),
                        true
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
