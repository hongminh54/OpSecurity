package mc.sourcecode54.opSecurity;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginManager implements Listener {
    private final OpSecurity plugin;
    private final ConfigManager config;
    private final PermissionHandler perms;
    private final Map<UUID, Boolean> authenticated = new HashMap<>();
    private final Map<UUID, Inventory> guis = new HashMap<>();
    private final Map<UUID, StringBuilder> inputs = new HashMap<>();

    public LoginManager(OpSecurity plugin, ConfigManager config, PermissionHandler perms) {
        this.plugin = plugin;
        this.config = config;
        this.perms = perms;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isRegistered(Player player) {
        return config.isRegistered(player.getUniqueId().toString());
    }

    public boolean isRegistered(OfflinePlayer offlinePlayer) {
        return config.isRegistered(offlinePlayer.getUniqueId().toString());
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }

    public void setAuthenticated(Player player) {
        authenticated.put(player.getUniqueId(), true);
    }

    public void clearPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        guis.remove(uuid);
        inputs.remove(uuid);
        authenticated.put(uuid, false);
    }

    public void openLoginGUI(Player player) {
        if (!config.enableLoginGUI) {
            player.sendMessage(config.getMessage("login-cli-prompt", null));
            return;
        }
        if (!player.hasPermission("opsecurity.login")) {
            player.sendMessage(config.getMessage("no-permission", null));
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Đăng nhập Staff");
        ItemStack enter = new ItemStack(getMaterial("GREEN_WOOL", "LIME_DYE"), 1);
        ItemMeta enterMeta = enter.getItemMeta();
        enterMeta.setDisplayName(ChatColor.GREEN + "Nhập mật khẩu (Chat)");
        enter.setItemMeta(enterMeta);

        ItemStack forgot = new ItemStack(getMaterial("RED_WOOL", "RED_DYE"), 1);
        ItemMeta forgotMeta = forgot.getItemMeta();
        forgotMeta.setDisplayName(ChatColor.RED + "Quên mật khẩu");
        forgot.setItemMeta(forgotMeta);

        gui.setItem(3, enter);
        gui.setItem(5, forgot);

        player.openInventory(gui);
        guis.put(player.getUniqueId(), gui);
        inputs.put(player.getUniqueId(), new StringBuilder());
        setAuthenticated(player);
    }

    private Material getMaterial(String modern, String legacy) {
        try { return Material.valueOf(modern); } catch (IllegalArgumentException e) { return Material.valueOf(legacy); }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        if (!guis.containsKey(uuid) || event.getInventory() != guis.get(uuid)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String name = item.getItemMeta().getDisplayName();
        if (name.equals(ChatColor.GREEN + "Nhập mật khẩu (Chat)")) {
            if (!player.hasPermission("opsecurity.login")) {
                player.sendMessage(config.getMessage("no-permission", null));
                player.closeInventory();
                return;
            }
            player.sendMessage(config.getMessage("login-chat-prompt", Map.of("method", "chat")));
            player.closeInventory();
        } else if (name.equals(ChatColor.RED + "Quên mật khẩu")) {
            if (!player.hasPermission("opsecurity.forgot")) {
                player.sendMessage(config.getMessage("no-permission", null));
                player.closeInventory();
                return;
            }
            sendContactRequest(player, config.getDefaultOrValidRank(perms.getPlayerRank(player)), "forgot", null);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (inputs.containsKey(uuid)) {
            event.setCancelled(true);
            if (!player.hasPermission("opsecurity.login")) {
                player.sendMessage(config.getMessage("no-permission", null));
                inputs.remove(uuid);
                guis.remove(uuid);
                return;
            }
            String msg = event.getMessage();
            StringBuilder pwd = inputs.get(uuid);
            pwd.append(msg);
            String stored = config.getPassword(uuid.toString());
            if (pwd.toString().equals(stored)) {
                String rank = config.getRank(uuid.toString());
                if (!config.isValidRank(rank)) {
                    rank = config.getDefaultOrValidRank(perms.getPlayerRank(player));
                }
                setAuthenticated(player);
                player.sendMessage(config.getMessage("login-success", Map.of("rank", rank)));
                config.logSecurityEvent(config.getMessage("login-gui-success-log", Map.of("player", player.getName(), "rank", rank)));
                guis.remove(uuid);
                inputs.remove(uuid);
                clearPlayerData(player);
            } else {
                player.sendMessage(config.getMessage("login-failure", null));
                config.logSecurityEvent(config.getMessage("login-log-failure", Map.of("player", player.getName())));
                inputs.put(uuid, new StringBuilder());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !isAuthenticated(player)) openLoginGUI(player);
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (guis.containsKey(uuid) && !isAuthenticated(player)) {
            player.sendMessage(config.getMessage("login-close-prompt", null));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (perms.isStaff(player)) {
            String rank = config.getRank(player.getUniqueId().toString());
            if (!config.isValidRank(rank)) {
                rank = config.getDefaultOrValidRank(perms.getPlayerRank(player));
            }
            if (isRegistered(player) && config.enableLoginGUI) {
                if (player.hasPermission("opsecurity.login")) {
                    openLoginGUI(player);
                } else {
                    player.sendMessage(config.getMessage("no-permission", null));
                }
            } else if (isRegistered(player)) {
                if (player.hasPermission("opsecurity.login")) {
                    player.sendMessage(config.getMessage("login-cli-prompt", Map.of("rank", rank)));
                } else {
                    player.sendMessage(config.getMessage("no-permission", null));
                }
            }
        }
    }

    public void sendContactRequest(Player player, String rank, String type, String message) {
        String baseMsg = player.getName() + " (Rank: " + rank + ") " + (type.equals("forgot") ? "cần reset mật khẩu" : "gửi tin nhắn: " + message);
        for (Player admin : plugin.getServer().getOnlinePlayers()) {
            if (perms.isStaff(admin) && admin.hasPermission("opsecurity.forgot")) {
                admin.sendMessage(ChatColor.YELLOW + baseMsg);
            }
        }
        if (!plugin.getServer().getOnlinePlayers().stream().anyMatch(p -> perms.isStaff(p) && p.hasPermission("opsecurity.forgot"))) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("discordLink", config.discordLink);
            placeholders.put("facebookLink", config.facebookLink);
            player.sendMessage(config.getMessage("contact-discord", placeholders));
            player.sendMessage(config.getMessage("contact-facebook", placeholders));
        }
        config.logSecurityEvent(player.getName() + " " + (type.equals("forgot") ? "yêu cầu reset" : "gửi tin nhắn") + " với rank " + rank + (message != null ? ": " + message : "") + ".");
        if (type.equals("forgot")) {
            player.sendMessage(config.getMessage("reset-notify", null));
        } else {
            player.sendMessage(config.getMessage("contact-sent-offline", null));
        }
    }
}