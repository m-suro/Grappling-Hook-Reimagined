package me.msuro.grapplinghook.api;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.GrapplingHookType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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
        String entitiesMode = hookType.getMobsMode();
        List<String> entitiesList = hookType.getMobsList();
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
}
