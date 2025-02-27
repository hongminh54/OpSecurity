package mc.sourcecode54.opSecurity;

import org.bukkit.entity.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

public class PermissionHandler {
    private final OpSecurity plugin;
    private final ConfigManager configManager;
    private LuckPerms luckPermsAPI = null;

    public PermissionHandler(OpSecurity plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setupPermissions();
    }

    private void setupPermissions() {
        if (!configManager.useLuckPerms) {  // Dùng configManager.useLuckPerms
            plugin.getLogger().info("LuckPerms bị tắt trong config.");
            return;
        }

        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsAPI = LuckPermsProvider.get();
                plugin.getLogger().info("Đã kết nối với LuckPerms!");
            } catch (IllegalStateException e) {
                luckPermsAPI = null;
                plugin.getLogger().warning("Không thể kết nối với LuckPerms! Kiểm tra plugin LuckPerms.");
            }
        } else {
            plugin.getLogger().warning("LuckPerms không được cài đặt trên server!");
        }
    }

    public boolean isStaff(Player player) {
        boolean isStaff = false;

        if (configManager.useLuckPerms && luckPermsAPI != null) {  // Dùng configManager.useLuckPerms
            User user = luckPermsAPI.getUserManager().getUser(player.getUniqueId());
            isStaff = user != null && user.getCachedData().getPermissionData().checkPermission("security.staff").asBoolean();
        }

        if (configManager.useStaffYml && !isStaff) {
            String playerName = player.getName();
            for (String rank : configManager.getStaffConfig().getKeys(false)) {
                if (configManager.getStaffConfig().getStringList(rank).contains(playerName)) {
                    isStaff = true;
                    break;
                }
            }
        }

        return isStaff;
    }

    public String getPlayerRank(Player player) {
        if (configManager.useLuckPerms && luckPermsAPI != null) {  // Dùng configManager.useLuckPerms
            User user = luckPermsAPI.getUserManager().getUser(player.getUniqueId());
            return user != null && user.getPrimaryGroup() != null ? user.getPrimaryGroup() : "Không có rank";
        }

        if (configManager.useStaffYml) {
            String playerName = player.getName();
            for (String rank : configManager.getStaffConfig().getKeys(false)) {
                if (configManager.getStaffConfig().getStringList(rank).contains(playerName)) {
                    return rank;
                }
            }
        }

        return "Không có rank";
    }
}