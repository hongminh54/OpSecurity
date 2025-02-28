package mc.sourcecode54.opSecurity;

import org.bukkit.OfflinePlayer;
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
                        .anyMatch(group -> config.getValidRanks().stream().anyMatch(valid -> valid.equalsIgnoreCase(group.getName())));
            }
            return false;
        } else {
            return config.getStaffConfig() != null && config.getStaffConfig().getStringList("staff").contains(player.getName());
        }
    }

    public boolean isStaff(OfflinePlayer offlinePlayer) {
        if (config.useLuckPerms && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
            if (user != null) {
                return user.getInheritedGroups(QueryOptions.defaultContextualOptions()).stream()
                        .anyMatch(group -> config.getValidRanks().stream().anyMatch(valid -> valid.equalsIgnoreCase(group.getName())));
            }
            return false;
        } else {
            return config.getDataConfig() != null && config.getDataConfig().contains(offlinePlayer.getUniqueId().toString() + ".rank");
        }
    }

    public String getPlayerRank(Player player) {
        if (config.useLuckPerms && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String primaryGroup = user.getPrimaryGroup();
                if (primaryGroup != null && config.isValidRank(primaryGroup)) {
                    return primaryGroup;
                }
                return config.getDefaultOrValidRank("Default");
            }
            return "Default";
        } else {
            return config.getRank(player.getUniqueId().toString());
        }
    }

    public String getPlayerRank(OfflinePlayer offlinePlayer) {
        if (config.useLuckPerms && luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
            if (user != null) {
                String primaryGroup = user.getPrimaryGroup();
                if (primaryGroup != null && config.isValidRank(primaryGroup)) {
                    return primaryGroup;
                }
                return config.getDefaultOrValidRank("Default");
            }
            return "Default";
        } else {
            return config.getRank(offlinePlayer.getUniqueId().toString());
        }
    }

    public void reload() {
        if (config.useLuckPerms && luckPerms != null) {
            plugin.getLogger().info("Đang reload quyền hạn từ LuckPerms...");
        } else {
            config.loadFiles();
            plugin.getLogger().info("Đang reload quyền hạn từ file cấu hình...");
        }
    }
}