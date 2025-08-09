package me.msuro.grapplinghook.listeners;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.GrapplingHookType;
import me.msuro.grapplinghook.api.HookAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;


public class PlayerListener implements Listener {

    private static GrapplingHook plugin;

    public PlayerListener(GrapplingHook instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrapplingHookAction(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!HookAPI.isGrapplingHook(player.getInventory().getItemInMainHand()))
            return;

        Bukkit.broadcastMessage(player.getName() + " called onGrapplingHookAction " + event.getState().name());
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
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                Block nearestBlock = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                if (!HookAPI.canHookOntoBlock(hookType, nearestBlock)) {
                    return; // If the hook cannot hook onto the block, do nothing
                }

                handleBlockHook(event, player, hookType);

                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.setCancelled(true);
                break;
            case FISHING: // Throwing the hook
                handleThrownHook(event, player, hookType);
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
                if (event.getHook().getLocation().getX() % 1 < 0.1 || event.getHook().getLocation().getX() % 1 > 0.9 ||
                        event.getHook().getLocation().getZ() % 1 < 0.1 || event.getHook().getLocation().getZ() % 1 > 0.9) {
                    block = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
                    airCase = false; // If the hook is near a block boundary, use the nearest block, thus the same mechanics as in IN_GROUND case
                } else {
                    block = event.getHook().getLocation().getBlock();
                }
                if (!HookAPI.canHookOntoBlock(hookType, block))
                    return; // If the hook cannot hook onto the block, do nothing

                if (airCase) {
                    handleAirHook(event, player, hookType);
                } else {
                    handleBlockHook(event, player, hookType);
                }

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
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        // Based on how high the player is looking, adjust the Y velocity
        double y = -0.0063 * pitchDifference + 1;

        Vector vector = null;
        // Initial vertical boost to prevent ground dragging
        event.getPlayer().getVelocity().add(new Vector(0, 0.25, 0));
        // Nerf player's velocity to prevent excessive boost from sprinting and jumping
        event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                .multiply(new Vector(0.3, 1, 0.3)));

        Location hookLocation = event.getHook().getLocation();
        vector = hookLocation.toVector().subtract(player.getLocation().toVector());
        vector = vector.normalize().multiply(hookType.getVelocityPullMultiplier());
        event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                .multiply(new Vector(1, 0, 1)) // Reduce the pull strength to prevent excessive speed
                .setY(y) // Set the Y velocity to a small value to prevent excessive speed
                .add(vector)); // Add the hook's pull vector to the player's velocity
    }

    private void handleBlockHook(PlayerFishEvent event, Player player, GrapplingHookType hookType) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HookAPI.isGrapplingHook(itemInHand))
            return;

        Block nearestBlock = getNearestBlockLocation(event.getHook().getLocation()).getBlock();
        if (!HookAPI.canHookOntoBlock(hookType, nearestBlock)) {
            return; // If the hook cannot hook onto the block, do nothing
        }

        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        // Based on how high the player is looking, adjust the Y velocity
        double y = -0.0063 * pitchDifference + 1;

        BukkitTask asyncTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Initial vertical boost to prevent ground dragging
                event.getPlayer().getVelocity().add(new Vector(0, 0.25, 0));
                this.wait(50);
                // Nerf player's velocity to prevent excessive boost from sprinting and jumping
                event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                        .multiply(new Vector(0.3, 1, 0.3)));
                // Set the Y velocity to a small value to prevent excessive speed
                event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                        .add(new Vector(0, y, 0)));
                // Add the hook's pull vector to the player's velocity
                Vector vector1 = event.getHook().getLocation().toVector().subtract(player.getLocation().toVector());
                vector1 = vector1.normalize().multiply(hookType.getVelocityPullMultiplier());
                event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                        .add(vector1)); // Add the hook's pull vector to the player's velocity
            } catch (InterruptedException e) {
                Bukkit.getLogger().severe("Error while handling block hook: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
