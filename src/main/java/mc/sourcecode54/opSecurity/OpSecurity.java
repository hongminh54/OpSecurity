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
            getLogger().info("OpSecurity v1.1 đang khởi động..."); // Cập nhật CURRENT_VERSION nếu cần
            getLogger().info("Tác giả: hongminh54");
            getLogger().info("Hỗ trợ Minecraft 1.8 - 1.21.4");
            getLogger().info("==========================================");

            configManager = new ConfigManager(this);
            permissionHandler = new PermissionHandler(this, configManager);
            loginManager = new LoginManager(this, configManager, permissionHandler);

            getServer().getPluginManager().registerEvents(new EventListener(this, configManager, permissionHandler, loginManager), this);
            getServer().getPluginManager().registerEvents(loginManager, this);
            getCommand("opsec").setExecutor(loginManager);
            getCommand("opsec").setTabCompleter(loginManager);

            new AutoUpdater(this).checkForUpdates();

            getLogger().info("OpSecurity đã được bật!");
        } catch (Exception e) {
            getLogger().severe("Lỗi khi bật OpSecurity: " + e.getMessage());
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
}