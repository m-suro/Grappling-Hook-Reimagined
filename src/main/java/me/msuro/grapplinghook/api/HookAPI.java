package me.msuro.grapplinghook.api;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.GrapplingHookType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.List;

public final class HookAPI {
	
	public static boolean isGrapplingHook(ItemStack is) {
		ItemMeta im = is.getItemMeta();
		if(is.getType() == Material.FISHING_ROD && im != null) {
			PersistentDataContainer persistentData = im.getPersistentDataContainer();
            try {
                int uses = persistentData.get(new NamespacedKey(GrapplingHook.getPlugin(), "uses"), PersistentDataType.INTEGER);
                return (uses >= 0 && persistentData.has(new NamespacedKey(GrapplingHook.getPlugin(), "id"), PersistentDataType.INTEGER));
            } catch (NullPointerException e) {
                return false;
            }
		}
		return false;
	}

    public static boolean canHookOntoBlock(GrapplingHookType hookType, Block block) {
        String blocksMode = hookType.getBlocksMode();
        List<String> blocksList = hookType.getBlocksList();
        String blockTypeName = block.getType().name();

        //Bukkit.broadcastMessage("Checking if hook `" + hookType.getId() + "` can hook onto block: " + blockTypeName + " [List mode = " + blocksMode + "]");
        //Bukkit.broadcastMessage("Blocks list: " + blocksList);

        // Use case-insensitive check with Set for better performance
        boolean blockInList = false;
        for (String blockType : blocksList) {
            if (blockType.equalsIgnoreCase(blockTypeName)) {
                blockInList = true;
                break;
            }
        }

        switch (blocksMode.toUpperCase()) {
            case "ALLOW_ONLY":
                return blockInList;
            case "BLOCK_ONLY":
                return !blockInList;
            default:
                Bukkit.getLogger().warning("Invalid blocks mode: " + blocksMode +
                        ". Valid modes are ALLOW_ONLY and BLOCK_ONLY. Defaulting to BLOCK_ONLY behavior.");
                return true; // Default to allowing hooks (safer default)
        }
    }

    public static boolean canHookOntoEntity(GrapplingHookType hookType, EntityType entityType) {
        String entitiesMode = hookType.getEntityMode();
        List<String> entitiesList = hookType.getEntityList();
        String entityTypeName = entityType.name();

        // Use case-insensitive check with simple loop for better performance
        boolean entityInList = false;
        for (String entity : entitiesList) {
            if (entity.equalsIgnoreCase(entityTypeName)) {
                entityInList = true;
                break;
            }
        }

        switch (entitiesMode.toUpperCase()) {
            case "ALLOW_ONLY":
                return entityInList;
            case "BLOCK_ONLY":
                return !entityInList;
            default:
                Bukkit.getLogger().warning("Invalid entities mode: " + entitiesMode +
                        ". Valid modes are ALLOW_ONLY and BLOCK_ONLY. Defaulting to BLOCK_ONLY behavior.");
                return true; // Default to allowing hooks (safer default)
        }
    }

    /**
     * Checks if the grappling hook can be used by the player.
     * Checks:
     * - Uses limit (if set)
     * - Cooldown (if set)
     * @param hookType The type of the grappling hook.
     * @param player The player using the hook.
     * @return null if the hook can be used, otherwise a string indicating the reason why it cannot be used:
     * - "USE_LIMIT_REACHED" if the hook has reached its maximum uses.
     * - "COOLDOWN_ACTIVE" if the hook is on cooldown.
        */
    public static String canUse(GrapplingHookType hookType, Player player) {
        if (hookType.getMaxUses() != -1) {
            int uses = hookType.getUses();
            if (uses >= hookType.getMaxUses()) {
                return "USE_LIMIT_REACHED";
            }
        }
        PersistentDataContainer persistentData = player.getPersistentDataContainer();
        NamespacedKey cooldownKey = new NamespacedKey(GrapplingHook.getPlugin(), "cooldown_" + hookType.getName());
        if (persistentData.has(cooldownKey, PersistentDataType.LONG)) {
            long cooldownEnd = persistentData.get(cooldownKey, PersistentDataType.LONG);
            long currentTime = System.currentTimeMillis();
            if (currentTime < cooldownEnd) {
                //long remainingTime = (cooldownEnd - currentTime) / 1000;
                return "COOLDOWN_ACTIVE;" + (cooldownEnd - currentTime) / 1000;
            }
        }
        return null;
    }

