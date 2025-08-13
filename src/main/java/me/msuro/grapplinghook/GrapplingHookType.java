package me.msuro.grapplinghook;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

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

    @Getter @Setter private Vector velocityThrowMultiplier; // Velocity when thrown
    @Getter @Setter private Vector velocityPullMultiplier; // Velocity when pulled back

    @Getter @Setter private Boolean fallDamage; // Whether the hook causes fall damage
    @Getter @Setter private Boolean slowFall; // Whether the hook causes slow falling
    @Getter @Setter private Boolean lineBreak; // Whether the hook line can break
    @Getter @Setter private Boolean stickyHook; // Whether the hook sticks to blocks

    @Getter @Setter private String blocksMode; // 'ALLOW_ONLY', 'BLOCK_ONLY' - Type of the list of blocks that the hook can attach to
    @Getter @Setter private List<String> blocksList; // List of blocks that the hook can/cannot attach to

    @Getter @Setter private String entityMode; // 'ALLOW_ONLY', 'BLOCK_ONLY' - Type of the list of mobs that the hook can attach to
    @Getter @Setter private List<String> entityList; // List of mobs that the hook can/cannot attach to

    public GrapplingHookType(String name) {
        this.name = name;
    }

    /**
     * Creates a new GrapplingHookType item stack based on the current settings.
     * Generates a random ID for the hook and sets the item meta accordingly.
     *
     * @return The GrapplingHookType instance with the created ItemStack.
     * @throws IllegalArgumentException if the hook name is null or empty.
     */
    public GrapplingHookType createItemStack() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hook name cannot be null or empty");
        }

        // Generate a random ID for the hook - for now not really used for anything
        this.id = (int) (Math.random() * (9999999 - 1111111 + 1)) + 1111111;

        this.itemStack = new ItemStack(FISHING_ROD);

        fillMissingDataFromConfig();

        itemStack.setItemMeta(getReadyMeta());
        return this;
    }

    public GrapplingHookType fromConfig() {
        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = plugin.getHooksConfig();
        String path = "hooks." + name + ".settings.";

        this.maxUses = config.getInt(path + "max_uses", -1);
        this.uses = 0;

        // Load throwing speed vector
        double throwX = config.getDouble(path + "speed_settings.throwing_speed.x", 1.0);
        double throwY = config.getDouble(path + "speed_settings.throwing_speed.y", 1.0);
        double throwZ = config.getDouble(path + "speed_settings.throwing_speed.z", 1.0);
        this.velocityThrowMultiplier = new Vector(throwX, throwY, throwZ);

        // Load pulling speed vector
        double pullX = config.getDouble(path + "speed_settings.pulling_speed.x", 1.0);
        double pullY = config.getDouble(path + "speed_settings.pulling_speed.y", 1.0);
        double pullZ = config.getDouble(path + "speed_settings.pulling_speed.z", 1.0);
        this.velocityPullMultiplier = new Vector(pullX, pullY, pullZ);

        this.cooldown = config.getInt(path + "cooldown_seconds", 0);

        this.fallDamage = !config.getBoolean(path + "special_features.prevent_fall_damage", false);
        this.slowFall = config.getBoolean(path + "special_features.slow_falling", false);
        this.lineBreak = config.getBoolean(path + "special_features.break_on_disconnect", false);
        this.stickyHook = config.getBoolean(path + "special_features.sticky_landing", false);

        path = "hooks." + name + ".";

        if (blocksMode == null)
            blocksMode = config.getString(path + "allowed_blocks.mode", "ALLOW_ONLY");
        if (blocksList == null || blocksList.isEmpty())
            blocksList = config.getStringList(path + "allowed_blocks.block_list");

        if (entityMode == null)
            entityMode = config.getString(path + "allowed_entities.mode", "ALLOW_ONLY");
        if (entityList == null || entityList.isEmpty())
            entityList = config.getStringList(path + "allowed_entities.entity_list");

        createItemStack();
        return this;

    }

    /**
     * Creates a GrapplingHookType from an existing ItemStack.
     * The ItemStack must be a valid fishing rod with the necessary persistent data.
     *
     * @param is The ItemStack to create the GrapplingHookType from.
     * @return The GrapplingHookType created from the ItemStack.
     * @throws IllegalArgumentException if the ItemStack is not a valid fishing rod or does not contain the required data.
     */
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

    /**
     * Prepares the ItemMeta for the grappling hook item.
     * Sets the display name, lore, and persistent data.
     *
     * @return The prepared ItemMeta.
     * @throws IllegalStateException if the ItemStack or ItemMeta is not initialized properly.
     */
    @SuppressWarnings("deprecation")
    private ItemMeta getReadyMeta() {
        if (itemStack == null) {
            throw new IllegalStateException("ItemStack is not initialized. Call createItemStack() first.");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("ItemMeta is null. Ensure the ItemStack is valid.");
        }

        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = plugin.getHooksConfig();

        String usesPlaceholder = maxUses == -1 ? "∞" : String.valueOf(maxUses - uses);

        String itemName = config.getString("hooks." + name + ".item_display.name", name).replace("[uses]", usesPlaceholder);
        List<String> itemLore = config.getStringList("hooks." + name + ".item_display.description");
        meta.setDisplayName(plugin.formatMessage(itemName));
        itemLore.replaceAll(line -> plugin.formatMessage(line));
        if (!itemLore.isEmpty()) {
            itemLore.replaceAll(line -> line.replace("[uses]", usesPlaceholder));
            meta.setLore(itemLore);
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "id"), PersistentDataType.INTEGER, id);
        container.set(new NamespacedKey(plugin, "uses"), PersistentDataType.INTEGER, uses);
        container.set(new NamespacedKey(plugin, "name"), PersistentDataType.STRING, name);

        itemStack.setItemMeta(meta);
        return meta;
    }

    private void fillMissingDataFromConfig() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hook name cannot be null or empty");
        }

        GrapplingHook plugin = GrapplingHook.getPlugin();
        YamlConfiguration config = plugin.getHooksConfig();
        String path = "hooks." + name + ".settings.";

        if (maxUses == null)
            maxUses = config.getInt(path + "max_uses", -1);
        if (uses == null)
            uses = 0;
        if (velocityThrowMultiplier == null) {
            double throwX = config.getDouble(path + "speed_settings.throwing_speed.x", 1.0);
            double throwY = config.getDouble(path + "speed_settings.throwing_speed.y", 1.0);
            double throwZ = config.getDouble(path + "speed_settings.throwing_speed.z", 1.0);
            velocityThrowMultiplier = new Vector(throwX, throwY, throwZ);
        }
        if (velocityPullMultiplier == null) {
            double pullX = config.getDouble(path + "speed_settings.pulling_speed.x", 1.0);
            double pullY = config.getDouble(path + "speed_settings.pulling_speed.y", 1.0);
            double pullZ = config.getDouble(path + "speed_settings.pulling_speed.z", 1.0);
            velocityPullMultiplier = new Vector(pullX, pullY, pullZ);
        }
        if (cooldown == null)
            cooldown = config.getInt(path + "cooldown_seconds", 0);
        if (fallDamage == null)
            fallDamage = !config.getBoolean(path + "special_features.prevent_fall_damage", false);
        if (slowFall == null)
            slowFall = config.getBoolean(path + "special_features.slow_falling", false);
        if (lineBreak == null)
            lineBreak = config.getBoolean(path + "special_features.break_on_disconnect", false);
        if (stickyHook == null)
            stickyHook = config.getBoolean(path + "special_features.sticky_landing", false);

        path = "hooks." + name + ".";

        if (blocksMode == null)
            blocksMode = config.getString(path + "allowed_blocks.mode", "ALLOW_ONLY");
        if (blocksList == null || blocksList.isEmpty())
            blocksList = config.getStringList(path + "allowed_blocks.block_list");

        if (entityMode == null)
            entityMode = config.getString(path + "allowed_entities.mode", "ALLOW_ONLY");
        if (entityList == null || entityList.isEmpty())
            entityList = config.getStringList(path + "allowed_entities.entity_list");
    }

    @Override
    public String toString() {
        return "GrapplingHookType{" +
                "maxUses=" + maxUses +
                ", name='" + name + '\'' +
                ", uses=" + uses +
                ", id=" + id +
                ", cooldown=" + cooldown +
                ", velocityThrowMultiplier=" + velocityThrowMultiplier +
                ", velocityPullMultiplier=" + velocityPullMultiplier +
                ", fallDamage=" + fallDamage +
                ", slowFall=" + slowFall +
                ", lineBreak=" + lineBreak +
                ", stickyHook=" + stickyHook +
                ", blocksMode='" + blocksMode + '\'' +
                ", blocksList=" + blocksList +
                ", mobsMode='" + entityMode + '\'' +
                ", mobsList=" + entityList +
                ", itemStack=" + (itemStack != null ? itemStack.getType() : "null") +
                '}';
    }



}
