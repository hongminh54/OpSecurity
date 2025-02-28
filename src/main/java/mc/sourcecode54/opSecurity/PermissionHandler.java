package mc.sourcecode54.opSecurity;

import org.bukkit.entity.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class PermissionHandler {
    private final OpSecurity plugin;
    private final ConfigManager config;
    private final LuckPerms luckPerms;

    public PermissionHandler(OpSecurity plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (this.luckPerms == null) {
            plugin.getLogger().severe("LuckPerms không được tìm thấy! Vui lòng cài đặt plugin LuckPerms.");
        }
    }

    public boolean isStaff(Player player) {
        if (config.useLuckPerms && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getInheritedGroups(QueryOptions.defaultContextualOptions()).stream()
                        .anyMatch(group -> group.getName().equalsIgnoreCase("staff") || group.getName().equalsIgnoreCase("admin") || group.getName().equalsIgnoreCase("owner"));
            }
            return false;
        } else {
            // Fallback nếu không dùng LuckPerms, kiểm tra trong staff.yml hoặc data.yml
            return config.getStaffConfig() != null && config.getStaffConfig().getStringList("staff").contains(player.getName());
        }
    }

    public String getPlayerRank(Player player) {
        if (config.useLuckPerms && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "Default";
            }
            return "Default";
        } else {
            // Fallback nếu không dùng LuckPerms, lấy rank từ data.yml
            return config.getRank(player.getUniqueId().toString());
        }
    }

    public void reload() {
        if (config.useLuckPerms && luckPerms != null) {
            // Reload dữ liệu từ LuckPerms (nếu cần)
            plugin.getLogger().info("Đang reload quyền hạn từ LuckPerms...");
            // Có thể gọi luckPerms.getUserManager().loadAllUsers() hoặc các phương thức khác
        } else {
            // Reload từ staff.yml hoặc data.yml
            config.loadFiles();
            plugin.getLogger().info("Đang reload quyền hạn từ file cấu hình...");
        }
    }
}