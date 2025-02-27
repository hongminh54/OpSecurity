package mc.sourcecode54.opSecurity;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.CommandSender;

public class AutoUpdater {
    private final JavaPlugin plugin;
    private static final String GITHUB_API_URL = "https://api.github.com/repos/hongminh54/OpSecurity/releases/latest";
    private static final String CURRENT_VERSION = "1.1";
    private boolean hasChecked = false;

    public AutoUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        if (hasChecked) {
            plugin.getLogger().info("Đã kiểm tra cập nhật trước đó. Bỏ qua kiểm tra thêm.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (isCurrentVersionLatest()) {
                        plugin.getLogger().info("Plugin đang chạy phiên bản mới nhất: " + CURRENT_VERSION);
                        hasChecked = true;
                        return;
                    }

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(GITHUB_API_URL))
                            .header("Accept", "application/vnd.github.v3+json")
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String jsonResponse = response.body();

                    JSONParser parser = new JSONParser();
                    JSONObject json = (JSONObject) parser.parse(jsonResponse);
                    String latestVersion = (String) json.get("tag_name");
                    if (latestVersion != null) {
                        latestVersion = latestVersion.replace("v", "");

                        if (!CURRENT_VERSION.equals(latestVersion)) {
                            plugin.getLogger().info("Phiên bản mới " + latestVersion + " đã được tìm thấy! Đang cập nhật trực tiếp...");
                            JSONArray assets = (JSONArray) json.get("assets");
                            if (assets != null && !assets.isEmpty()) {
                                JSONObject asset = (JSONObject) assets.get(0);
                                String downloadUrl = (String) asset.get("browser_download_url");
                                downloadAndUpdateDirectly(downloadUrl);
                            } else {
                                plugin.getLogger().warning("Không tìm thấy assets trong release.");
                            }
                        } else {
                            plugin.getLogger().info("Plugin đang chạy phiên bản mới nhất: " + CURRENT_VERSION);
                        }
                    } else {
                        plugin.getLogger().warning("Không thể xác định phiên bản mới từ GitHub.");
                    }
                    hasChecked = true;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Không thể kiểm tra cập nhật từ GitHub: " + e.getMessage());
                    hasChecked = true;
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private boolean isCurrentVersionLatest() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonResponse = response.body();

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonResponse);
            String latestVersion = (String) json.get("tag_name");
            if (latestVersion != null) {
                latestVersion = latestVersion.replace("v", "");
                return CURRENT_VERSION.equals(latestVersion);
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Không thể kiểm tra phiên bản mới nhất từ GitHub: " + e.getMessage());
            return false;
        }
    }

    private void downloadAndUpdateDirectly(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            File pluginFolder = plugin.getDataFolder().getParentFile();
            File currentJar = new File(pluginFolder, "OpSecurity.jar");
            File backupJar = new File(pluginFolder, "OpSecurity_backup.jar");

            if (currentJar.exists()) {
                if (backupJar.exists()) backupJar.delete();
                currentJar.renameTo(backupJar);
                plugin.getLogger().info("Đã sao lưu phiên bản cũ thành OpSecurity_backup.jar.");
            }

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (ReadableByteChannel rbc = Channels.newChannel(response.body());
                 FileOutputStream fos = new FileOutputStream(currentJar)) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }

            plugin.getLogger().info("Đã cập nhật OpSecurity trực tiếp trong thư mục plugins/. Vui lòng khởi động lại server để áp dụng!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Không thể cập nhật trực tiếp: " + e.getMessage());
            File pluginFolder = plugin.getDataFolder().getParentFile();
            File currentJar = new File(pluginFolder, "OpSecurity.jar");
            File backupJar = new File(pluginFolder, "OpSecurity_backup.jar");
            if (backupJar.exists() && !currentJar.exists()) {
                backupJar.renameTo(currentJar);
                plugin.getLogger().info("Đã khôi phục phiên bản cũ từ backup.");
            }
        }
    }

    public void manualCheckForUpdates(CommandSender sender) { // Đảm bảo phương thức này là public
        hasChecked = false; // Reset trạng thái để kiểm tra lại
        checkForUpdates();
        sender.sendMessage("§eĐang kiểm tra và cập nhật phiên bản mới...");
    }
}