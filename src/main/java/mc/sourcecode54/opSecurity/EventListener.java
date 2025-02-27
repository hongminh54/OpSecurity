package mc.sourcecode54.opSecurity;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class EventListener implements Listener { // Xử lý các event người chơi
    private final OpSecurity plugin; // Truy cập plugin
    private final ConfigManager configManager; // Quản lý file, quan trọng cho hộp thư
    private final PermissionHandler permissionHandler; // Xử lý quyền
    private final LoginManager loginManager; // Quản lý đăng nhập

    public EventListener(OpSecurity plugin, ConfigManager configManager, PermissionHandler permissionHandler, LoginManager loginManager) { // Khởi tạo
        this.plugin = plugin;
        this.configManager = configManager;
        this.permissionHandler = permissionHandler;
        this.loginManager = loginManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) { // Xử lý khi người chơi join, quan trọng cho hộp thư
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player) && loginManager.isRegistered(player)) {
            player.sendMessage(ChatColor.YELLOW + "Vui lòng nhập mật khẩu qua GUI hoặc /opsec login <mật khẩu>");
            loginManager.openLoginGUI(player);
            if (configManager.reminderInterval > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!loginManager.isAuthenticated(player) && player.isOnline()) {
                            player.sendMessage(ChatColor.YELLOW + "Nhắc nhở: Hãy đăng nhập bằng GUI hoặc /opsec login!");
                        } else cancel();
                    }
                }.runTaskTimer(plugin, 20L * configManager.reminderInterval, 20L * configManager.reminderInterval);
            }
        }
        if (permissionHandler.isStaff(player)) { // Kiểm tra tin nhắn chưa đọc, quan trọng cho hộp thư
            List<String> pendingMessages = configManager.getPendingMessages();
            if (!pendingMessages.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Bạn có tin nhắn chưa đọc:");
                for (String msg : pendingMessages) player.sendMessage(msg);
                configManager.clearPendingMessages();
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) { // Xử lý khi người chơi rời
        loginManager.clearPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) { // Chặn tương tác khi chưa đăng nhập
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player) && loginManager.isRegistered(player) && !loginManager.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Bạn phải đăng nhập trước khi tương tác!");
            player.kickPlayer(ChatColor.RED + "Đăng nhập bằng GUI hoặc /opsec login!");
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) { // Chặn lệnh khi chưa đăng nhập
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player) && loginManager.isRegistered(player) && !loginManager.isAuthenticated(player)) {
            String command = event.getMessage().split(" ")[0].substring(1).toLowerCase();
            if (!LoginManager.PLUGIN_COMMANDS.contains(command)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Bạn phải đăng nhập trước khi dùng lệnh!");
                player.kickPlayer(ChatColor.RED + "Đăng nhập bằng GUI hoặc /opsec login!");
            }
        }
    }
}