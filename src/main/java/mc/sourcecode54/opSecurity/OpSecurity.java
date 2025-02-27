package mc.sourcecode54.opSecurity;

import org.bukkit.plugin.java.JavaPlugin;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;

import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class OpSecurity extends JavaPlugin { // Class chính của plugin, quản lý khởi động và tắt
    private ConfigManager configManager; // Quản lý các file config và dữ liệu
    private PermissionHandler permissionHandler; // Xử lý quyền (LuckPerms hoặc staff.yml)
    private LoginManager loginManager; // Quản lý đăng nhập, GUI, và hiệu ứng
    private static final String GITHUB_API_URL = "https://api.github.com/repos/hongminh54/OpSecurity/releases/latest"; // URL API GitHub, thay bằng repo của bạn
    private static final String CURRENT_VERSION = "1.0"; // Phiên bản hiện tại, cập nhật khi phát hành

    @Override
    public void onEnable() { // Khởi động plugin
        try {
            // Thông báo khởi động trong console (quan trọng)
            getLogger().info("==========================================");
            getLogger().info("OpSecurity v" + CURRENT_VERSION + " đang khởi động...");
            getLogger().info("Tác giả: hongminh54");
            getLogger().info("Hỗ trợ Minecraft 1.8 - 1.21.4");
            getLogger().info("==========================================");

            configManager = new ConfigManager(this); // Khởi tạo ConfigManager
            permissionHandler = new PermissionHandler(this, configManager); // Khởi tạo PermissionHandler
            loginManager = new LoginManager(this, configManager, permissionHandler); // Khởi tạo LoginManager

            getServer().getPluginManager().registerEvents(new EventListener(this, configManager, permissionHandler, loginManager), this); // Đăng ký listener cho sự kiện
            getServer().getPluginManager().registerEvents(loginManager, this); // Đăng ký LoginManager làm listener
            getCommand("opsec").setExecutor(loginManager); // Gán lệnh /opsec
            getCommand("opsec").setTabCompleter(loginManager); // Gán tab complete cho /opsec

            // Kiểm tra cập nhật từ GitHub (quan trọng)
            checkForUpdates();

        } catch (Exception e) { // Xử lý lỗi khởi động
            getLogger().severe("Lỗi khi kích hoạt OpSecurity: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() { // Tắt plugin
        if (configManager != null) configManager.saveFiles(); // Lưu dữ liệu nếu configManager tồn tại
    }

    // Kiểm tra và tải cập nhật từ GitHub (quan trọng)
    private void checkForUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    HttpClient client = HttpClient.newHttpClient(); // Tạo client HTTP
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(GITHUB_API_URL))
                            .header("Accept", "application/vnd.github.v3+json")
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    JSONParser parser = new JSONParser(); // Parse JSON
                    JSONObject json = (JSONObject) parser.parse(response.body());
                    String latestVersion = (String) json.get("tag_name");
                    if (latestVersion != null) {
                        latestVersion = latestVersion.replace("v", "");

                        if (!CURRENT_VERSION.equals(latestVersion)) {
                            getLogger().info("Phiên bản mới " + latestVersion + " đã được tìm thấy! Đang tải xuống...");
                            JSONArray assets = (JSONArray) json.get("assets");
                            if (assets != null && !assets.isEmpty()) {
                                JSONObject asset = (JSONObject) assets.get(0);
                                String downloadUrl = (String) asset.get("browser_download_url");
                                downloadUpdate(downloadUrl);
                            }
                        } else {
                            getLogger().info("Plugin đang chạy phiên bản mới nhất: " + CURRENT_VERSION);
                        }
                    }
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Không thể kiểm tra cập nhật từ GitHub: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(this);
    }

    // Tải file cập nhật từ GitHub (quan trọng)
    private void downloadUpdate(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            File updateFolder = new File(getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists()) updateFolder.mkdirs();
            File newJar = new File(updateFolder, "OpSecurity.jar");

            ReadableByteChannel rbc = Channels.newChannel(client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body());
            FileOutputStream fos = new FileOutputStream(newJar);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();

            getLogger().info("Đã tải phiên bản mới vào thư mục 'plugins/update'. Khởi động lại server để áp dụng!");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Không thể tải phiên bản mới: " + e.getMessage());
        }
    }
}