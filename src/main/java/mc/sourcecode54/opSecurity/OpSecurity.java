package mc.sourcecode54.opSecurity;

import org.bukkit.plugin.java.JavaPlugin;

public class OpSecurity extends JavaPlugin {
    private ConfigManager configManager;
    private PermissionHandler permissionHandler;
    private LoginManager loginManager;

    @Override
    public void onEnable() {
        try {
            getLogger().info("==========================================");
            getLogger().info("OpSecurity v1.0 đang khởi động...");
            getLogger().info("Tác giả: Hong Minh");
            getLogger().info("Hỗ trợ Minecraft 1.8 - 1.21.4");
            getLogger().info("==========================================");

            configManager = new ConfigManager(this);
            permissionHandler = new PermissionHandler(this, configManager);
            loginManager = new LoginManager(this, configManager, permissionHandler);

            getServer().getPluginManager().registerEvents(new EventListener(this, configManager, permissionHandler, loginManager), this);
            getServer().getPluginManager().registerEvents(loginManager, this);
            OpSecCommand opSecCommand = new OpSecCommand(this, configManager, permissionHandler, new AutoUpdater(this), loginManager);
            getCommand("opsec").setExecutor(opSecCommand);
            getCommand("opsec").setTabCompleter(opSecCommand);

            new AutoUpdater(this).checkForUpdates();

            getLogger().info("OpSecurity đã được kích hoạt thành công!");
        } catch (Exception e) {
            getLogger().severe("Lỗi khi kích hoạt OpSecurity: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.saveFiles();
            getLogger().info("OpSecurity đã được tắt và lưu dữ liệu thành công.");
        } else {
            getLogger().warning("ConfigManager là null, không thể lưu dữ liệu.");
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}