package mc.sourcecode54.opSecurity;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConfigManager {
    private final OpSecurity plugin; // Plugin chính để log và truy cập server
    private File dataFile, staffFile, logFile, messagesFile; // Các file dữ liệu
    private FileConfiguration dataConfig, staffConfig, messagesConfig; // Config cho từng file
    public boolean useLuckPerms; // Sử dụng LuckPerms để quản lý quyền
    public boolean useStaffYml; // Sử dụng staff.yml để quản lý staff
    public int reminderInterval; // Khoảng thời gian nhắc nhở đăng nhập (giây)
    public boolean enableLoginEffects; // Bật/tắt hiệu ứng khi đăng nhập

    public ConfigManager(OpSecurity plugin) { // Khởi tạo với plugin
        this.plugin = plugin;
        loadConfig(); // Tải cấu hình
        loadFiles(); // Tải các file
    }

    private void loadConfig() { // Tải config.yml, quan trọng cho các tùy chọn
        plugin.saveDefaultConfig(); // Tạo config.yml nếu chưa có
        FileConfiguration config = plugin.getConfig();
        useLuckPerms = config.getBoolean("use-luckperms", true); // Kiểm tra dùng LuckPerms
        useStaffYml = config.getBoolean("use-staff-yml", true); // Kiểm tra dùng staff.yml
        reminderInterval = config.getInt("login-reminder-interval", 10); // Thời gian nhắc nhở
        enableLoginEffects = config.getBoolean("enable-login-effects", true); // Bật/tắt hiệu ứng
    }

    private void loadFiles() { // Tải các file dữ liệu, quan trọng cho staff.yml và hộp thư
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) createFile(dataFile, "data.yml"); // Tạo file data.yml
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        staffFile = new File(plugin.getDataFolder(), "staff.yml");
        if (!staffFile.exists() && useStaffYml) createFile(staffFile, "staff.yml"); // Tạo file staff.yml nếu dùng
        staffConfig = YamlConfiguration.loadConfiguration(staffFile);

        logFile = new File(plugin.getDataFolder(), "security.log");
        if (!logFile.exists()) createFile(logFile, "security.log"); // Tạo file log
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) createFile(messagesFile, "messages.yml"); // Tạo file hộp thư
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void createFile(File file, String name) { // Phương thức phụ trợ để tạo file
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            plugin.getLogger().info("Đã tạo file " + name + " mới.");
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể tạo file " + name + ": " + e.getMessage());
        }
    }

    public void saveFiles() { // Lưu các file, quan trọng cho staff.yml và hộp thư
        try {
            if (dataConfig != null) dataConfig.save(dataFile);
            if (useStaffYml && staffConfig != null) staffConfig.save(staffFile);
            if (messagesConfig != null) messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể lưu file: " + e.getMessage());
        }
    }

    public FileConfiguration getDataConfig() { return dataConfig; } // Getter cho dataConfig
    public FileConfiguration getStaffConfig() { return staffConfig; } // Getter cho staffConfig, quan trọng cho quản lý staff
    public FileConfiguration getMessagesConfig() { return messagesConfig; } // Getter cho messagesConfig, quan trọng cho hộp thư

    public void logSecurityEvent(String event) { // Ghi log bảo mật
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            writer.write("[" + timestamp + "] " + event + "\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể ghi log: " + e.getMessage());
        }
    }

    public void addStaffToYml(String playerName, String rank) { // Thêm staff vào staff.yml, quan trọng cho tối ưu
        if (!useStaffYml) return;
        List<String> staffList = staffConfig.getStringList(rank);
        if (staffList == null) staffList = new ArrayList<>();
        if (!staffList.contains(playerName)) {
            staffList.add(playerName);
            staffConfig.set(rank, staffList);
            saveFiles();
            plugin.getLogger().info("Đã thêm " + playerName + " vào " + rank + " trong staff.yml.");
        }
    }

    public void addMessageToAdmin(String sender, String message) { // Lưu tin nhắn vào hộp thư, quan trọng cho liên hệ admin
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String key = "messages." + timestamp;
        messagesConfig.set(key + ".sender", sender);
        messagesConfig.set(key + ".message", message);
        saveFiles();
    }

    public List<String> getPendingMessages() { // Lấy danh sách tin nhắn chờ, quan trọng cho hộp thư
        List<String> pending = new ArrayList<>();
        if (messagesConfig.getConfigurationSection("messages") != null) {
            for (String key : messagesConfig.getConfigurationSection("messages").getKeys(false)) {
                String sender = messagesConfig.getString("messages." + key + ".sender");
                String msg = messagesConfig.getString("messages." + key + ".message");
                pending.add(ChatColor.YELLOW + "[Tin nhắn từ " + sender + " vào " + key + "]: " + ChatColor.WHITE + msg);
            }
        }
        return pending;
    }

    public void clearPendingMessages() { // Xóa tin nhắn sau khi gửi, quan trọng cho hộp thư
        messagesConfig.set("messages", null);
        saveFiles();
    }
}