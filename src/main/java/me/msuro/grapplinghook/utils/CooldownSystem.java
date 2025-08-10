package me.msuro.grapplinghook.utils;

import me.msuro.grapplinghook.GrapplingHook;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

public class CooldownSystem {

    @SuppressWarnings("UnstableApiUsage")
    public static void startCooldown(Player player, ItemStack itemStack, int duration) {
        GrapplingHook plugin = GrapplingHook.getPlugin();
        // UseCooldownComponent was introduced in 1.21.2
        if (plugin.isServerVersionAtLeast1_21_2()) {
            String hookName = itemStack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "name"), PersistentDataType.STRING);
            if (hookName == null) {
                plugin.getLogger().warning("Hook name is null for item: " + itemStack.getType());
                return;
            }
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                plugin.getLogger().warning("ItemMeta is null for item: " + itemStack.getType());
                return;
            }

            NamespacedKey cooldownKey = new NamespacedKey(plugin, "cooldown_" + hookName);

            // Get or create the UseCooldownComponent
            UseCooldownComponent cooldownComponent = meta.getUseCooldown();
            cooldownComponent.setCooldownGroup(cooldownKey);
            // Do not use setCooldownSeconds because it sets the cooldown permanently - so the cooldown starts even on casting the hook out, not only retrieving it
            // You have to clear the cooldownComponent manually anyway because Minecraft has a default cooldown of 1 second.
            meta.setUseCooldown(cooldownComponent);

            itemStack.setItemMeta(meta);

            player.setCooldown(itemStack, duration*20);
            player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + duration*1000L);
        } else {
            plugin.getLogger().warning("For now the cooldown system is not available on versions below 1.21 as the API does not support it.");
        }
    }
}
