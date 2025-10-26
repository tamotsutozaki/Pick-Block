package com.pickblock.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class InvUtil {
    /** insere s na mão; se não der, tenta hotbar; se não der, inventário; se não couber, dropa no chão */
    public static void giveToHandOrHotbar(ServerPlayer p, ItemStack s) {
        if (s.isEmpty()) return;
        Inventory inv = p.getInventory();

        // tentar completar mão principal
        int sel = inv.selected;
        ItemStack inHand = inv.getItem(sel);
        if (inHand.isEmpty()) {
            inv.setItem(sel, s);
            sync(p); return;
        }
        if (ItemHandlerHelper.canItemStacksStack(inHand, s)) {
            int space = inHand.getMaxStackSize() - inHand.getCount();
            int toMove = Math.min(space, s.getCount());
            if (toMove > 0) {
                inHand.grow(toMove);
                s.shrink(toMove);
                if (s.isEmpty()) { sync(p); return; }
            }
        }

        // tentar hotbar
        for (int i = 0; i < 9 && !s.isEmpty(); i++) {
            if (i == sel) continue;
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) { inv.setItem(i, s); s = ItemStack.EMPTY; break; }
            if (ItemHandlerHelper.canItemStacksStack(slot, s)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int toMove = Math.min(space, s.getCount());
                if (toMove > 0) {
                    slot.grow(toMove);
                    s.shrink(toMove);
                }
            }
        }

        // inventário geral
        if (!s.isEmpty()) {
            boolean added = p.addItem(s);
            if (!added) p.drop(s, false);
        }
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
    }
}
