/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.transfer.access;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

class InfiniteMaterialsItemAccess implements ItemAccess, nfcompat.bridge.PlayerLedgerAccess {
    private final Player player;
    private final ResourceHandler<ItemResource> mainSlots;
    private final ItemResource resource;
    private final int amount;

    InfiniteMaterialsItemAccess(Player player, ItemResource resource, int amount) {
        this.player = player;
        this.mainSlots = PlayerInventoryWrapper.of(player).getMainSlots();
        this.resource = resource;
        this.amount = amount;
    }

    @Override
    public ItemResource getResource() {
        return resource;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        if (amount > 0 && !ResourceHandlerUtil.contains(mainSlots, resource)) {
            // Only add the item to the player inventory if it's not already in the inventory.
            mainSlots.insert(resource, 1, transaction);
        }

        // Insertion always succeeds from the POV of the access' user.
        return amount;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        // Extraction always succeeds from the POV of the access' user.
        return amount;
    }

    /**
     * Только для шима nfcompat: владелец доступа. В креативе ItemAccess создаётся
     * именно так (ItemAccess.forPlayerInteraction), поэтому перенос остатка жидкости
     * должен работать и здесь.
     */
    @Override
    public Player nfcompat$player() {
        return player;
    }
}
