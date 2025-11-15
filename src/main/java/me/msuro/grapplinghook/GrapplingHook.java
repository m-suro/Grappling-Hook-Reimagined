package me.msuro.grapplinghook;

import lombok.Getter;
import lombok.Setter;
import me.msuro.grapplinghook.listeners.InventoryListener;
import me.msuro.grapplinghook.listeners.PlayerListener;
import me.msuro.grapplinghook.utils.CooldownSystem;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrapplingHook extends JavaPlugin{

    @Getter
	private PlayerListener playerListener = new PlayerListener(this);
    @Getter
    private InventoryListener inventoryListener = new InventoryListener(this);
    @Getter
	private CommandHandler commandHandler;
	@Getter
    private static GrapplingHook plugin;

    @Getter
    private boolean isServerVersionAtLeast1_21_2 = false;

    @Getter
    private YamlConfiguration hooksConfig;
    @Getter
    private YamlConfiguration config;

    @Getter @Setter
    private List<Entity> armorStandList = new ArrayList<>();


    Metrics metrics;


    public void onEnable(){
		plugin = this;

        // bStats - https://bstats.org/plugin/bukkit/Grappling%20Hook/26907
        int pluginId = 26907;
        metrics = new Metrics(this, pluginId);


        try {
            // e.g., "1.21.4-R0.1-SNAPSHOT"
            String bukkit = Bukkit.getBukkitVersion();
            String core = bukkit.split("-")[0];          // "1.21.4"
            String[] parts = core.split("\\.");          // ["1","21","4"]

            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;   // 1
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;   // 21
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;   // 4

            isServerVersionAtLeast1_21_2 = (major > 1) || (major == 1 && minor >= 21 && patch >= 2);
        } catch (Exception ex) {
            getLogger().warning("Unable to parse Bukkit version: " + Bukkit.getBukkitVersion());
            // Fallback: assume not 1.21+
            isServerVersionAtLeast1_21_2 = false;
        }

        if (!isServerVersionAtLeast1_21_2) {
            getLogger().warning("This plugin is working best on 1.21.2+ versions of Minecraft.");
            getLogger().warning("The cooldown system is not available on versions below 1.21.2 as the API does not support it.");
            getLogger().warning("Please consider updating your server to 1.21.2+ for the best experience.");
        }

		getServer().getPluginManager().registerEvents(playerListener, this);
		getServer().getPluginManager().registerEvents(inventoryListener, this);

		File configFile = new File(this.getDataFolder() + "/config.yml");
        checkAndCreateConfigFile("config.yml");
        checkAndCreateConfigFile("hooks.yml");
		config = YamlConfiguration.loadConfiguration(configFile);
        hooksConfig = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "hooks.yml"));


		commandHandler = new CommandHandler(this, "grapplinghook.operator", "grapplinghook", "Base command for the GrapplingHook plugin", "/gh", new ArrayList(Arrays.asList("gh")));

	}

	public void onDisable(){
        metrics.shutdown();
        HandlerList.unregisterAll(playerListener);
        HandlerList.unregisterAll(inventoryListener);
        if (commandHandler != null) {
            try {
                Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                commandMapField.setAccessible(true);
                CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
                commandMap.getKnownCommands().remove(commandHandler.getName());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                getLogger().severe("Failed to unregister command handler: " + e.getMessage());
            }
        }

        for(Entity entity : armorStandList) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }

	}

	public void reload(){
		HandlerList.unregisterAll(playerListener);
		HandlerList.unregisterAll(inventoryListener);

		onDisable();
		onEnable();
	}

	private void copy(InputStream in, File file) {
		try {
			OutputStream out = new FileOutputStream(file);
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.close();
			in.close();
            getLogger().info("Config file " + file.getName() + " created successfully.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    private void checkAndCreateConfigFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            try {
                if (file.createNewFile()) {
                    InputStream in = getResource(fileName);
                    if (in != null) {
                        getLogger().info("Creating config file: " + fileName);
                        copy(in, file);
                    } else {
                        getLogger().warning("Resource " + fileName + " not found in plugin jar.");
                    }
                }
            } catch (IOException e) {
                getLogger().severe("Could not create config file: " + fileName);
                e.printStackTrace();
            }
        }
    }

    public String formatMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        message = message.replace("[prefix]", config.getString("prefix", "[GrapplingHook]"));
        Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder formattedMessage = new StringBuilder();
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String colorCode = "§x§" + hexColor.charAt(0) + "§" + hexColor.charAt(1) + "§" + hexColor.charAt(2) +
                    "§" + hexColor.charAt(3) + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5);
            matcher.appendReplacement(formattedMessage, colorCode);
        }
        matcher.appendTail(formattedMessage);
        return ChatColor.translateAlternateColorCodes('&', formattedMessage.toString());
    }

    public void sendFormattedMessage(CommandSender sender, String rawMessage) {
        String formatted = formatMessage(rawMessage);
        if (formatted != null && !formatted.trim().isEmpty()) {
            sender.sendMessage(formatted);
        }
    }

    public void sendFormattedActionBar(Player player, String rawMessage) {
        String formatted = formatMessage(rawMessage);
        if (formatted != null && !formatted.trim().isEmpty()) {
            player.sendActionBar(formatted);
        }
    }

}