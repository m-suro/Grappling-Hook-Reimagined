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

        boolean blockInList = blocksList.stream()
                .anyMatch(blockType -> blockType.equalsIgnoreCase(blockTypeName));

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

        boolean entityInList = entitiesList.stream()
                .anyMatch(entity -> entity.equalsIgnoreCase(entityTypeName));

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

    public static boolean canUse(GrapplingHookType hookType, Player player) {
        if (hookType.getMaxUses() != -1) {
            int uses = hookType.getUses();
            if (uses >= hookType.getMaxUses()) {
                player.sendActionBar(GrapplingHook.getPlugin().formatMessage("&8[&b\uD83C\uDFA3&8]&7 This hook has ran out of uses!"));
                return false;
            }
        }
        return true;
    }

    public static boolean setUses(ItemStack is, int uses) {
        if (uses < 0) {
            Bukkit.getLogger().warning("Cannot set uses to a negative value: " + uses);
            return false;
        }
        Damageable meta = (Damageable) is.getItemMeta();
        if (meta == null) {
            Bukkit.getLogger().warning("ItemMeta is null for item: " + is.getType());
            return false;
        }

        PersistentDataContainer persistentData = meta.getPersistentDataContainer();
        persistentData.set(new NamespacedKey(GrapplingHook.getPlugin(), "uses"), PersistentDataType.INTEGER, uses);

        GrapplingHookType hookType = new GrapplingHookType(null).fromItemStack(is);
        if (hookType.getMaxUses() > 0) {
            int maxDurability = 64;
            double ratio = (double) uses / (double) hookType.getMaxUses();
            int damage = (int) Math.round(ratio * maxDurability);

            damage = Math.min(damage, maxDurability-1); // Ensure damage does not exceed max durability so it doesn't break

            meta.setDamage(damage);
        } else {
            // -1 or 0: unlimited or disabled durability tracking; clear visible damage
            meta.setDamage(0);
        }

        // Update [uses] in item meta
        String placeholder = hookType.getMaxUses() == -1 ? "∞" : String.valueOf(hookType.getMaxUses()-uses);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(GrapplingHook.getPlugin().getDataFolder(), "hooks.yml"));
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
        return true;
    }
}
