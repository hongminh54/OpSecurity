package mc.sourcecode54.opSecurity;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.net.URI;

public class AutoUpdater {
    private final JavaPlugin plugin;
    private static final String API_URL = "https://api.github.com/repos/hongminh54/OpSecurity/releases/latest";
    private static final String VERSION = "1.1";
    private boolean checked = false;

    public AutoUpdater(JavaPlugin plugin) { this.plugin = plugin; }

    public void checkForUpdates() {
        if (checked) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (isLatestVersion()) {
                    plugin.getLogger().info("Plugin đang dùng phiên bản mới nhất: " + VERSION);
                    checked = true;
                    return;
                }
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).header("Accept", "application/vnd.github.v3+json").build();
                String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                JSONObject data = (JSONObject) new JSONParser().parse(json);
                String latest = (String) data.get("tag_name");
                if (latest != null && !VERSION.equals(latest.replace("v", ""))) {
                    plugin.getLogger().info("Phiên bản mới " + latest + " tìm thấy. Cập nhật trực tiếp...");
                    downloadUpdate((String) ((JSONObject) data.get("assets")).get("browser_download_url"));
                }
                checked = true;
            } catch (Exception e) { plugin.getLogger().warning("Không kiểm tra cập nhật: " + e.getMessage()); checked = true; }
        });
    }

    private boolean isLatestVersion() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).header("Accept", "application/vnd.github.v3+json").build();
            String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            String latest = (String) ((JSONObject) new JSONParser().parse(json)).get("tag_name");
            return VERSION.equals(latest.replace("v", ""));
        } catch (Exception e) { plugin.getLogger().warning("Không kiểm tra phiên bản: " + e.getMessage()); return true; }
    }

    private void downloadUpdate(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            File jar = new File(plugin.getDataFolder().getParent(), "OpSecurity.jar");
            File backup = new File(plugin.getDataFolder().getParent(), "OpSecurity_backup.jar");
            if (jar.exists() && backup.exists()) backup.delete();
            if (jar.exists()) jar.renameTo(backup);
            try (ReadableByteChannel rbc = Channels.newChannel(client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body());
                 FileOutputStream fos = new FileOutputStream(jar)) { fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE); }
            plugin.getLogger().info("Cập nhật thành công. Khởi động lại server!");
        } catch (Exception e) { plugin.getLogger().warning("Lỗi cập nhật: " + e.getMessage()); restoreBackup(); }
    }

    private void restoreBackup() {
        File jar = new File(plugin.getDataFolder().getParent(), "OpSecurity.jar");
        File backup = new File(plugin.getDataFolder().getParent(), "OpSecurity_backup.jar");
        if (backup.exists() && !jar.exists()) backup.renameTo(jar);
    }

    public void manualCheckForUpdates(CommandSender sender) {
        checked = false;
        checkForUpdates();
        sender.sendMessage("§eKiểm tra và cập nhật phiên bản...");
    }
}