package mc.sourcecode54.opSecurity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

public class EventListener implements Listener {
    private final OpSecurity plugin;
    private final ConfigManager config;
    private final PermissionHandler perms;
    private final LoginManager loginMgr;

    public EventListener(OpSecurity plugin, ConfigManager config, PermissionHandler perms, LoginManager loginMgr) {
        this.plugin = plugin;
        this.config = config;
        this.perms = perms;
        this.loginMgr = loginMgr;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!perms.isStaff(player)) return;
        if (loginMgr.isRegistered(player)) {
            if (config.enableLoginGUI) {
                player.sendMessage(config.getMessage("login-gui-prompt", null));
                loginMgr.openLoginGUI(player);
            } else {
                player.sendMessage(config.getMessage("login-cli-prompt", null));
            }
            if (config.reminderInterval > 0) {
                Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!loginMgr.isAuthenticated(player) && player.isOnline()) {
                        player.sendMessage(config.getMessage("login-reminder", null));
                        if (Bukkit.getScheduler().getPendingTasks().stream()
                                .filter(t -> t.getOwner().equals(plugin)).count() >= 5) {
                            Bukkit.getScheduler().cancelTasks(plugin);
                        }
                    } else {
                        Bukkit.getScheduler().cancelTasks(plugin);
                    }
                }, 20L * config.reminderInterval, 20L * config.reminderInterval);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        loginMgr.clearPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!perms.isStaff(player) || !loginMgr.isRegistered(player) || loginMgr.isAuthenticated(player)) return;

        String command = event.getMessage().split(" ")[0].substring(1).toLowerCase();
        if (!OpSecCommand.PLUGIN_COMMANDS.contains(command) && !command.startsWith("opsec") && !command.startsWith("os")) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("login-required-command", null));
            player.kickPlayer(config.getMessage("login-kick-command", null));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("command", command);
            config.logSecurityEvent(config.getMessage("command-blocked-log", placeholders));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!perms.isStaff(player) || !loginMgr.isRegistered(player) || loginMgr.isAuthenticated(player)) return;

        Action action = event.getAction();
        boolean isHandInteraction = true;

        // Kiểm tra phiên bản server để sử dụng getHand()
        if (Bukkit.getServer().getBukkitVersion().contains("1.8") || Bukkit.getServer().getBukkitVersion().contains("1.7")) {
            // Với Minecraft 1.8.x, không có getHand(), chỉ kiểm tra action
            isHandInteraction = true;  // Giả định tất cả tương tác đều từ tay chính (1.8 không phân biệt tay)
        } else {
            // Với Minecraft 1.9.x trở lên, kiểm tra hand
            EquipmentSlot hand = event.getHand();
            isHandInteraction = (hand == EquipmentSlot.HAND || hand == EquipmentSlot.OFF_HAND);
        }

        if ((action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR ||
                action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) && isHandInteraction) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("login-required-interact", null));
            player.kickPlayer(config.getMessage("login-kick-interact", null));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("action", action.name());
            config.logSecurityEvent(config.getMessage("interact-blocked-log", placeholders));
        }
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!perms.isStaff(player) || !loginMgr.isRegistered(player) || loginMgr.isAuthenticated(player)) return;

        event.setCancelled(true);
        player.sendMessage(config.getMessage("login-required-interact", null));
        player.kickPlayer(config.getMessage("login-kick-interact", null));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("action", "CONSUME_ITEM");
        config.logSecurityEvent(config.getMessage("interact-blocked-log", placeholders));
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!perms.isStaff(player) || !loginMgr.isRegistered(player) || loginMgr.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("login-required-command", null));
            player.kickPlayer(config.getMessage("login-kick-command", null));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("command", "CHAT");
            config.logSecurityEvent(config.getMessage("command-blocked-log", placeholders));
        }
    }
}