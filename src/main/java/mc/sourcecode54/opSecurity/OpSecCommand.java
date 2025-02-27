package mc.sourcecode54.opSecurity;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class OpSecCommand implements CommandExecutor, TabCompleter {
    private final OpSecurity plugin;
    private final ConfigManager configManager;
    private final PermissionHandler permissionHandler;
    private final AutoUpdater autoUpdater;
    private final LoginManager loginManager;
    private final Map<UUID, Boolean> authenticated = new HashMap<>();
    private final Map<UUID, Inventory> loginGUIs = new HashMap<>();
    private final Map<UUID, StringBuilder> passwordInputs = new HashMap<>();
    public static final List<String> PLUGIN_COMMANDS = Arrays.asList("opsec", "addstaff", "removestaff");
    private static final List<String> SUB_COMMANDS = Arrays.asList("register", "login", "forgot", "check", "contactadmin", "reset", "update", "reload");

    public OpSecCommand(OpSecurity plugin, ConfigManager configManager, PermissionHandler permissionHandler, AutoUpdater autoUpdater, LoginManager loginManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.permissionHandler = permissionHandler;
        this.autoUpdater = autoUpdater;
        this.loginManager = loginManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cChỉ người chơi mới dùng được lệnh này!");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage("§cSử dụng: /opsec [register|login|forgot|check|contactadmin|reset|update|reload] [args]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (!player.hasPermission("opsecurity." + subCommand) && !isAuthenticated(player)) {
            player.sendMessage("§cBạn không có quyền dùng lệnh này hoặc chưa đăng nhập!");
            return true;
        }

        String targetName = null;
        Player target = null;

        switch (subCommand) {
            case "register":
                if (!permissionHandler.isStaff(player)) {
                    player.sendMessage("§cBạn không phải staff!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cSử dụng: /opsec register <mật khẩu>");
                    return true;
                }
                String password = args[1];
                if (password.length() > configManager.maxPasswordLength) {
                    player.sendMessage("§cMật khẩu không được vượt quá " + configManager.maxPasswordLength + " ký tự!");
                    return true;
                }
                configManager.getDataConfig().set(player.getUniqueId().toString() + ".password", password);
                String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                configManager.addStaffToYml(player.getName(), rank);
                configManager.saveFiles();
                player.sendMessage("§aĐăng ký thành công với rank " + rank + "!");
                authenticated.put(player.getUniqueId(), true);
                configManager.logSecurityEvent(player.getName() + " đã đăng ký thành công với rank " + rank + ".");
                loginManager.playLoginEffects(player);
                return true;

            case "login":
                if (!permissionHandler.isStaff(player) || !loginManager.isRegistered(player)) {
                    player.sendMessage("§cBạn không cần đăng nhập!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cSử dụng: /opsec login <mật khẩu>");
                    return true;
                }
                String storedPassword = configManager.getDataConfig().getString(player.getUniqueId().toString() + ".password");
                if (args[1].equals(storedPassword)) {
                    rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                    authenticated.put(player.getUniqueId(), true);
                    player.sendMessage("§aĐăng nhập thành công! Rank hiện tại: " + rank);
                    configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua lệnh với rank " + rank + ".");
                    loginManager.playLoginEffects(player);
                    loginGUIs.remove(player.getUniqueId());
                    passwordInputs.remove(player.getUniqueId());
                } else {
                    player.sendMessage("§cMật khẩu sai!");
                    configManager.logSecurityEvent(player.getName() + " đăng nhập thất bại (mật khẩu sai).");
                }
                return true;

            case "forgot":
                if (!permissionHandler.isStaff(player) || !loginManager.isRegistered(player)) {
                    player.sendMessage("§cBạn không cần đăng nhập!");
                    return true;
                }
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                loginManager.sendForgotRequest(player, rank); // Sử dụng phương thức public
                return true;

            case "contactadmin":
                if (!permissionHandler.isStaff(player) || !loginManager.isRegistered(player)) {
                    player.sendMessage("§cBạn không cần đăng nhập!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cSử dụng: /opsec contactadmin <tin nhắn>");
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                boolean adminOnline = false;
                for (Player admin : plugin.getServer().getOnlinePlayers()) {
                    if (permissionHandler.isStaff(admin)) {
                        admin.sendMessage("§e[Liên hệ từ " + player.getName() + " (Rank: " + rank + ")]: " + message);
                        adminOnline = true;
                    }
                }
                if (!adminOnline) {
                    configManager.addMessageToAdmin("contact", player.getName(), message, rank);
                    player.sendMessage("§eKhông có admin online. Tin nhắn đã được lưu vào hộp thư.");
                } else {
                    player.sendMessage("§aTin nhắn đã gửi tới admin!");
                }
                configManager.logSecurityEvent(player.getName() + " đã gửi tin nhắn tới admin với rank " + rank + ": " + message);
                return true;

            case "check":
                if (!player.hasPermission("opsecurity.check")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage("§cSử dụng: /opsec check <player>");
                    return true;
                }
                targetName = args[1];
                target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    player.sendMessage("§cNgười chơi '" + targetName + "' không online!");
                    return true;
                }
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(target));
                player.sendMessage("§a" + targetName + " có rank: " + rank);
                configManager.logSecurityEvent(player.getName() + " đã kiểm tra rank của " + targetName + " là " + rank + ".");
                return true;

            case "addstaff":
                if (!player.hasPermission("opsecurity.addstaff")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cSử dụng: /addstaff <rank> <player>");
                    return true;
                }
                String addRank = args[1];
                targetName = args[2];
                target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    player.sendMessage("§cNgười chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!configManager.isValidRank(addRank)) {
                    player.sendMessage("§cRank '" + addRank + "' không hợp lệ!");
                    return true;
                }
                configManager.addStaffToYml(targetName, addRank);
                player.sendMessage("§aĐã thêm " + targetName + " vào rank " + addRank + " trong staff.yml!");
                configManager.logSecurityEvent(player.getName() + " đã thêm " + targetName + " vào rank " + addRank + ".");
                return true;

            case "removestaff":
                if (!player.hasPermission("opsecurity.removestaff")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cSử dụng: /removestaff <rank> <player>");
                    return true;
                }
                String removeRank = args[1];
                targetName = args[2];
                target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    player.sendMessage("§cNgười chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!configManager.isValidRank(removeRank)) {
                    player.sendMessage("§cRank '" + removeRank + "' không hợp lệ!");
                    return true;
                }
                List<String> staffList = configManager.getStaffConfig().getStringList(removeRank);
                if (staffList != null && staffList.contains(targetName)) {
                    staffList.remove(targetName);
                    configManager.getStaffConfig().set(removeRank, staffList);
                    configManager.saveFiles();
                    player.sendMessage("§aĐã xóa " + targetName + " khỏi rank " + removeRank + " trong staff.yml!");
                    configManager.logSecurityEvent(player.getName() + " đã xóa " + targetName + " khỏi rank " + removeRank + ".");
                } else {
                    player.sendMessage("§c" + targetName + " không tồn tại trong rank " + removeRank + "!");
                }
                return true;

            case "reset":
                if (!player.hasPermission("opsecurity.reset")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (!configManager.canResetPassword()) {
                    player.sendMessage("§cTính năng reset thủ công đã bị tắt trong config.yml!");
                    return true;
                }
                if (args.length != 3) {
                    player.sendMessage("§cSử dụng: /opsec reset <player> <password>");
                    return true;
                }
                targetName = args[1];
                String newPassword = args[2];
                if (!configManager.isPasswordValid(newPassword)) {
                    player.sendMessage("§cMật khẩu mới không hợp lệ hoặc vượt quá " + configManager.maxPasswordLength + " ký tự!");
                    return true;
                }
                target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    player.sendMessage("§cNgười chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!permissionHandler.isStaff(target) || !isRegistered(target)) {
                    player.sendMessage("§c" + targetName + " không phải staff hoặc chưa đăng ký!");
                    return true;
                }
                configManager.getDataConfig().set(target.getUniqueId().toString() + ".password", newPassword);
                configManager.getDataConfig().set(target.getUniqueId().toString() + ".last-reset", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                configManager.saveFiles();
                player.sendMessage("§aĐã reset mật khẩu cho " + targetName + " thành công!");
                target.sendMessage("§aMật khẩu của bạn đã được admin reset. Vui lòng đăng nhập lại với mật khẩu mới: " + newPassword);
                configManager.logSecurityEvent(player.getName() + " đã reset mật khẩu cho " + targetName + " với rank " + configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(target)) + ".");
                return true;

            case "update":
                if (!player.hasPermission("opsecurity.update")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                player.sendMessage("§eĐang kiểm tra và cập nhật phiên bản mới...");
                autoUpdater.manualCheckForUpdates(player);
                return true;

            case "reload":
                if (!player.hasPermission("opsecurity.reload")) {
                    player.sendMessage("§cBạn không có quyền dùng lệnh này!");
                    return true;
                }
                try {
                    plugin.reloadConfig();
                    configManager.loadConfig(); // Sử dụng phương thức public
                    configManager.loadFiles(); // Sử dụng phương thức public
                    player.sendMessage("§aĐã tải lại dữ liệu từ các file .yml thành công!");
                    configManager.logSecurityEvent(player.getName() + " đã tải lại dữ liệu plugin.");
                } catch (Exception e) {
                    player.sendMessage("§cLỗi khi tải lại dữ liệu: " + e.getMessage());
                    plugin.getLogger().severe("Lỗi khi reload dữ liệu: " + e.getMessage());
                    e.printStackTrace();
                    return true;
                }
                return true;

            default:
                player.sendMessage("§cLệnh không hợp lệ! Sử dụng: /opsec [register|login|forgot|check|contactadmin|reset|update|reload] [args]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player) sender;
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allCommands = new ArrayList<>(SUB_COMMANDS);
            if (player.hasPermission("opsecurity.addstaff")) allCommands.add("addstaff");
            if (player.hasPermission("opsecurity.removestaff")) allCommands.add("removestaff");
            if (player.hasPermission("opsecurity.check")) allCommands.add("check");
            if (player.hasPermission("opsecurity.reset")) allCommands.add("reset");
            if (player.hasPermission("opsecurity.update")) allCommands.add("update");
            if (player.hasPermission("opsecurity.reload")) allCommands.add("reload");
            for (String sub : allCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    if (sub.equals("register") && permissionHandler.isStaff(player) && !isRegistered(player)) suggestions.add(sub);
                    else if (sub.equals("login") && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                    else if ((sub.equals("forgot") || sub.equals("contactadmin") || sub.equals("check") || sub.equals("reset") || sub.equals("update") || sub.equals("reload")) && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                    else if (player.hasPermission("opsecurity." + sub)) suggestions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("addstaff") || subCommand.equals("removestaff")) {
                if (player.hasPermission("opsecurity." + subCommand)) suggestions.add("<rank>");
            } else if (subCommand.equals("check") || subCommand.equals("reset")) {
                if (player.hasPermission("opsecurity." + subCommand)) suggestions.add("<player>");
            } else if (subCommand.equals("update") || subCommand.equals("reload")) {
                // Không cần gợi ý gì thêm cho update và reload
            } else if (subCommand.equals("register") && permissionHandler.isStaff(player) && !isRegistered(player)) {
                suggestions.add("<mật khẩu>");
            } else if (subCommand.equals("login") && permissionHandler.isStaff(player) && isRegistered(player)) {
                suggestions.add("<mật khẩu>");
            } else if (subCommand.equals("contactadmin") && permissionHandler.isStaff(player) && isRegistered(player)) {
                suggestions.add("<tin nhắn>");
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("addstaff") || args[0].equalsIgnoreCase("removestaff"))) {
            if (player.hasPermission("opsecurity." + args[0].toLowerCase())) suggestions.add("<player>");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            if (player.hasPermission("opsecurity.reset")) suggestions.add("<password>");
        }
        return suggestions;
    }

    private boolean isAuthenticated(Player player) {
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }

    private boolean isRegistered(Player player) {
        return configManager.getDataConfig().contains(player.getUniqueId().toString() + ".password");
    }
}