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

    @EventHandler(priority = EventPriority.HIGH)
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
        double pitchDifference = 90 - Math.abs(player.getLocation().getPitch());
        if(pitchDifference < 10) {
            Vector multiply = new Vector((pitchDifference * pitchDifference) / 100, 0.6, (pitchDifference * pitchDifference) / 100);
            event.getHook().setVelocity(event.getHook().getVelocity().multiply(multiply));
            Bukkit.broadcastMessage("Setting x and z velocity * " + multiply);
        }
        //Bukkit.broadcastMessage(" ");

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if(!HookAPI.isGrapplingHook(itemInHand))
            return;

        GrapplingHookType hookType = new GrapplingHookType(null).fromItemStack(itemInHand);


        // IN_GROUND or FAILED_ATTEMPT -> basic grappling hook
        switch(event.getState()) {
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.setCancelled(true);
                break;
            case FISHING: // Throwing the hook
                event.getHook().setVelocity(event.getHook().getVelocity().multiply(hookType.getVelocityThrowMultiplier()));                break;
            case REEL_IN: // Pulling the hook back
                event.getPlayer().setVelocity(new Vector(0, 0.25, 0));
                Location hookLocation = event.getHook().getLocation();
                Vector vector = hookLocation.toVector().subtract(player.getLocation().toVector());
                vector = vector.normalize().multiply(hookType.getVelocityPullMultiplier());
                event.getPlayer().setVelocity(vector);
            default:
                break;
        }
       }
}
