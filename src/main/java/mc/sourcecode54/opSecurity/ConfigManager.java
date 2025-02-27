package mc.sourcecode54.opSecurity;

import net.md_5.bungee.api.ChatColor;
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
    private final OpSecurity plugin;
    private File dataFile, staffFile, logFile, messagesFile;
    private FileConfiguration dataConfig, staffConfig, messagesConfig;
    public boolean useLuckPerms;
    public boolean useStaffYml;
    public int reminderInterval;
    public boolean enableLoginEffects;
    public boolean enableManualReset; // Bật/tắt reset thủ công
    public int maxPasswordLength; // Độ dài tối đa của mật khẩu
    private static final int MAX_MESSAGES = 50; // Giới hạn số tin nhắn trong messages.yml
    private static final long MAX_LOG_SIZE = 1024 * 1024; // 1MB giới hạn kích thước log

    public ConfigManager(OpSecurity plugin) {
        this.plugin = plugin;
        loadConfig();
        loadFiles();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        useLuckPerms = config.getBoolean("use-luckperms", true);
        useStaffYml = config.getBoolean("use-staff-yml", true);
        reminderInterval = config.getInt("login-reminder-interval", 10);
        enableLoginEffects = config.getBoolean("enable-login-effects", true);
        enableManualReset = config.getBoolean("enable-manual-reset", true); // Tính năng reset thủ công
        maxPasswordLength = config.getInt("max-password-length", 16); // Độ dài tối đa mật khẩu
    }

    private void loadFiles() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) createFile(dataFile, "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        staffFile = new File(plugin.getDataFolder(), "staff.yml");
        if (!staffFile.exists() && useStaffYml) createFile(staffFile, "staff.yml");
        staffConfig = YamlConfiguration.loadConfiguration(staffFile);

        logFile = new File(plugin.getDataFolder(), "security.log");
        if (!logFile.exists()) createFile(logFile, "security.log");
        optimizeLogFile(); // Tối ưu kích thước log
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) createFile(messagesFile, "messages.yml");
        optimizeMessagesFile(); // Tối ưu số tin nhắn
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void createFile(File file, String name) {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            plugin.getLogger().info("Đã tạo file " + name + " mới.");
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể tạo file " + name + ": " + e.getMessage());
        }
    }

    public void saveFiles() {
        try {
            if (dataConfig != null) dataConfig.save(dataFile);
            if (useStaffYml && staffConfig != null) staffConfig.save(staffFile);
            if (messagesConfig != null) {
                optimizeMessagesFile(); // Tối ưu trước khi lưu
                messagesConfig.save(messagesFile);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể lưu file: " + e.getMessage());
        }
    }

    private void optimizeLogFile() {
        try {
            if (logFile.length() > MAX_LOG_SIZE) {
                FileWriter writer = new FileWriter(logFile, false); // Ghi đè
                writer.write("Log đã được tối ưu, giữ lại dòng gần nhất.\n");
                writer.close();
                plugin.getLogger().info("Đã tối ưu file security.log do vượt quá 1MB.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể tối ưu file security.log: " + e.getMessage());
        }
    }

    private void optimizeMessagesFile() {
        if (messagesConfig.getConfigurationSection("messages") != null) {
            List<String> keys = new ArrayList<>(messagesConfig.getConfigurationSection("messages").getKeys(false));
            while (keys.size() > MAX_MESSAGES) {
                String oldestKey = keys.get(0); // Lấy key cũ nhất
                messagesConfig.set("messages." + oldestKey, null);
                keys.remove(0);
            }
        }
    }

    public FileConfiguration getDataConfig() { return dataConfig; }
    public FileConfiguration getStaffConfig() { return staffConfig; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }

    public void logSecurityEvent(String event) {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            writer.write("[" + timestamp + "] " + event + "\n");
            optimizeLogFile(); // Tối ưu log sau mỗi ghi
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể ghi log: " + e.getMessage());
        }
    }

    public void addStaffToYml(String playerName, String rank) {
        if (!useStaffYml) return;
        List<String> staffList = staffConfig.getStringList(rank);
        if (staffList == null) staffList = new ArrayList<>();
        if (!staffList.contains(playerName)) {
            if (isValidRank(rank)) {
                staffList.add(playerName);
                staffConfig.set(rank, staffList);
                saveFiles();
                plugin.getLogger().info("Đã thêm " + playerName + " vào " + rank + " trong staff.yml.");
            } else {
                plugin.getLogger().warning("Rank '" + rank + "' không hợp lệ trong staff.yml, sử dụng 'Default' thay thế.");
                staffList.add(playerName);
                staffConfig.set("Default", staffList);
                saveFiles();
                plugin.getLogger().info("Đã thêm " + playerName + " vào rank 'Default' trong staff.yml.");
            }
        }
    }

    public boolean isValidRank(String rank) {
        if (rank == null || rank.trim().isEmpty()) return false;
        return useStaffYml && staffConfig.getKeys(false).contains(rank);
    }

    public String getDefaultOrValidRank(String rank) {
        if (isValidRank(rank)) return rank;
        plugin.getLogger().warning("Rank '" + rank + "' không hợp lệ, sử dụng 'Default' thay thế.");
        return "Default";
    }

    public void addMessageToAdmin(String type, String sender, String content, String rank) {
        String validRank = getDefaultOrValidRank(rank);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String key = "messages." + timestamp;
        messagesConfig.set(key + ".type", type);
        messagesConfig.set(key + ".sender", sender);
        messagesConfig.set(key + ".content", content);
        messagesConfig.set(key + ".rank", validRank);
        saveFiles();
    }

    public List<String> getPendingMessages() {
        List<String> pending = new ArrayList<>();
        if (messagesConfig.getConfigurationSection("messages") != null) {
            for (String key : messagesConfig.getConfigurationSection("messages").getKeys(false)) {
                String type = messagesConfig.getString("messages." + key + ".type");
                String sender = messagesConfig.getString("messages." + key + ".sender");
                String content = messagesConfig.getString("messages." + key + ".content");
                String rank = messagesConfig.getString("messages." + key + ".rank", "Default");
                String msg = ChatColor.YELLOW + "[Tin " + type + " từ " + sender + " (Rank: " + rank + ") vào " + key + "]: " + ChatColor.WHITE + content;
                pending.add(msg);
            }
        }
        return pending;
    }

    public void clearPendingMessages() {
        messagesConfig.set("messages", null);
        saveFiles();
    }

    public boolean canResetPassword() {
        return enableManualReset;
    }

    public boolean isPasswordValid(String password) {
        return password != null && !password.trim().isEmpty() && password.length() <= maxPasswordLength;
    }
}