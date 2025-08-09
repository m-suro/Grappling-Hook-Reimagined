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
import org.bukkit.util.Vector;


public class PlayerListener implements Listener {

    private static GrapplingHook plugin;

    public PlayerListener(GrapplingHook instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrapplingHookAction(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if(!HookAPI.isGrapplingHook(player.getInventory().getItemInMainHand()))
            return;

        Bukkit.broadcastMessage(player.getName() + " called onGrapplingHookAction " + event.getState().name());
        //double  x = Math.round(event.getHook().getVelocity().getX() * 100.0) / 100.0,
        //        y = Math.round(event.getHook().getVelocity().getY() * 100.0) / 100.0,
        //        z = Math.round(event.getHook().getVelocity().getZ() * 100.0) / 100.0;
        //Bukkit.broadcastMessage("Default velocity - " + " x: " + x + ", y: " + y + ", z: " + z);
        // Player looking almost directly down or up - massively decrease x and z velocity to make traveling up or down easier

        //Bukkit.broadcastMessage(" ");

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if(!HookAPI.isGrapplingHook(itemInHand))
            return;

        GrapplingHookType hookType = new GrapplingHookType(null).fromItemStack(itemInHand);

        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());

        // IN_GROUND or FAILED_ATTEMPT -> basic grappling hook
        switch(event.getState()) {
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.setCancelled(true);
                break;
            case FISHING: // Throwing the hook

                // Near vertical pitch, reduce x and z velocity to make it easier to travel up or down
                if(pitchDifference < 10) {
                    Vector multiply = new Vector((pitchDifference * pitchDifference) / 100, 0.8, (pitchDifference * pitchDifference) / 100);
                    event.getHook().setVelocity(event.getHook().getVelocity().multiply(multiply));
                }
                event.getHook().setVelocity(event.getHook().getVelocity().multiply(hookType.getVelocityThrowMultiplier()));
                break;
            case REEL_IN: // Pulling the hook back

                double y = -0.0063 * pitchDifference + 1;

                // Initial vertical boost to prevent ground dragging
                event.getPlayer().getVelocity().add(new Vector(0, 0.25, 0));
                // Nerf player's velocity to prevent excessive boost from sprinting and jumping
                event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                        .multiply(new Vector(0.3, 1, 0.3)));

                Location hookLocation = event.getHook().getLocation();
                Vector vector = hookLocation.toVector().subtract(player.getLocation().toVector());
                vector = vector.normalize().multiply(hookType.getVelocityPullMultiplier());
                event.getPlayer().setVelocity(event.getPlayer().getVelocity()
                        .multiply(new Vector(1,0,1)) // Reduce the pull strength to prevent excessive speed
                        .setY(y) // Set the Y velocity to 1.0 to ensure the player is pulled up
                        .add(vector)); // Add the hook's pull vector to the player's velocity

                break;
            default:
                break;
        }
       }
}
