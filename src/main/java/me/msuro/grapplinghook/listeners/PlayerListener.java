package me.msuro.grapplinghook.listeners;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.api.HookAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
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

        // IN_GROUND or FAILED_ATTEMPT -> basic grappling hook
        if(event.getState() == PlayerFishEvent.State.IN_GROUND || event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT) {

            Location loc = event.getHook().getLocation();

            //Block block = loc.clone().add(0, -0.3, 0).getBlock();
            //PlayerGrappleEvent e = new PlayerGrappleEvent(player, player, loc);
           // plugin.getServer().getPluginManager().callEvent(e);
        }
        else if(event.getState() == PlayerFishEvent.State.CAUGHT_FISH){
            //PlayerGrappleEvent e = new PlayerGrappleEvent(player, player, event.getHook().getLocation());
            //plugin.getServer().getPluginManager().callEvent(e);
            event.setCancelled(true);
        }
        //
        else if(event.getState() == PlayerFishEvent.State.FISHING){

        }
        else if(event.getState() == PlayerFishEvent.State.REEL_IN){

            Block block = event.getHook().getLocation().clone().add(0, -0.1, 0).getBlock();

//            if (HookAPI.canHookMaterial(player, block.getType())) {
//                if(plugin.usePerms() == false || player.hasPermission("grapplinghook.pull.self")){
//                    PlayerGrappleEvent e = new PlayerGrappleEvent(player, player, event.getHook().getLocation());
//                    plugin.getServer().getPluginManager().callEvent(e);
//                }
//            }
        }
        else{
        }
    }


}
