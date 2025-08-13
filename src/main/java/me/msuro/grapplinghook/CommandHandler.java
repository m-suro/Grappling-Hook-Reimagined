package me.msuro.grapplinghook;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

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
        } catch (Exception e) {
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
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.commands-help")));
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.command-reload").replace("[command]", this.getName())));
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.command-give-self").replace("[command]", this.getName())));
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.command-give-player").replace("[command]", this.getName())));
                    return true;
                } else {
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no-permission")));
                    return true;
                }
            }
            //these are commands that can be executed from the console
            else {
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.commands-help")));
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.command-reload").replace("[command]", this.getName())));
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.command-give-player").replace("[command]", this.getName())));
                return true;
            }
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (player.hasPermission("grapplinghook.operator") || player.isOp()) {
                        plugin.reload();
                        player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.plugin-reloaded-player")));
                    } else {
                        player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no-permission")));
                    }
                } else {
                    plugin.reload();
                    sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.plugin-reloaded-console")));
                }
                return true;
            } else if (args[0].equalsIgnoreCase("give")) {
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.hook-not-specified"))
                        .replace("[command]", this.getName()));
            }
            return true;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (player.hasPermission("grapplinghook.give")) {
                        String hookName = args[1];
                        ConfigurationSection hooksSection = plugin.getHooksConfig().getConfigurationSection("hooks");
                        if (hooksSection != null && hooksSection.contains(hookName)) {
                            GrapplingHookType hookType = new GrapplingHookType(hookName).createItemStack();
                            if (hookType == null) {
                                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.give-hook-failed").replace("[hook_id]", hookName)));
                                return true;
                            }
                            if (player.getInventory().firstEmpty() == -1) {
                                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.inventory-full")));
                                return true;
                            }
                            player.getInventory().addItem(hookType.getItemStack());
                            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.hook-given-self").replace("[hook_id]", hookName)));
                        } else {
                            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.hook-not-found").replace("[hook_id]", hookName)));
                        }
                    } else {
                        player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no-permission")));
                    }
                } else {
                    sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.player-not-specified"))
                            .replace("[command]", this.getName()));
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                String hookName = args[1];
                String pName = args[2];
                Player player = Bukkit.getPlayer(pName);
                if (player == null || !player.isOnline()) {
                    String message = plugin.getConfig().getString("messages.player-not-found").replace("[player]", pName);
                    sender.sendMessage(plugin.formatMessage(message));
                    return true;
                }
                ConfigurationSection hooksSection = plugin.getHooksConfig().getConfigurationSection("hooks");
                if (hooksSection != null && hooksSection.contains(hookName)) {
                    GrapplingHookType hookType = new GrapplingHookType(hookName).createItemStack();
                    if (hookType == null) {
                        String message = plugin.getConfig().getString("messages.give-hook-failed").replace("[hook_id]", hookName);
                        sender.sendMessage(plugin.formatMessage(message));
                        return true;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        String message = plugin.getConfig().getString("messages.inventory-full-player");
                        sender.sendMessage(plugin.formatMessage(message));
                        return true;
                    }
                    player.getInventory().addItem(hookType.getItemStack());

                    // Send success message to command sender
                    String senderMessage = plugin.getConfig().getString("messages.hook-given-player")
                            .replace("[hook_id]", hookName)
                            .replace("[player]", player.getName());
                    sender.sendMessage(plugin.formatMessage(senderMessage));

                    // Send notification to receiving player
                    String receiverMessage = plugin.getConfig().getString("messages.hook-received").replace("[hook_id]", hookName);
                    player.sendMessage(plugin.formatMessage(receiverMessage));
                } else {
                    String message = plugin.getConfig().getString("messages.hook-not-found").replace("[hook_id]", hookName);
                    sender.sendMessage(plugin.formatMessage(message));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        List<String> results = new ArrayList<>();
        if (args.length == 1) {
            // First argument: command name
            List<String> subcommands = new ArrayList<>();
            subcommands.add("reload");
            subcommands.add("give");
            return sortedResults(args[0], subcommands);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Second argument: hook name
            ConfigurationSection hooksSection = plugin.getHooksConfig().getConfigurationSection("hooks");
            if (hooksSection != null) {
                List<String> hookNames = new ArrayList<>(hooksSection.getKeys(false));
                return sortedResults(args[1], hookNames);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Third argument: player name (only online players for better UX)
            List<String> playerNames = new ArrayList<>();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                playerNames.add(onlinePlayer.getName());
            }
            return sortedResults(args[2], playerNames);
        }
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
    public List<String> sortedResults(String arg, List<String> results) {
        final List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(arg, results, completions);
        Collections.sort(completions);
        results.clear();
        for (String s : completions) {
            results.add(s);
        }
        return results;
    }
}
