package mc.sourcecode54.opSecurity;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
                new BukkitRunnable() {
                    int count = 0;
                    @Override
                    public void run() {
                        if (!loginManager.isAuthenticated(player) && player.isOnline()) {
                            if (count < 5) { // Giới hạn 5 lần nhắc nhở
                                player.sendMessage(ChatColor.YELLOW + "Nhắc nhở: Hãy đăng nhập bằng GUI hoặc /opsec login!");
                                count++;
                            } else {
                                cancel(); // Dừng nhắc nhở sau 5 lần
                            }
                        } else {
                            cancel(); // Dừng nếu đã đăng nhập
                        }
                    }
                }.runTaskTimer(plugin, 20L * configManager.reminderInterval, 20L * configManager.reminderInterval);
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
            player.kickPlayer(ChatColor.RED + "Đăng nhập bằng GUI hoặc /opsec login!");
            configManager.logSecurityEvent(player.getName() + " đã bị chặn lệnh '" + command + "' do chưa đăng nhập.");
        }
    }
}