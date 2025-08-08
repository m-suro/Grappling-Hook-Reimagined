package me.msuro.grapplinghook;

import lombok.Getter;
import me.msuro.grapplinghook.listeners.PlayerListener;
import me.msuro.grapplinghook.utils.ConfigUpdater;
import me.msuro.grapplinghook.utils.RecipeLoader;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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

	private boolean usePerms = false;
	private boolean teleportHooked = false;
    private boolean consumeUseOnSlowfall = false;
	private String commandAlias;
	private RecipeLoader recipeLoader;


	public void onEnable(){
		plugin = this;
		getServer().getPluginManager().registerEvents(playerListener, this);
		
		File configFile = new File(this.getDataFolder() + "/config.yml");
		if(!configFile.exists())
		{
		  this.saveDefaultConfig();
		}
        try {
            ConfigUpdater.update(plugin, "config.yml", configFile, new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }
		config = YamlConfiguration.loadConfiguration(configFile);

		commandAlias = config.getString("command");


		commandHandler = new CommandHandler(this, "grapplinghook.operator", commandAlias, "Base command for the GrapplingHook plugin", "/gh", new ArrayList(Arrays.asList(commandAlias)));
	}

	public void onDisable(){
		recipeLoader.unloadRecipes();
	}

	public void reload(){
		HandlerList.unregisterAll(playerListener);

		onDisable();
		onEnable();
	}

	public RecipeLoader getRecipeLoader(){
		return recipeLoader;
	}

    public PlayerListener getPlayerListener(){
		return playerListener;
	}

	public boolean isConsumeUseOnSlowfall(){
		return consumeUseOnSlowfall;
	}

	public boolean usePerms(){
		return usePerms;
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