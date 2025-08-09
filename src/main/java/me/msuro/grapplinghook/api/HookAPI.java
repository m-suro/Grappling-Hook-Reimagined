package me.msuro.grapplinghook.api;

import me.msuro.grapplinghook.GrapplingHook;
import org.bukkit.*;
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

	public static void breakHookInHand(Player player){
		player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 10f, 1f);
	}
}
