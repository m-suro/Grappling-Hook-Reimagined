package me.msuro.grapplinghook.listeners;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.api.HookAPI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LoomInventory;
import org.bukkit.inventory.SmithingInventory;

public class InventoryListener implements Listener {

    private final GrapplingHook plugin;

    public InventoryListener(GrapplingHook plugin) {
        this.plugin = plugin;
    }

    /**
     * Blocks grappling hooks from being placed in anvils
     * Prevents: repairing, renaming, enchanting
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        handleInventoryClick(event, "anvil");
    }

    /**
     * Blocks grappling hooks from being placed in grindstones
     * Prevents: removing enchantments, repairing
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstoneClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory)) return;
        handleInventoryClick(event, "grindstone");
    }

    /**
     * Blocks grappling hooks from being placed in smithing tables
     * Prevents: upgrading with netherite, armor trims
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithingTableClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory)) return;
        handleInventoryClick(event, "smithing table");
    }

    /**
     * Blocks grappling hooks from being placed in enchanting tables
     * Prevents: adding enchantments
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantingTableClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory)) return;
        handleInventoryClick(event, "enchanting table");
    }

    /**
     * Blocks grappling hooks from being used in crafting (both 2x2 and 3x3)
     * Prevents: combining with other items
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.WORKBENCH) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if(event.getSlotType() != InventoryType.SlotType.CRAFTING) return;
        // Check if placing a grappling hook in crafting grid
        if (cursor != null && HookAPI.isGrapplingHook(cursor) && event.getSlot() < 9) {
            event.setCancelled(true);
            sendBlockMessage((Player) event.getWhoClicked(), "crafting");
            return;
        }

        // Check if there's already a grappling hook in crafting grid
        if (current != null && HookAPI.isGrapplingHook(current) && event.getSlot() < 9) {
            event.setCancelled(true);
            sendBlockMessage((Player) event.getWhoClicked(), "crafting");
        }
    }

    /**
     * Blocks grappling hooks from being placed in cartography tables
     * Prevents: potential future interactions with maps
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCartographyTableClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CartographyInventory)) return;
        handleInventoryClick(event, "cartography table");
    }

    /**
     * Blocks grappling hooks from being placed in looms
     * Prevents: potential interactions with banners
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLoomClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof LoomInventory)) return;
        handleInventoryClick(event, "loom");
    }
    
    /**
     * Helper method to handle inventory click events for blocking grappling hooks
     * Reduces code duplication across multiple inventory event handlers
     */
    private void handleInventoryClick(InventoryClickEvent event, String inventoryType) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Check if player is trying to place a grappling hook
        if (cursor != null && HookAPI.isGrapplingHook(cursor)) {
            event.setCancelled(true);
            sendBlockMessage((Player) event.getWhoClicked(), inventoryType);
            return;
        }

        // Check if there's already a grappling hook in the slot being clicked
        if (current != null && HookAPI.isGrapplingHook(current)) {
            event.setCancelled(true);
            sendBlockMessage((Player) event.getWhoClicked(), inventoryType);
        }
    }

    /**
     * Blocks grappling hooks from being used in prepare events
     * Additional safety net for crafting/anvil preparations
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (containsGrapplingHook(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (containsGrapplingHook(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (containsGrapplingHook(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (containsGrapplingHook(event.getInventory().getContents())) {
            event.setResult(null);
        }
    }
    
    /**
     * Helper method to check if an array of items contains a grappling hook
     * Reduces code duplication across prepare event handlers
     */
    private boolean containsGrapplingHook(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && HookAPI.isGrapplingHook(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a formatted message to the player explaining why the action was blocked
     */
    private void sendBlockMessage(Player player, String inventoryType) {
        String rawMessage = plugin.getConfig().getString("messages.inventory-combine-block")
                        .replace("[invtype]", inventoryType);
        plugin.sendFormattedMessage(player, rawMessage);
    }
}
