package me.msuro.grapplinghook;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.List;

import static org.bukkit.Material.FISHING_ROD;

public class GrapplingHookType {

    // Settings
    @Getter @Setter private Integer maxUses;
    // Future: AirHook, Sticky, FluidSticky, SlowFall, FallDamage, CustomModelData, Cooldown

    @Getter @Setter private String name; // Name from the config (to identify the hook type)
    @Getter @Setter private Integer uses;
    @Getter @Setter private Integer id; // Unique ID for the hook type
    @Getter @Setter private Integer cooldown; // Cooldown in seconds
    @Getter @Setter private ItemStack itemStack;

    @Getter @Setter private Integer velocityThrow; // Velocity when thrown
    @Getter @Setter private Integer velocityPull; // Velocity when pulled back

    @Getter @Setter private Boolean fallDamage; // Whether the hook causes fall damage
    @Getter @Setter private Boolean slowFall; // Whether the hook causes slow falling
    @Getter @Setter private Boolean lineBreak; // Whether the hook line can break
    @Getter @Setter private Boolean stickyHook; // Whether the hook sticks to blocks

    public GrapplingHookType(String name) {
        this.name = name;
    }

    public GrapplingHookType createItemStack() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hook name cannot be null or empty");
        }
        this.id = (int) (Math.random() * (9999999 - 1111111 + 1)) + 1111111;
        this.itemStack = new ItemStack(FISHING_ROD);
        itemStack.setItemMeta(getReadyMeta());
        return this;
    }

    public GrapplingHookType fromConfig() {
        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hooks.yml"));
        String path = "hooks." + name + ".settings.";

        this.maxUses = config.getInt(path + "uses", -1);
        this.uses = maxUses;

        this.velocityThrow = config.getInt(path + "velocityThrow", 1);
        this.velocityPull = config.getInt(path + "velocityPull", 1);

        this.cooldown = config.getInt(path + "cooldown", 0);

        this.fallDamage = config.getBoolean(path + "extra.FALLDAMAGE", true);
        this.slowFall = config.getBoolean(path + "extra.SLOWFALL", false);
        this.lineBreak = config.getBoolean(path + "extra.LINEBREAK", true);
        this.stickyHook = config.getBoolean(path + "extra.STICKYHOOK", false);

        createItemStack();
        return this;

    }

    public GrapplingHookType fromItemStack(ItemStack is) {
        if (is == null || is.getType() != FISHING_ROD) {
            throw new IllegalArgumentException("ItemStack must be a valid fishing rod");
        }
        this.itemStack = is;
        ItemMeta meta = is.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("ItemMeta is null. Ensure the ItemStack is valid.");
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if(!container.has(new NamespacedKey(GrapplingHook.getPlugin(), "id"), PersistentDataType.INTEGER)) {
            throw new IllegalArgumentException("ItemStack does not contain valid GrapplingHook data");
        }
        if(!container.has(new NamespacedKey(GrapplingHook.getPlugin(), "uses"), PersistentDataType.INTEGER)) {
            throw new IllegalArgumentException("ItemStack does not contain valid GrapplingHook uses data");
        }
        if(!container.has(new NamespacedKey(GrapplingHook.getPlugin(), "name"), PersistentDataType.STRING)) {
            throw new IllegalArgumentException("ItemStack does not contain valid GrapplingHook name data");
        }
        this.id = container.get(new NamespacedKey(GrapplingHook.getPlugin(), "id"), PersistentDataType.INTEGER);
        this.uses = container.get(new NamespacedKey(GrapplingHook.getPlugin(), "uses"), PersistentDataType.INTEGER);
        this.name = container.get(new NamespacedKey(GrapplingHook.getPlugin(), "name"), PersistentDataType.STRING);

        fillMissingDataFromConfig();

        return this;
    }

    private ItemMeta getReadyMeta() {
        if (itemStack == null) {
            throw new IllegalStateException("ItemStack is not initialized. Call createItemStack() first.");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("ItemMeta is null. Ensure the ItemStack is valid.");
        }

        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hooks.yml"));

        String itemName = config.getString("hooks." + name + ".item.name", name);
        List<String> itemLore = config.getStringList("hooks." + name + ".item.lore");
        meta.setDisplayName(plugin.formatMessage(itemName));
        itemLore.replaceAll(line -> plugin.formatMessage(line));
        if (!itemLore.isEmpty()) {
            meta.setLore(itemLore);
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "id"), PersistentDataType.INTEGER, id);
        container.set(new NamespacedKey(plugin, "uses"), PersistentDataType.INTEGER, uses);
        container.set(new NamespacedKey(plugin, "name"), PersistentDataType.STRING, name);

        itemStack.setItemMeta(meta);
        return meta;
    }

    private GrapplingHookType fillMissingDataFromConfig() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hook name cannot be null or empty");
        }

        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hooks.yml"));
        String path = "hooks." + name + ".settings.";

        if (maxUses == null)
            maxUses = config.getInt(path + "uses", -1);
        if (uses == null)
            uses = 0;
        if (velocityThrow == null)
            velocityThrow = config.getInt(path + "velocityThrow", 0);
        if (velocityPull == null)
            velocityPull = config.getInt(path + "velocityPull", 0);
        if (cooldown == null)
            cooldown = config.getInt(path + "cooldown", 0);
        if (fallDamage == null)
            fallDamage = config.getBoolean(path + "extra.FALLDAMAGE", true);
        if (slowFall == null)
            slowFall = config.getBoolean(path + "extra.SLOWFALL", false);
        if (lineBreak == null)
            lineBreak = config.getBoolean(path + "extra.LINEBREAK", true);
        if (stickyHook == null)
            stickyHook = config.getBoolean(path + "extra.STICKYHOOK", false);

        return this;
    }

}

/*
hooks:
  air_hook:
    settings:
      enabled: true
      uses: 10
      # Throw - how far the hook will go when thrown
      velocityThrow: 1.0
        # Pull - how far the player will be pulled when the hook is used
      velocityPull: 1.0
      cooldown: 0 # in seconds
      extra:
        SLOWFALL: false
        FALLDAMAGE: false
        LINEBREAK: false
        STICKYHOOK: false
    item:
      name: '&6Air Grappling Hook'
      lore:
      - '&7Uses left - &a[uses]'
      customModelData: 0
    blocks:
      listType: 'WHITELIST' # options are BLACKLIST and WHITELIST
      list:
      - 'AIR'
    entities:
      listType: 'WHITELIST' # options are BLACKLIST and WHITELIST
      list:
      - ''
 */