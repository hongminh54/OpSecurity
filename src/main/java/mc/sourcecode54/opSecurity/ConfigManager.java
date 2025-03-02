package mc.sourcecode54.opSecurity;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class ConfigManager {
    private final OpSecurity plugin;
    private final OpSecCommand opSecCommand;  // Thêm tham chiếu đến OpSecCommand
    private File dataFile, staffFile, logFile, messagesFile;
    private FileConfiguration dataConfig, staffConfig, messagesConfig;
    public boolean useLuckPerms, useStaffYml, enableLoginGUI, enableManualReset;
    public int reminderInterval, maxPasswordLength;
    public String databaseType, mysqlHost, mysqlDatabase, mysqlUsername, mysqlPassword, sqliteFile, discordLink, facebookLink;
    public int mysqlPort;
    private Connection dbConnection;
    private List<String> validRanks;  // Danh sách rank hợp lệ từ config.yml

    public ConfigManager(OpSecurity plugin, OpSecCommand opSecCommand) {
        this.plugin = plugin;
        this.opSecCommand = opSecCommand;  // Nhận OpSecCommand từ constructor
        loadConfig();
        loadFiles();
        initializeDatabase();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        useLuckPerms = config.getBoolean("use-luckperms", true);
        useStaffYml = config.getBoolean("use-staff-yml", true);
        reminderInterval = config.getInt("login-reminder-interval", 10);
        enableLoginGUI = config.getBoolean("enable-login-gui", true);
        enableManualReset = config.getBoolean("enable-manual-reset", true);
        maxPasswordLength = config.getInt("max-password-length", 16);
        databaseType = config.getString("database-type", "yml").toLowerCase();
        mysqlHost = config.getString("mysql-host", "localhost");
        mysqlPort = config.getInt("mysql-port", 3306);
        mysqlDatabase = config.getString("mysql-database", "opsecurity");
        mysqlUsername = config.getString("mysql-username", "root");
        mysqlPassword = config.getString("mysql-password", "password");
        sqliteFile = config.getString("sqlite-file", "plugins/OpSecurity/database.db");
        discordLink = config.getString("discord-link", "https://discord.gg/your-discord-invite");
        facebookLink = config.getString("facebook-link", "https://www.facebook.com/your-admin-page");
        validRanks = config.getStringList("valid-ranks");  // Load danh sách rank hợp lệ
        if (validRanks == null || validRanks.isEmpty()) {
            validRanks = List.of("Default", "Staff", "Admin", "Owner");  // Default nếu không cấu hình
            config.set("valid-ranks", validRanks);
            try {
                config.save(plugin.getConfig().getCurrentPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Lỗi lưu valid-ranks vào config.yml: " + e.getMessage());
            }
        }
    }

    public void loadFiles() {
        try {
            if ("yml".equals(databaseType)) {
                dataFile = ensureFile("data.yml");
                dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            }
            if (useStaffYml) {
                staffFile = ensureFile("staff.yml");
                staffConfig = YamlConfiguration.loadConfiguration(staffFile);
            }
            logFile = ensureFile("security.log");
            messagesFile = ensureFile("messages.yml");
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            if (messagesConfig.getKeys(false).isEmpty()) {
                // Nếu messages.yml rỗng, tạo mặc định với tin nhắn tối ưu
                messagesConfig.set("prefix", "&c[OpSecurity] ");
                messagesConfig.set("no-permission", "&c❌ Bạn không đủ quyền! Liên hệ admin nhé 😕");
                messagesConfig.set("invalid-usage", "&c❓ Sử dụng: /opsec [sub] [args] hoặc /os [sub] [args]");
                messagesConfig.set("console-only-commands", "&c🛠 Chỉ console/owner dùng: reload, check, addstaff, removestaff, update, reset!");
                messagesConfig.set("already-registered", "&c🔒 Bạn đã đăng ký, dùng /opsec login hoặc /os login!");
                messagesConfig.set("only-staff-register", "&c🚫 Chỉ staff mới được đăng ký!");
                messagesConfig.set("password-too-long", "&c🔐 Mật khẩu quá dài (≤ {maxLength} ký tự) 😞");
                messagesConfig.set("register-success", "&a🎉 Đăng ký thành công! Rank: &b{rank} 🚀 Chào mừng bạn!");
                messagesConfig.set("register-log", "{player} đã đăng ký với rank {rank}.");
                messagesConfig.set("register-usage", "&c📝 Dùng: /opsec register <mật khẩu> hoặc /os register <mật khẩu>");
                messagesConfig.set("only-staff-login", "&c🚫 Chỉ staff mới được đăng nhập!");
                messagesConfig.set("not-registered", "&c🔓 Chưa đăng ký! Dùng /opsec register <mật khẩu> hoặc /os register <mật khẩu> 😕");
                messagesConfig.set("already-logged-in", "&c✅ Đã đăng nhập, không cần lại!");
                messagesConfig.set("login-usage", "&c🔑 Dùng: /opsec login <mật khẩu> hoặc /os login <mật khẩu>");
                messagesConfig.set("login-success", "&a🚀 Đăng nhập thành công! Rank: &b{rank} 🎉");
                messagesConfig.set("login-failure", "&c❌ Mật khẩu sai! Thử lại hoặc liên hệ admin 😞");
                messagesConfig.set("login-log-failure", "{player} đăng nhập thất bại.");
                messagesConfig.set("login-chat-prompt", "&e📧 Nhập mật khẩu vào chat: {method} 🖋️");
                messagesConfig.set("login-close-prompt", "&e🔐 Dùng chat hoặc /opsec login để đăng nhập! ⏰");
                messagesConfig.set("login-gui-success-log", "{player} đăng nhập qua GUI với rank {rank}.");
                messagesConfig.set("login-cli-prompt", "&e🔑 Dùng /opsec login <mật khẩu> hoặc /os login <mật khẩu> (GUI tắt). ⏳");
                messagesConfig.set("login-reminder", "&e⏰ Nhắc nhở: Đăng nhập qua GUI hoặc /opsec login!");
                messagesConfig.set("login-required-command", "&c🚫 Đăng nhập trước khi dùng lệnh! 🔒");
                messagesConfig.set("login-kick-command", "&c🔒 Dùng GUI hoặc /opsec login để đăng nhập!");
                messagesConfig.set("command-blocked-log", "{player} bị chặn lệnh '{command}' do chưa đăng nhập.");
                messagesConfig.set("login-required-interact", "&c🚫 Đăng nhập trước khi tương tác! 🔒");
                messagesConfig.set("login-kick-interact", "&c🔒 Dùng GUI hoặc /opsec login để đăng nhập!");
                messagesConfig.set("interact-blocked-log", "{player} bị chặn tương tác '{action}' do chưa đăng nhập.");
                messagesConfig.set("forgot-not-needed", "&c❓ Không cần đăng nhập!");
                messagesConfig.set("contactadmin-usage", "&c📩 Dùng: /opsec contactadmin <tin nhắn> hoặc /os contactadmin <tin nhắn>");
                messagesConfig.set("check-usage", "&c🔍 Dùng: /opsec check <player> hoặc /os check <player>");
                messagesConfig.set("check-offline", "&c😞 Player offline!");
                messagesConfig.set("check-result", "&a✅ {player} có rank: &b{rank}");
                messagesConfig.set("check-log", "{sender} kiểm tra rank {player} là {rank}.");
                messagesConfig.set("addstaff-usage", "&c➕ Dùng: /addstaff <rank> <player> hoặc /opsec addstaff <rank> <player>");
                messagesConfig.set("addstaff-offline", "&c😞 Player '{player}' không online và chưa từng chơi!");
                messagesConfig.set("addstaff-invalid-rank", "&c🛑 Rank '{rank}' không hợp lệ!");
                messagesConfig.set("addstaff-success", "&a🎉 Thêm {player} vào rank &b{rank}!");
                messagesConfig.set("addstaff-log", "{sender} thêm {player} vào rank {rank}.");
                messagesConfig.set("removestaff-usage", "&c➖ Dùng: /removestaff <rank> <player> hoặc /opsec removestaff <rank> <player>");
                messagesConfig.set("removestaff-offline", "&c😞 Player '{player}' không online và chưa từng chơi!");
                messagesConfig.set("removestaff-not-in-rank", "&c🛑 {player} không trong rank '{rank}'!");
                messagesConfig.set("removestaff-success", "&a✅ Xóa {player} khỏi rank &b{rank}!");
                messagesConfig.set("removestaff-log", "{sender} xóa {player} khỏi rank {rank}.");
                messagesConfig.set("reset-usage", "&c🔄 Dùng: /opsec reset <player> <password> hoặc /os reset <player> <password>");
                messagesConfig.set("reset-disabled", "&c🛑 Reset thủ công bị tắt!");
                messagesConfig.set("reset-invalid-password", "&c😞 Mật khẩu không hợp lệ (≤ {maxLength} ký tự)!");
                messagesConfig.set("reset-offline", "&c😞 Player '{player}' không online!");
                messagesConfig.set("reset-not-staff", "&c🚫 {player} không phải staff hoặc chưa đăng ký!");
                messagesConfig.set("reset-success", "&a✅ Reset mật khẩu cho {player} thành công!");
                messagesConfig.set("reset-notify", "&e🔔 Phải đợi admin xử lý hoặc liên hệ trực tiếp.");
                messagesConfig.set("reset-log", "{sender} reset mật khẩu {player} với rank {rank}.");
                messagesConfig.set("update-checking", "&e⏳ Kiểm tra và cập nhật...");
                messagesConfig.set("reload-success", "&a🎉 Tải lại dữ liệu thành công!");
                messagesConfig.set("reload-failure", "&c😞 Lỗi tải lại: {error}");
                messagesConfig.set("reload-log", "{sender} tải lại plugin.");
                messagesConfig.set("reload-player-denied", "&c🚫 Chỉ console/owner mới dùng lệnh này!");
                messagesConfig.set("gui-open", "&e🖥️ Mở GUI đăng nhập!");
                messagesConfig.set("contact-discord", "&e📧 Liên kết Discord: {discordLink}");
                messagesConfig.set("contact-facebook", "&e📧 Liên kết Facebook: {facebookLink}");
                messagesConfig.set("contact-sent-offline", "&e🔔 Tin nhắn đã gửi qua link nếu không admin online.");
                messagesConfig.set("luckperms-disabled", "&c🚫 LuckPerms không hoạt động! Vui lòng cài đặt plugin LuckPerms.");
                messagesConfig.set("changepassword-usage", "&c🔐 Dùng: /opsec changepassword <mật khẩu cũ> <mật khẩu mới>");
                messagesConfig.set("changepassword-success", "&a✅ {player} đã đổi mật khẩu thành công! 🔒");
                messagesConfig.set("changepassword-failure", "&c❌ Đổi mật khẩu thất bại: {reason} 😞");
                messagesConfig.set("changepassword-log", "{player} đổi mật khẩu với rank {rank}.");
                saveMessages();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi load messages.yml: " + e.getMessage());
        }
    }

    public void saveMessages() {
        try {
            if (messagesConfig != null) messagesConfig.save(messagesFile);
        } catch (IOException e) { plugin.getLogger().warning("Lỗi lưu messages.yml: " + e.getMessage()); }
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key, "&cLỗi: Tin nhắn không tồn tại!");
        message = ChatColor.translateAlternateColorCodes('&', message);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return message;
    }

    private File ensureFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { plugin.getLogger().warning("Không tạo được " + name + ": " + e.getMessage()); }
        }
        return file;
    }

    public void initializeDatabase() {
        if ("yml".equals(databaseType)) return;

        try {
            if ("mysql".equals(databaseType)) {
                String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + "?useSSL=false";
                Properties props = new Properties();
                props.setProperty("user", mysqlUsername);
                props.setProperty("password", mysqlPassword);
                dbConnection = DriverManager.getConnection(url, props);
                createTables();
            } else if ("sqlite".equals(databaseType)) {
                String url = "jdbc:sqlite:" + sqliteFile;
                dbConnection = DriverManager.getConnection(url);
                createTables();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
            dbConnection = null; // Fallback to YML nếu lỗi
            databaseType = "yml";
            loadFiles(); // Load YML nếu không kết nối được
        }
    }

    private void createTables() throws SQLException {
        if (dbConnection == null) return;
        String sql = """
            CREATE TABLE IF NOT EXISTS staff (
                uuid VARCHAR(36) PRIMARY KEY,
                password VARCHAR(255) NOT NULL,
                rank VARCHAR(50) DEFAULT 'Default',
                last_reset TIMESTAMP
            );
        """;
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void saveFiles() {
        try {
            if ("yml".equals(databaseType) && dataConfig != null) dataConfig.save(dataFile);
            if (useStaffYml && staffConfig != null) staffConfig.save(staffFile);
        } catch (IOException e) { plugin.getLogger().warning("Lỗi lưu file: " + e.getMessage()); }
    }

    public FileConfiguration getDataConfig() {
        if ("yml".equals(databaseType)) return dataConfig;
        return null;
    }

    public FileConfiguration getStaffConfig() {
        if (useStaffYml) return staffConfig;
        return null;
    }

    public void logSecurityEvent(String event) {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " + event + "\n");
            if (logFile.length() > 1024 * 1024) { // 1MB
                try (FileWriter clear = new FileWriter(logFile, false)) { clear.write("Log đã được tối ưu.\n"); }
                catch (IOException ioe) { plugin.getLogger().warning("Lỗi tối ưu log: " + ioe.getMessage()); }
            }
        } catch (IOException e) { plugin.getLogger().warning("Lỗi ghi log: " + e.getMessage()); }
    }

    public void addStaffToYml(String playerName, String rank) {
        if (!useStaffYml || staffConfig == null) return;
        List<String> staff = staffConfig.getStringList(rank);
        if (staff == null) staff = new ArrayList<>();
        if (!staff.contains(playerName)) {
            staff.add(playerName);
            staffConfig.set(rank, staff);
            saveFiles();
        }
    }

    public boolean isValidRank(String rank) {
        if (useLuckPerms && opSecCommand != null && opSecCommand.getLuckPerms() != null) {
            // Kiểm tra rank từ LuckPerms
            return opSecCommand.getLuckPerms().getGroupManager().getLoadedGroups().stream()
                    .anyMatch(group -> group.getName().equalsIgnoreCase(rank)) || validRanks.stream()
                    .anyMatch(valid -> valid.equalsIgnoreCase(rank));
        }
        if (useStaffYml && staffConfig != null) {
            // Kiểm tra rank từ staff.yml
            return staffConfig.contains(rank) || validRanks.stream()
                    .anyMatch(valid -> valid.equalsIgnoreCase(rank));
        }
        return validRanks.stream().anyMatch(valid -> valid.equalsIgnoreCase(rank));  // Fallback về valid-ranks
    }

    public String getDefaultOrValidRank(String rank) {
        if (isValidRank(rank)) return rank;
        // Nếu rank không hợp lệ, trả về rank từ LuckPerms hoặc Default
        if (useLuckPerms && opSecCommand != null && opSecCommand.getLuckPerms() != null && opSecCommand.perms != null) {
            Player player = Bukkit.getPlayer(rank);  // Giả định rank là tên player
            if (player != null) {
                return opSecCommand.perms.getPlayerRank(player);  // Lấy rank từ LuckPerms
            }
        }
        return validRanks.contains("Default") ? "Default" : validRanks.get(0);  // Fallback về Default hoặc rank đầu tiên
    }

    public boolean canResetPassword() { return enableManualReset; }
    public boolean isPasswordValid(String password) { return password != null && !password.trim().isEmpty() && password.length() <= maxPasswordLength; }

    public boolean isRegistered(String uuid) {
        if ("yml".equals(databaseType)) {
            return getDataConfig().contains(uuid + ".password");
        } else if (dbConnection != null) {
            try (PreparedStatement stmt = dbConnection.prepareStatement("SELECT uuid FROM staff WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi kiểm tra đăng ký trong DB: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean isRegistered(OfflinePlayer offlinePlayer) {
        return isRegistered(offlinePlayer.getUniqueId().toString());
    }

    public String getPassword(String uuid) {
        if ("yml".equals(databaseType)) {
            return getDataConfig().getString(uuid + ".password");
        } else if (dbConnection != null) {
            try (PreparedStatement stmt = dbConnection.prepareStatement("SELECT password FROM staff WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getString("password") : null;
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi lấy mật khẩu từ DB: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    public void setPassword(String uuid, String password, String rank, Date lastReset) {
        if ("yml".equals(databaseType)) {
            FileConfiguration data = getDataConfig();
            data.set(uuid + ".password", password);
            data.set(uuid + ".rank", rank);  // Gán rank từ valid-ranks hoặc LuckPerms
            if (lastReset != null) {
                data.set(uuid + ".last-reset", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastReset));
            }
            try {
                data.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Lỗi lưu mật khẩu vào YML: " + e.getMessage());
            }
        } else if (dbConnection != null) {
            try {
                String sql = "INSERT INTO staff (uuid, password, rank, last_reset) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE password = ?, rank = ?, last_reset = ?";
                try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
                    stmt.setString(1, uuid);
                    stmt.setString(2, password);
                    stmt.setString(3, rank);
                    stmt.setTimestamp(4, lastReset != null ? new Timestamp(lastReset.getTime()) : null);
                    stmt.setString(5, password);
                    stmt.setString(6, rank);
                    stmt.setTimestamp(7, lastReset != null ? new Timestamp(lastReset.getTime()) : null);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi lưu mật khẩu vào DB: " + e.getMessage());
            }
        }
    }

    public String getRank(String uuid) {
        if ("yml".equals(databaseType)) {
            return getDataConfig().getString(uuid + ".rank", "Default");
        } else if (dbConnection != null) {
            try (PreparedStatement stmt = dbConnection.prepareStatement("SELECT rank FROM staff WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getString("rank") : "Default";
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi lấy rank từ DB: " + e.getMessage());
                return "Default";
            }
        }
        return "Default";
    }

    public Date getLastReset(String uuid) {
        if ("yml".equals(databaseType)) {
            String timestamp = getDataConfig().getString(uuid + ".last-reset");
            if (timestamp != null) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(timestamp);
                } catch (Exception e) {
                    plugin.getLogger().warning("Lỗi parse last-reset từ YML: " + e.getMessage());
                }
            }
            return null;
        } else if (dbConnection != null) {
            try (PreparedStatement stmt = dbConnection.prepareStatement("SELECT last_reset FROM staff WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getTimestamp("last_reset") : null;
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi lấy last-reset từ DB: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    public void closeConnection() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Lỗi đóng kết nối DB: " + e.getMessage());
            }
        }
    }

    public List<String> getValidRanks() {
        return validRanks;
    }
}