    /**
     * Sets the number of uses for a grappling hook item.
     * Updates the item's durability, display name and lore accordingly.
     * Depending on config settings if the hook has reached its maximum uses, ut it will either be destroyed or the durability will be set to maximum damage.
     * @param is The ItemStack representing the grappling hook.
     * @param uses The number of uses to set (must be non-negative).
     * @return true if the uses were set successfully, false otherwise.
     */
    public static boolean setUses(Player p, ItemStack is, int uses) {
        if (uses < 0) {
            Bukkit.getLogger().warning("Cannot set uses to a negative value: " + uses);
            return false;
        }
        ItemMeta im = is.getItemMeta();
        if (im instanceof Damageable meta) {
            if (meta == null) {
                Bukkit.getLogger().warning("ItemMeta is null for item: " + is.getType());
                return false;
            }

            // Set how many times this hook has been used
            PersistentDataContainer persistentData = meta.getPersistentDataContainer();
            persistentData.set(new NamespacedKey(GrapplingHook.getPlugin(), "uses"), PersistentDataType.INTEGER, uses);

            // Update the durability based on uses (0 uses -> full durability, max uses -> max damage before breaking)
            GrapplingHookType hookType = GrapplingHookType.fromItemStack(is);
            if (hookType.getMaxUses() > 0) {
                int maxDurability = 64;
                double ratio = (double) uses / (double) hookType.getMaxUses();
                if (ratio >= 1.0) {
                    YamlConfiguration config = GrapplingHook.getPlugin().getConfig();
                    boolean destroyOnMaxUses = config.getBoolean("destroy-hook-on-use-limit", false);
                    meta.setDamage(maxDurability - 1);
                    if(destroyOnMaxUses) {
                        is.setAmount(0); // Remove the item if it has reached its max uses
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        p.sendMessage(GrapplingHook.getPlugin().formatMessage(config.getString("messages.hook-destroyed", "Your grappling hook has reached its maximum uses and has been destroyed.")
                                .replace("[hookname]", im.getDisplayName())));
                    }
                } else {
                    // Calculate damage needed to apply, ratio = percentage of uses used (0.0 to 1.0)
                    // multiply by max durability to get the damage value according to item
                    int damage = (int) Math.round(ratio * maxDurability);

                    damage = Math.min(damage, maxDurability - 1);

                    meta.setDamage(damage);
                }
            } else {
                meta.setDamage(0);
            }

            // Update [uses] in item meta
            String placeholder = hookType.getMaxUses() == -1 ? "∞" : String.valueOf(hookType.getMaxUses() - uses);

            YamlConfiguration config = GrapplingHook.getPlugin().getHooksConfig();
            String itemName = config.getString("hooks." + hookType.getName() + ".item_display.name", "Grappling Hook");
            itemName = itemName.replace("[uses]", placeholder);
            meta.setDisplayName(GrapplingHook.getPlugin().formatMessage(itemName));

            List<String> itemLore = config.getStringList("hooks." + hookType.getName() + ".item_display.description");
            itemLore = itemLore.stream()
                    .map(line -> line.replace("[uses]", placeholder))
                    .map(GrapplingHook.getPlugin()::formatMessage)
                    .toList();
            meta.setLore(itemLore);

            is.setItemMeta(meta);
        }
        return true;
    }
}
