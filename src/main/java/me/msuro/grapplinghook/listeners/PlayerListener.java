package me.msuro.grapplinghook.listeners;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.GrapplingHookType;
import me.msuro.grapplinghook.api.HookAPI;
import me.msuro.grapplinghook.utils.CooldownSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.event.player.PlayerFishEvent.State.FISHING;

@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public class PlayerListener implements Listener {

    private static GrapplingHook plugin;

    public PlayerListener(GrapplingHook instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrapplingHookAction(PlayerFishEvent event) {
        Player player = event.getPlayer();

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        // Item is a grappling hook, so we don't care about any vanilla mechanics
        // as long as they are not throwing the hook, we cancel the event
        // Since we are canceling the event, we have to remove the hook entity manually later
        if(event.getState() != FISHING)
            event.setCancelled(true);

        GrapplingHookType hookType = GrapplingHookType.fromItemStack(itemInHand);


        // Check if the player can use the hook
        // If the player cannot use the hook, we remove the hook entity and send a message
        String canUseResult = HookAPI.canUse(hookType, player);
        if (canUseResult != null) {
            String[] canUseParts = canUseResult.split(";");
            event.getHook().remove(); // Remove the hook entity if the player cannot use the hook
            switch (canUseParts[0]) {
                case "USE_LIMIT_REACHED":
                    player.sendActionBar(GrapplingHook.getPlugin().formatMessage(plugin.getConfig().getString("messages.use-limit-reached"))
                            .replace("[maxuses]", String.valueOf(hookType.getMaxUses())));
                    break;
                case "COOLDOWN_ACTIVE":
                    // Hardcoded message because afaik there is no way to actually reach it - the cooldown is handled directly by minecraft
                    player.sendActionBar(GrapplingHook.getPlugin().formatMessage("&8[&b\uD83C\uDFA3&8]&7 You cannot use this hook yet! Cooldown active for " + canUseParts[1] + " seconds."));
                    break;
            }
            return;
        }

        //Bukkit.broadcastMessage(event.getState().name());

        switch (event.getState()) {
            case FISHING: // Throwing the hook
                handleThrownHook(event, player, hookType);

                if(plugin.isServerVersionAtLeast1_21_2()) {
                    // Clear the default cooldown (~1 second) for the grappling hook item. It gets created every time in CooldownSystem.startCooldown() by Minecraft
                    // So far there is no way to not create it, but we can clear it right after the hook is thrown. It causes a small visual glitch, but it is better than having a cooldown.
                    ItemMeta meta = itemInHand.getItemMeta();
                    meta.setUseCooldown(null);
                    itemInHand.setItemMeta(meta);
                }

                break;
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                Block nearestBlock = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                if (!HookAPI.canHookOntoBlock(hookType, nearestBlock)) {
                    event.getHook().remove();
                    return; // If the hook cannot hook onto the block, do nothing
                }

                handleBlockHook(event, player, hookType);

                HookAPI.setUses(player, itemInHand, hookType.getUses()+1);

                if (plugin.isServerVersionAtLeast1_21_2())
                    CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());

                event.getHook().remove(); // Remove the hook entity after pulling it back

                break;
            case REEL_IN: // Pulling the hook back mid-air/block/
                /*
                 * Determine which block the hook is "attached" to when reeling in.
                 * - If the hook's location is near a block boundary (e.g., 0.125 or 0.875 in X or Z),
                 *   use the nearest solid block to the hook's location, thus calling it "block case". (This is the case when the player throws the hook onto the side of a block)
                 * - If the hook's location is not near a block boundary,
                 *   use the block at the hook's location, calling it "air case".
                 */
                Block block;
                // By default, assume the hook is in the air - this is the most common case when this state gets called.
                boolean airCase = true;
                double relativeX = Math.abs(event.getHook().getLocation().getX() % 1);
                double relativeZ = Math.abs(event.getHook().getLocation().getZ() % 1);
                if(relativeX == 0.125 || relativeX == 0.875 ||
                        relativeZ == 0.125 || relativeZ == 0.875) {
                    block = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                    airCase = false;
                } else {
                    block = event.getHook().getLocation().getBlock();
                }
                if (!HookAPI.canHookOntoBlock(hookType, block)) {
                    event.getHook().remove();
                    return; // If the hook cannot hook onto the block, do nothing
                }

                // If the hook is in the air, we will handle it differently than if it is on a block
                if (airCase) {
                    handleAirHook(event, player, hookType);
                } else {
                    handleBlockHook(event, player, hookType);
                }

                HookAPI.setUses(player, itemInHand, hookType.getUses()+1);

                if (plugin.isServerVersionAtLeast1_21_2())
                    CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());

                event.getHook().remove(); // Remove the hook entity after pulling it back

                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.getHook().remove();
                if(event.getCaught() != null) {
                    event.getCaught().remove(); // Remove the caught fish entity
                    event.setExpToDrop(0); // No experience drop
                }
                break;
            case CAUGHT_ENTITY:
                if(event.getHook().getHookedEntity() != null) {

                    if(!HookAPI.canHookOntoEntity(hookType, event.getHook().getHookedEntity().getType())) {
                        event.getHook().remove(); // If the hook cannot pull the entity, remove the hook entity
                        return;
                    }

                    handleEntityPull(event, player, hookType);
                    HookAPI.setUses(player, itemInHand, hookType.getUses()+1);
                    if (plugin.isServerVersionAtLeast1_21_2())
                        CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());
                    event.getHook().remove(); // Remove the hook entity after pulling it back
                    return;
                }
            default:
                break;
        }
    }


    /**
     * Finds the nearest solid block location around the given location.
     * If no solid blocks are found, returns the original location.
     * Use 6 neighboring locations to check for solid blocks
     * Could have used all 3x3x3 blocks around the location, but that would be too many checks and could cause performance issues.
     *
     * @param loc The location to check for nearby solid blocks.
     * @return The nearest solid block location or the original location if none found.
     */
    private Location getNearestBlockLocation(Location loc) {
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        // Check 6 neighboring blocks directly without creating a list
        // This reduces memory allocation overhead
        Location[] offsets = {
            loc.clone().add(1, 0, 0),   // East
            loc.clone().add(-1, 0, 0),  // West
            loc.clone().add(0, 0, 1),   // South
            loc.clone().add(0, 0, -1),  // North
            loc.clone().add(0, 1, 0),   // Up
            loc.clone().add(0, -1, 0)   // Down
        };

        for (Location candidate : offsets) {
            Block block = candidate.getBlock();
            if (!block.getType().isSolid()) {
                continue;
            }
            double distance = loc.distanceSquared(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        return nearest != null ? nearest : loc;
    }

    /**
     * Handles the case when the hook is thrown.
     * Adjusts the velocity based on the player's pitch and hook type.
     *
     * @param event    The PlayerFishEvent containing the hook and player information.
     * @param player   The player who threw the hook.
     * @param hookType The type of grappling hook being used.
     */
    private void handleThrownHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        // Near vertical pitch, reduce hook's x and z velocity to make it easier to travel up or down
        // Also reduce the y velocity to prevent the hook from flying too high (Can be adjusted in the config anyway - just makes it more balanced for default 1.0 multipliers)
        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        if (pitchDifference < 10) {
            Vector multiply = new Vector((pitchDifference * pitchDifference) / 100, 0.8, (pitchDifference * pitchDifference) / 100);
            event.getHook().setVelocity(event.getHook().getVelocity().multiply(multiply));
        }
        event.getHook().setVelocity(event.getHook().getVelocity().multiply(hookType.getVelocityThrowMultiplier()));
    }

    /**
     * Handles the case when the hook is pulled back while in the air.
     * Adjusts the player's velocity based on the hook's location and player's pitch.
     *
     * @param event    The PlayerFishEvent containing the hook and player information.
     * @param player   The player who is pulling the hook back.
     * @param hookType The type of grappling hook being used.
     */
    private void handleAirHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand)) return;

        // Convert pitch to factor: straight ahead = 0° , straight up/down = +/- 90°
        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        // Based on how high the player is looking, adjust the Y velocity
        double y = -0.006 * pitchDifference + 1;

        // Tick 0: small vertical lift to prevent ground drag
        Bukkit.getScheduler().runTask(plugin, () -> {
            Vector v0 = player.getVelocity().clone().add(new Vector(0, 0.25, 0));
            player.setVelocity(v0);
        });

        // Tick 1: nerf horizontal player speed and inject controlled Y
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector v1 = player.getVelocity().clone();
            v1.multiply(new Vector(0.3, 1, 0.3));
            v1.setY(y);
            player.setVelocity(v1);
        }, 1L);

        // Tick 2: add pull toward hook
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector pull = event.getHook().getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .multiply(hookType.getVelocityPullMultiplier());

            Vector v2 = player.getVelocity().clone();
            v2.add(pull);
            player.setVelocity(v2);
        }, 2L);
    }


    private void handleBlockHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand)) return;

        Block target = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
        if (!HookAPI.canHookOntoBlock(hookType, target)) return;

        // Convert pitch to factor: straight ahead = 0° , straight up/down = +/- 90°
        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        // Based on how high the player is looking, adjust the Y velocity
        double y = -0.006 * pitchDifference + 1;

        // Tick 0: small vertical lift so the player unsticks from ground
        Bukkit.getScheduler().runTask(plugin, () -> {
            Vector v0 = player.getVelocity().clone().add(new Vector(0, 0.25, 0));
            player.setVelocity(v0);
        });

        // Tick 1: nerf horizontal speed player and inject controlled Y
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector v1 = player.getVelocity().clone();
            v1.multiply(new Vector(0.3, 1, 0.3));
            v1.setY(y);
            player.setVelocity(v1);
        }, 1L);

        // Tick 1 (after Y set) or Tick 2 for extra safety: add pull toward hook
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector pull = event.getHook().getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .multiply(hookType.getVelocityPullMultiplier());

            // preserve current Y (already set) and add pull mostly to XZ
            Vector v2 = player.getVelocity().clone();
            v2.add(pull);
            player.setVelocity(v2);
        }, 2L);
    }

    private void handleEntityPull(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        if (event.getHook().getHookedEntity() == null) return; // If the hook is not hooked onto an entity, do nothing

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand)) return;

        if (!HookAPI.canHookOntoEntity(hookType, event.getHook().getHookedEntity().getType())) return;

        // Pull the entity towards the player
        Vector pull = player.getLocation().toVector()
                .subtract(event.getHook().getHookedEntity().getLocation().toVector())
                .normalize()
                .multiply(hookType.getVelocityPullMultiplier());

        Vector v2 = event.getHook().getHookedEntity().getVelocity().clone();
        v2.add(pull);
        event.getHook().getHookedEntity().setVelocity(v2);
    }

}
