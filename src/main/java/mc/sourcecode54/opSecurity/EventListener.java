package mc.sourcecode54.opSecurity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class EventListener implements Listener {
    private final OpSecurity plugin;
    private final ConfigManager configManager;
    private final PermissionHandler permissionHandler;
    private final LoginManager loginManager;

    public EventListener(OpSecurity plugin, ConfigManager configManager, PermissionHandler permissionHandler, LoginManager loginManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.permissionHandler = permissionHandler;
        this.loginManager = loginManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!permissionHandler.isStaff(player)) return; // Chỉ xử lý nếu là staff

        String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
        if (loginManager.isRegistered(player)) {
            // Yêu cầu đăng nhập nếu đã đăng ký
            player.sendMessage(ChatColor.YELLOW + "Vui lòng nhập mật khẩu qua GUI hoặc /opsec login <mật khẩu>");
            loginManager.openLoginGUI(player);
            if (configManager.reminderInterval > 0) {
                Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!loginManager.isAuthenticated(player) && player.isOnline()) {
                        if (Bukkit.getScheduler().getPendingTasks().stream()
                                .filter(task -> task.getOwner().equals(plugin))
                                .count() < 5) { // Giới hạn 5 lần nhắc nhở
                            player.sendMessage(ChatColor.YELLOW + "Nhắc nhở: Hãy đăng nhập bằng GUI hoặc /opsec login!");
                        } else {
                            Bukkit.getScheduler().cancelTasks(plugin); // Hủy nếu đã nhắc đủ 5 lần
                        }
                    } else {
                        Bukkit.getScheduler().cancelTasks(plugin); // Hủy nếu đã đăng nhập
                    }
                }, 20L * configManager.reminderInterval, 20L * configManager.reminderInterval);
            }
        }

        // Kiểm tra và gửi tin nhắn chưa đọc (quên mật khẩu, liên hệ admin)
        List<String> pendingMessages = configManager.getPendingMessages();
        if (!pendingMessages.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Bạn có tin nhắn chưa đọc (Rank: " + rank + "):");
            for (String msg : pendingMessages) {
                player.sendMessage(msg);
            }
            configManager.clearPendingMessages();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        loginManager.clearPlayerData(player); // Xóa dữ liệu khi player rời
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!permissionHandler.isStaff(player) || !loginManager.isRegistered(player) || loginManager.isAuthenticated(player)) {
            return; // Bỏ qua nếu không phải staff, chưa đăng ký, hoặc đã đăng nhập
        }

        String command = event.getMessage().split(" ")[0].substring(1).toLowerCase();
        if (!OpSecCommand.PLUGIN_COMMANDS.contains(command)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bạn phải đăng nhập trước khi dùng lệnh!");
            player.kickPlayer(ChatColor.RED + "Đăng nhập bằng GUI hoặc /opsec login!"); // Khôi phục kick cho lệnh
            configManager.logSecurityEvent(player.getName() + " đã bị chặn lệnh '" + command + "' do chưa đăng nhập.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!permissionHandler.isStaff(player) || !loginManager.isRegistered(player) || loginManager.isAuthenticated(player)) {
            return; // Bỏ qua nếu không phải staff, chưa đăng ký, hoặc đã đăng nhập
        }

        // Kiểm tra nếu tương tác block hoặc item (ăn, click block, dùng item)
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bạn phải đăng nhập trước khi tương tác!");
            player.kickPlayer(ChatColor.RED + "Đăng nhập bằng GUI hoặc /opsec login!"); // Kick khi tương tác block/item
            configManager.logSecurityEvent(player.getName() + " đã bị chặn tương tác '" + action.name() + "' do chưa đăng nhập.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
    }
}