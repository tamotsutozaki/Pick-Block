package com.pickblock.net;

import com.pickblock.util.InvUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkEvent;
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

            ItemStack wireless = ItemStack.EMPTY;
            for (ItemStack s : player.getInventory().items) {
                if (s == null || s.isEmpty()) continue;
                String cls = s.getItem().getClass().getName();
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

            int linkedX = Integer.MIN_VALUE, linkedY = Integer.MIN_VALUE, linkedZ = Integer.MIN_VALUE;
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> linkedDimKey = null;

            if (wireless.hasTag()) {
                var tag = wireless.getTag();
                if (tag.contains("BindX") && tag.contains("BindY") && tag.contains("BindZ")) {
                    linkedX = tag.getInt("BindX");
                    linkedY = tag.getInt("BindY");
                    linkedZ = tag.getInt("BindZ");
                }
                if (tag.contains("BindDim")) {
                    String dimStr = tag.getString("BindDim");
                    if (!dimStr.isEmpty()) {
                        var dimRL = new net.minecraft.resources.ResourceLocation(dimStr);
                        linkedDimKey = net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION, dimRL
                        );
                    }
                }
            }

            net.minecraft.server.level.ServerLevel targetLevel = null;
            if (linkedDimKey != null && player.getServer() != null) {
                targetLevel = player.getServer().getLevel(linkedDimKey);
            }
            if (targetLevel == null) {
                targetLevel = player.serverLevel();
            }

            net.minecraft.core.BlockPos center = (linkedX != Integer.MIN_VALUE)
                    ? new net.minecraft.core.BlockPos(linkedX, linkedY, linkedZ)
                    : player.blockPosition();

            int want = Math.min(prototype.getMaxStackSize(), 64);

            var itemCap = wireless.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
            IItemHandler remote = itemCap.orElse(null);

            int got = 0;
            ItemStack giving = ItemStack.EMPTY;

            if (remote != null) {
                int slots = remote.getSlots();
                for (int i = 0; i < slots && got < want; i++) {
                    ItemStack inSlot = remote.getStackInSlot(i);
                    if (inSlot.isEmpty()) continue;
                    if (ItemStack.isSameItemSameTags(inSlot, prototype)) {
                        int canTake = Math.min(want - got, inSlot.getCount());
                        if (canTake <= 0) break;
                        ItemStack extracted = remote.extractItem(i, canTake, false);
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
            } else {
                int radius = 24;
                var level = targetLevel;
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
            }

            if (got > 0 && !giving.isEmpty()) {
                InvUtil.giveToHandOrHotbar(player, giving);
                player.displayClientMessage(
                        Component.literal("Puxado da rede: " + giving.getCount() + "x " + giving.getHoverName().getString()),
                        true
                );
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        0.25f, 1.10f);
            } else {
                player.displayClientMessage(Component.literal("Item não encontrado (ou fora de alcance da rede)."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
