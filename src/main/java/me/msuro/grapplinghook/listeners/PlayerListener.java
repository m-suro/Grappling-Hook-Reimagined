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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;


public class PlayerListener implements Listener {

    private static GrapplingHook plugin;

    public PlayerListener(GrapplingHook instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrapplingHookAction(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!HookAPI.isGrapplingHook(player.getInventory().getItemInMainHand()))
            return;

        //Bukkit.broadcastMessage(player.getName() + " called onGrapplingHookAction " + event.getState().name());
        //double  x = Math.round(event.getHook().getVelocity().getX() * 100.0) / 100.0,
        //        y = Math.round(event.getHook().getVelocity().getY() * 100.0) / 100.0,
        //        z = Math.round(event.getHook().getVelocity().getZ() * 100.0) / 100.0;
        //Bukkit.broadcastMessage("Default velocity - " + " x: " + x + ", y: " + y + ", z: " + z);
        // Player looking almost directly down or up - massively decrease x and z velocity to make traveling up or down easier

        //Bukkit.broadcastMessage(" ");

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        GrapplingHookType hookType = new GrapplingHookType(null).fromItemStack(itemInHand);

        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        // Based on how high the player is looking, adjust the Y velocity
        double y = -0.0063 * pitchDifference + 1;

        Vector vector = null;

        // IN_GROUND or FAILED_ATTEMPT -> basic grappling hook
        switch (event.getState()) {
            case FISHING: // Throwing the hook
                handleThrownHook(event, player, hookType);

                // Clear the default cooldown (~1 second) for the grappling hook item. It gets created every time in CooldownSystem.startCooldown()
                // So far no option to remove the default cooldown, so we just set it to null on use (here).
                ItemMeta meta = itemInHand.getItemMeta();
                meta.setUseCooldown(null);
                itemInHand.setItemMeta(meta);

                break;
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                Block nearestBlock = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                if (!HookAPI.canHookOntoBlock(hookType, nearestBlock)) {
                    return; // If the hook cannot hook onto the block, do nothing
                }

                handleBlockHook(event, player, hookType);

                CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());

                break;
            case REEL_IN: // Pulling the hook back mid-air
                /*
                 * Determine which block the hook is "attached" to when reeling in.
                 * - If the hook's position modulo 1 (in any axis) is near 0.0 or 1.0,
                 *      it's likely right next to a block boundary (e.g., stuck on a side/corner/edge).
                 *      In that case, use getNearestBlockLocation() to find the closest block face.
                 * - Otherwise, use the block at the hook's exact (integer) location.
                 */
                Block block = null;
                boolean airCase = true;
                double relativeX = Math.abs(event.getHook().getLocation().getX() % 1);
                double relativeZ = Math.abs(event.getHook().getLocation().getZ() % 1);
                if(relativeX == 0.125 || relativeX == 0.875 ||
                        relativeZ == 0.125 || relativeZ == 0.875) {
                    block = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                    airCase = false; // If the hook is near a block boundary, use the nearest block, thus the same mechanics as in IN_GROUND case
                } else {
                    block = event.getHook().getLocation().getBlock();
                }
                if (!HookAPI.canHookOntoBlock(hookType, block))
                    return; // If the hook cannot hook onto the block, do nothing
                Bukkit.broadcastMessage("Air case: " + airCase);
                if (airCase) {
                    handleAirHook(event, player, hookType);
                } else {
                    handleBlockHook(event, player, hookType);
                }

                CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());

                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.getHook().remove();
                if(event.getCaught() != null) {
                    event.getCaught().remove(); // Remove the caught fish entity
                    event.setExpToDrop(0); // No experience drop
                }
                event.setCancelled(true); // Cancel the event to prevent further processing
                break;
            default:
                break;
        }
    }


    private Location getNearestBlockLocation(Location loc) {
        Block block = loc.getWorld().getBlockAt(loc);
        List<Location> neighboringLocations = new ArrayList<>();
        neighboringLocations.add(block.getLocation().clone().add(0.1, 0, 0));
        neighboringLocations.add(block.getLocation().clone().add(-0.1, 0, 0));
        neighboringLocations.add(block.getLocation().clone().add(0, 0, 0.1));
        neighboringLocations.add(block.getLocation().clone().add(0, 0, -0.1));
        neighboringLocations.add(block.getLocation().clone().add(0, 0.1, 0));
        neighboringLocations.add(block.getLocation().clone().add(0, -0.1, 0));

        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Location locNeighbor : neighboringLocations) {
            if (!locNeighbor.getBlock().getType().isSolid()) {
                continue; // Skip air blocks
            }
            Bukkit.broadcastMessage("Checking block at " + locNeighbor.toString());
            double distance = loc.distanceSquared(locNeighbor);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = locNeighbor;
            }
        }
        if (nearest == null) {
            Bukkit.broadcastMessage("No solid blocks found near the hook location.");
            return loc; // If no solid blocks found, return the original location
        }
        return nearest;
    }

    private void handleThrownHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        // Near vertical pitch, reduce x and z velocity to make it easier to travel up or down
        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        if (pitchDifference < 10) {
            Vector multiply = new Vector((pitchDifference * pitchDifference) / 100, 0.8, (pitchDifference * pitchDifference) / 100);
            event.getHook().setVelocity(event.getHook().getVelocity().multiply(multiply));
        }
        event.getHook().setVelocity(event.getHook().getVelocity().multiply(hookType.getVelocityThrowMultiplier()));
    }

    private void handleAirHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand)) return;

        double pitchDiff = 90 - Math.abs(player.getLocation().getPitch());
        double y = -0.0063 * pitchDiff + 1.0;

        // Tick 0: small vertical lift to prevent ground drag
        Bukkit.getScheduler().runTask(plugin, () -> {
            Vector v0 = player.getVelocity().clone().add(new Vector(0, 0.25, 0));
            player.setVelocity(v0);
        });

        // Tick 1: nerf horizontal speed and inject controlled Y
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
            // If you want zero extra vertical pull from the vector, multiply by (1,0,1) instead.
            v2.add(pull.multiply(new Vector(1, 1, 1)));
            player.setVelocity(v2);
        }, 2L);
    }


    private void handleBlockHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand)) return;

        Block target = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
        if (!HookAPI.canHookOntoBlock(hookType, target)) return;

        double pitchDiff = 90 - Math.abs(player.getLocation().getPitch());
        double y = -0.0063 * pitchDiff + 1.0;

        // Tick 0: small vertical lift so the player unsticks from ground
        Bukkit.getScheduler().runTask(plugin, () -> {
            Vector v0 = player.getVelocity().clone().add(new Vector(0, 0.25, 0));
            player.setVelocity(v0);
        });

        // Tick 1: nerf horizontal speed and inject controlled Y
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
            v2.add(pull.multiply(new Vector(1, 1, 1))); // or new Vector(1, 0, 1) if you want no extra Y
            player.setVelocity(v2);
        }, 2L);
    }

}
