package mc.sourcecode54.opSecurity;

import mc.sourcecode54.opSecurity.command.ConsoleCommandHandler;
import mc.sourcecode54.opSecurity.command.PlayerCommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class OpSecurity extends JavaPlugin {
    private ConfigManager config;
    private PermissionHandler perms;
    private LoginManager loginMgr;

    @Override
    public void onEnable() {
        try {
            OpSecCommand command = new OpSecCommand(this, null, null, new AutoUpdater(this), null); // Tạm thời truyền null cho các tham số khác
            config = new ConfigManager(this, command);  // Truyền OpSecCommand hợp lệ ngay từ đầu
            perms = new PermissionHandler(this, config);
            loginMgr = new LoginManager(this, config, perms);

            Bukkit.getConsoleSender().sendMessage("§eOpSecurity v1.1 by SourceCode54");
            Bukkit.getConsoleSender().sendMessage("§aHỗ trợ phiên bản: 1.8.x - 1.21.x");
            Bukkit.getConsoleSender().sendMessage("§aHỗ trợ máy chủ: Spigot, Paper,....");

            command = new OpSecCommand(this, config, perms, new AutoUpdater(this), loginMgr);

            getServer().getPluginManager().registerEvents(new EventListener(this, config, perms, loginMgr), this);
            getServer().getPluginManager().registerEvents(loginMgr, this);
            getCommand("opsec").setExecutor(command);
            getCommand("opsec").setTabCompleter(command);
            new AutoUpdater(this).checkForUpdates();
            getLogger().info("OpSecurity v1.1 đã được kích hoạt!");
        } catch (Exception e) {
            getLogger().severe("Lỗi khi kích hoạt OpSecurity: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (config != null) {
            config.saveFiles();
            config.closeConnection();
        }
    }
}