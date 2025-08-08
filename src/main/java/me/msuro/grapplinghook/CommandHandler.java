package me.msuro.grapplinghook;

import me.msuro.grapplinghook.utils.HookSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandHandler extends BukkitCommand {

    private GrapplingHook plugin;

    public CommandHandler(GrapplingHook instance, String permission, String name, String description, String usageMessage, List<String> aliases) {
        super(name, description, usageMessage, aliases);
        this.setPermission(permission);
        plugin = instance;
        try {
            register();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                //these are commands only operators have access to
                if (player.hasPermission("grapplinghook.operator") || player.isOp()) {
                    player.sendMessage("/"+this.getName()+" give <hook_id> - give yourself a hook");
                    player.sendMessage("/"+this.getName()+" give <hook_id> <player> - give player a hook");
                    player.sendMessage("/"+this.getName()+" give2 - test the new hook system, gives all hooks to player");
                    return true;
                }
            }
            //these are commands that can be executed from the console
            else{
                sender.sendMessage("/"+this.getName()+" give <hook_id> <player> - give player a hook");
                return true;
            }
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if ((plugin.usePerms() && !player.hasPermission("grapplinghook.operator")) || (!plugin.usePerms() && !player.isOp())) {
                        player.sendMessage(ChatColor.RED+"You are not authorized to use this command.");
                        return true;
                    }
                    plugin.reload();
                    player.sendMessage(ChatColor.GREEN+"GrapplingHook has been reloaded.");
                } else {
                    plugin.reload();
                    sender.sendMessage("[GrapplingHook] Reloaded plugin.");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("give2")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml"));
                    ConfigurationSection hooksSection = config.getConfigurationSection("recipes");
                    for(String i : hooksSection.getKeys(false)) {
                        String id = hooksSection.getString(i + ".id");
                        GrapplingHookType hookType = new GrapplingHookType(id).createItemStack();
                        if (hookType != null) {
                            System.out.println(hookType.getItemStack());
                            player.getInventory().addItem(hookType.getItemStack());
                        } else {
                            player.sendMessage(ChatColor.RED+"Failed to give grappling hook with id: "+id);
                        }
                    }
                    player.sendMessage(ChatColor.GREEN+"Gave all grappling hooks to "+player.getName());
                } else {
                    sender.sendMessage("This command can only be used by players.");
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        List<String> results = new ArrayList<>();
        return results;
    }

    private void register()
            throws ReflectiveOperationException {
        final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        bukkitCommandMap.setAccessible(true);

        CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
        commandMap.register(this.getName(), this);
    }

    // Sorts possible results to provide true tab auto complete based off of what is already typed.
    public List <String> sortedResults(String arg, List<String> results) {
        final List <String> completions = new ArrayList < > ();
        StringUtil.copyPartialMatches(arg, results, completions);
        Collections.sort(completions);
        results.clear();
        for (String s: completions) {
            results.add(s);
        }
        return results;
    }
}
