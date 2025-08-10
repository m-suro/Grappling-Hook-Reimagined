package me.msuro.grapplinghook;

import lombok.Getter;
import me.msuro.grapplinghook.listeners.PlayerListener;
import me.msuro.grapplinghook.utils.CooldownSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrapplingHook extends JavaPlugin{
	
	private PlayerListener playerListener = new PlayerListener(this);
	private CommandHandler commandHandler;
	@Getter
    private static GrapplingHook plugin;
	protected FileConfiguration config;

	boolean usePerms = false;
	private boolean teleportHooked = false;
    private boolean consumeUseOnSlowfall = false;
	private String commandAlias;

    @Getter
    private boolean isServerVersionAtLeast1_21_2 = false;


	public void onEnable(){
		plugin = this;


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
            getLogger().warning("The new cooldown system is not available on versions below 1.21.2 as the API does not support it.");
            getLogger().warning("Please consider updating your server to 1.21.2+ for the best experience.");
        }

		getServer().getPluginManager().registerEvents(playerListener, this);
		
		File configFile = new File(this.getDataFolder() + "/config.yml");
        checkAndCreateConfigFile("config.yml");
        checkAndCreateConfigFile("hooks.yml");
		config = YamlConfiguration.loadConfiguration(configFile);

		commandAlias = config.getString("command");


		commandHandler = new CommandHandler(this, "grapplinghook.operator", commandAlias, "Base command for the GrapplingHook plugin", "/gh", new ArrayList(Arrays.asList(commandAlias)));
	}

	public void onDisable(){
	}

	public void reload(){
		HandlerList.unregisterAll(playerListener);

		onDisable();
		onEnable();
	}


    public PlayerListener getPlayerListener(){
		return playerListener;
	}

	public boolean isConsumeUseOnSlowfall(){
		return consumeUseOnSlowfall;
	}

	public boolean getTeleportHooked(){
		return teleportHooked;
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    private void checkAndCreateConfigFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
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
        if (message == null || message.isEmpty()) {
            return "";
        }
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

}