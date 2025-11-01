package me.msuro.grapplinghook.listeners;

import me.msuro.grapplinghook.GrapplingHook;
import me.msuro.grapplinghook.GrapplingHookType;
import me.msuro.grapplinghook.api.HookAPI;
import me.msuro.grapplinghook.utils.CooldownSystem;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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
        if (event.getState() != FISHING)
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

        Entity hookVehicle = event.getHook().getVehicle();
        if (hookVehicle != null) {
            // Hook is already attached to an entity - kill the vehicle to not leave invisble, immortal armor stands around
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (hookVehicle.isValid()) {
                    hookVehicle.remove();
                    plugin.getArmorStandList().remove(hookVehicle); // Remove the armor stand from the list if it is still there
                }
            }, 1L);
        }

        switch (event.getState()) {
            case FISHING: // Throwing the hook
                handleThrownHook(event, player, hookType);

                if (plugin.isServerVersionAtLeast1_21_2()) {
                    // Clear the default cooldown (~1 second) for the grappling hook item. It gets created every time in CooldownSystem.startCooldown() by Minecraft
                    // So far there is no way to not create it, but we can clear it right after the hook is thrown. It causes a small visual glitch, but it is better than having a cooldown.
                    ItemMeta meta = itemInHand.getItemMeta();
                    meta.setUseCooldown(null);
                    itemInHand.setItemMeta(meta);
                }

                runTrackingRunnable(event.getHook(), player.getLocation(), hookType);

                break;
            case IN_GROUND: // Pulling the hook back from ground
            case FAILED_ATTEMPT: // Pulling the hook back from water after failing to catch a fish
                double y = event.getHook().getLocation().getY();
                Location loc = event.getHook().getLocation().clone();
                loc.setY(y - 0.125); // Set the location to the block below the hook's location
                Block nearestBlock = loc.getBlock(); // Get the block below the hook's location
                if (!HookAPI.canHookOntoBlock(hookType, nearestBlock.getType().name())) {
                    event.getHook().remove();
                    return; // If the hook cannot hook onto the block, do nothing
                }

                handleBlockHook(event, player, hookType);

                HookAPI.setUses(player, itemInHand, hookType.getUses() + 1);

                addFallDamagePreventionPDC(player, hookType);

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
                // By default, assume the hook is in the air - this is the most common case when this state gets called.
                NamespacedKey raytracedKey = new NamespacedKey(plugin, "raytraced_" + hookType.getId());
                String raytracedBlockType = event.getHook().getPersistentDataContainer().get(raytracedKey, PersistentDataType.STRING);
                boolean airCase = raytracedBlockType == null || raytracedBlockType.equals("AIR");
                if (raytracedBlockType == null) {
                    // If the raytraced block type is null, it means the hook is in the air
                    raytracedBlockType = "AIR"; // Set it to AIR to avoid NPE
                }
                if (!HookAPI.canHookOntoBlock(hookType, raytracedBlockType)) {
                    event.getHook().remove();
                    return; // If the hook cannot hook onto the block, do nothing
                }

                // If the hook is in the air, we will handle it differently than if it is on a block
                if (airCase) {
                    handleAirHook(event, player, hookType);
                } else {
                    handleBlockHook(event, player, hookType);
                }

                HookAPI.setUses(player, itemInHand, hookType.getUses() + 1);

                addFallDamagePreventionPDC(player, hookType);

                if (plugin.isServerVersionAtLeast1_21_2())
                    CooldownSystem.startCooldown(player, itemInHand, hookType.getCooldown());

                event.getHook().remove(); // Remove the hook entity after pulling it back

                break;
            case CAUGHT_FISH: // Pulling the hook back from water after catching a fish
                event.getHook().remove();
                if (event.getCaught() != null) {
                    event.getCaught().remove(); // Remove the caught fish entity
                    event.setExpToDrop(0); // No experience drop
                }
                break;
            case CAUGHT_ENTITY:
                if (event.getHook().getHookedEntity() != null) {

                    if (!HookAPI.canHookOntoEntity(hookType, event.getHook().getHookedEntity().getType())) {
                        event.getHook().remove(); // If the hook cannot pull the entity, remove the hook entity
                        return;
                    }

                    handleEntityPull(event, player, hookType);
                    HookAPI.setUses(player, itemInHand, hookType.getUses() + 1);
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

        double y = getY(event, player);

        // Tick 0: small vertical lift so the player unsticks from ground
        Bukkit.getScheduler().runTask(plugin, () -> {
            Vector v0 = player.getVelocity().clone().add(new Vector(0, 0.25, 0));
            player.setVelocity(v0);
        });

        // Tick 1: nerf horizontal speed player and inject controlled Y
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector v1 = player.getVelocity().clone();
            v1.multiply(new Vector(0.3, 0.3, 0.3));
            player.setVelocity(v1);
        }, 1L);

        // Tick 1 (after Y set) or Tick 2 for extra safety: add pull toward hook
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Vector pull = event.getHook().getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .setY(y)
                    .multiply(hookType.getVelocityPullMultiplier());

            // preserve current Y (already set) and add pull mostly to XZ
            Vector v2 = player.getVelocity().clone();
            v2.add(pull);
            player.setVelocity(v2);
        }, 2L);

    }

    private static double getY(PlayerFishEvent event, Player player) {
        Location hookLocation = event.getHook().getLocation();
        double g = -0.08; // Gravity (Minecraft)
        double d = hookLocation.distance(player.getLocation()); // 3D distance
        double t = d;
        double minBoost = 0.5; // Example: adjust as needed (e.g., 0.1 to 0.2)
        double y = ((1.0 + t)
                * (hookLocation.getY() - player.getLocation().getY()) / t
                - g * t) / 25;
        y = y > 0 ? y + minBoost : y - minBoost;
        return y;
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

    private void runTrackingRunnable(FishHook hook, Location world, GrapplingHookType hookType) {
        new Object() {
            private BukkitTask task;
            private Location prev = hook.getLocation().clone();
            private ProjectileSource source = hook.getShooter();
            private Player player = (source instanceof Player) ? (Player) source : null;

            {
                task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!hook.isValid() || !hook.getWorld().equals(world.getWorld())) { // If the hook is not valid or the world has changed, cancel the task
                        task.cancel();
                        return;
                    }

                    if (!hookType.getStickyHook() && !hookType.getSlowFall()) {
                        // If the hook is not sticky and does not have slow fall, we don't need to track it
                        task.cancel();
                        return;
                    }


                    Location curr = hook.getLocation();
                    Vector delta = curr.toVector().subtract(prev.toVector());
                    double dist = delta.length();

                    if (dist < 1e-4) {
                        prev = curr.clone();
                        if(player == null)
                            return;
                        if (!hookType.getSlowFall())
                            return;
                        if (hook.getLocation().getY() < player.getLocation().getY())
                            return;
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 1, 1, false, false));
                        return;
                    }

                    RayTraceResult r = world.getWorld().rayTraceBlocks(
                            prev,
                            delta.normalize(),
                            dist + 0.1,
                            FluidCollisionMode.ALWAYS,
                            true
                    );

                    if (r != null && r.getHitBlock() != null && !r.getHitBlock().getType().isAir()) {
                        //BlockFace face = r.getHitBlockFace();
                        Location hit = new Location(world.getWorld(), r.getHitPosition().getX(), r.getHitPosition().getY(), r.getHitPosition().getZ());
                        NamespacedKey raytracedKey = new NamespacedKey(plugin, "raytraced_" + hookType.getId());
                        hook.getPersistentDataContainer().set(raytracedKey, PersistentDataType.STRING, r.getHitBlock().getType().name());
                        if(hookType.getStickyHook()) {
                            // Spawn an invisible armor stand at the hit location so the hook can be attached to it
                            ArmorStand vehicle = world.getWorld().spawn(hit, org.bukkit.entity.ArmorStand.class, armorStand -> {
                                armorStand.setVisible(false);
                                armorStand.setGravity(false);
                                armorStand.setBasePlate(false);
                                armorStand.setMarker(true);
                                armorStand.setCustomNameVisible(false);
                                armorStand.addPassenger(hook);
                                armorStand.setInvulnerable(true);
                            });
                            plugin.getArmorStandList().add(vehicle);
                            task.cancel();

                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (vehicle.isValid()) {
                                    plugin.getArmorStandList().remove(vehicle);
                                    vehicle.remove();
                                }
                            }, 100L); // 100 ticks
                        }
                    } else if (hook.getVelocity().lengthSquared() < 0.01) {
                        // Check block at current and all neighbors
                        curr = hook.getLocation();
                        for (BlockFace face : BlockFace.values()) {
                            Block neighbor = curr.getBlock().getRelative(face);
                            if (neighbor.getType().isSolid()) {
                                NamespacedKey raytracedKey = new NamespacedKey(plugin, "raytraced_" + hookType.getId());
                                hook.getPersistentDataContainer().set(raytracedKey, PersistentDataType.STRING, neighbor.getType().name());
                                if(hookType.getStickyHook()) {
                                    // Spawn an invisible armor stand at the hit location so the hook can be attached to it
                                    ArmorStand vehicle = world.getWorld().spawn(hook.getLocation(), org.bukkit.entity.ArmorStand.class, armorStand -> {
                                        armorStand.setVisible(false);
                                        armorStand.setGravity(false);
                                        armorStand.setBasePlate(false);
                                        armorStand.setMarker(true);
                                        armorStand.setCustomNameVisible(false);
                                        armorStand.addPassenger(hook);
                                        armorStand.setInvulnerable(true);
                                    });
                                    plugin.getArmorStandList().add(vehicle);
                                    task.cancel();

                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        if (vehicle.isValid()) {
                                            vehicle.remove();
                                            plugin.getArmorStandList().remove(vehicle);
                                        }
                                    }, 100L); // 100 ticks
                                }
                                break;
                            }
                        }
                    }
                    prev = curr.clone();

                }, 2L, 1L);
            }
        };
    }

    private void addFallDamagePreventionPDC(Player player, GrapplingHookType hookType) {
        if(hookType.getFallDamage())
            return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey usedHook = new NamespacedKey(plugin, "used_hook");
        NamespacedKey timeUsed = new NamespacedKey(plugin, "time_used_hook");
        NamespacedKey yLevelOnUse = new NamespacedKey(plugin, "y_level_on_use_hook");
        pdc.set(usedHook, PersistentDataType.INTEGER, hookType.getId());
        pdc.set(timeUsed, PersistentDataType.LONG, System.currentTimeMillis());
        pdc.set(yLevelOnUse, PersistentDataType.DOUBLE, player.getLocation().getY());
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey usedHookPDC = new NamespacedKey(plugin, "used_hook");
        NamespacedKey timeUsedPDC = new NamespacedKey(plugin, "time_used_hook");
        NamespacedKey yLevelOnUsePDC = new NamespacedKey(plugin, "y_level_on_use_hook");

        if (!pdc.has(usedHookPDC, PersistentDataType.INTEGER)
                || !pdc.has(timeUsedPDC, PersistentDataType.LONG)
                || !pdc.has(yLevelOnUsePDC, PersistentDataType.DOUBLE))
            return;

        int hookId = pdc.get(usedHookPDC, PersistentDataType.INTEGER);
        long timeUsedMillis = pdc.get(timeUsedPDC, PersistentDataType.LONG);
        double yLevelOnUse = pdc.get(yLevelOnUsePDC, PersistentDataType.DOUBLE);

        if(System.currentTimeMillis()-timeUsedMillis > 5000L)
            return;

        double currentY = player.getLocation().getY();
        if (currentY >= yLevelOnUse-3) {
            // Player is above the Y level when the hook was used - prevent fall damage
            // Subtract 3 for safety margin to account for slight variations in landing position
            event.setCancelled(true);
        } else {
            // Player is below the Y level when the hook was used - allow fall damage and remove PDC entries
            pdc.remove(usedHookPDC);
            pdc.remove(timeUsedPDC);
            pdc.remove(yLevelOnUsePDC);
        }
    }

}
