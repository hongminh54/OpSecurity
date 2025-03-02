package mc.sourcecode54.opSecurity.command;

import mc.sourcecode54.opSecurity.ConfigManager;
import mc.sourcecode54.opSecurity.OpSecCommand;
import mc.sourcecode54.opSecurity.OpSecurity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ConsoleCommandHandler {
    private final OpSecCommand opSecCommand;

    public static final List<String> SUB_COMMANDS = List.of("register", "login", "forgot", "check", "contactadmin", "reset", "update", "reload");

    public ConsoleCommandHandler(OpSecCommand opSecCommand) {
        this.opSecCommand = opSecCommand;
    }

    public boolean handleCommand(CommandSender sender, String subCommand, String[] args) {
        switch (subCommand.toLowerCase()) {  // Đảm bảo so sánh không phân biệt chữ hoa/thường
            case "reload":
                // Chỉ cho phép console hoặc owner (có quyền opsecurity.reload)
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.isOp() && !player.hasPermission("opsecurity.reload")) {
                        player.sendMessage(opSecCommand.config.getMessage("reload-player-denied", null));
                        return true;
                    }
                }
                try {
                    opSecCommand.plugin.reloadConfig();  // Tải lại config.yml
                    opSecCommand.config.loadConfig();  // Tải lại cấu hình từ config.yml
                    opSecCommand.config.loadFiles();  // Tải lại messages.yml, data.yml, staff.yml
                    opSecCommand.config.initializeDatabase();  // Tái khởi tạo kết nối cơ sở dữ liệu nếu cần
                    sender.sendMessage(opSecCommand.config.getMessage("reload-success", null));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("reload-log", Map.of("sender", sender.getName() != null ? sender.getName() : "Console")));
                } catch (Exception e) {
                    sender.sendMessage(opSecCommand.config.getMessage("reload-failure", Map.of("error", e.getMessage())));
                    opSecCommand.plugin.getLogger().severe("Lỗi reload: " + e.getMessage());
                }
                return true;

            case "check":
                if (!sender.hasPermission("opsecurity.check")) {
                    sender.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(opSecCommand.config.getMessage("check-usage", null));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]); // Khai báo target online trước
                OfflinePlayer offlineTarget = target != null ? target : Bukkit.getOfflinePlayer(args[1]); // Hỗ trợ player offline
                if (target == null && !offlineTarget.hasPlayedBefore()) {
                    sender.sendMessage(opSecCommand.config.getMessage("check-offline", Map.of("player", args[1])));
                    return true;
                }
                String rank = opSecCommand.config.getRank(offlineTarget.getUniqueId().toString()); // Lấy rank từ UUID
                sender.sendMessage(opSecCommand.config.getMessage("check-result", Map.of("player", offlineTarget.getName(), "rank", rank)));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("check-log", Map.of("sender", sender.getName() != null ? sender.getName() : "Console", "player", offlineTarget.getName(), "rank", rank)));
                return true;

            case "addstaff":
                if (!sender.hasPermission("opsecurity.addstaff")) {
                    sender.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(opSecCommand.config.getMessage("addstaff-usage", null));
                    return true;
                }
                String addRank = args[1];
                String targetName = args[2]; // Khai báo targetName rõ ràng trong case này
                target = Bukkit.getPlayer(targetName);
                OfflinePlayer offlineAddTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName); // Hỗ trợ player offline
                if (!opSecCommand.config.isValidRank(addRank)) {
                    sender.sendMessage(opSecCommand.config.getMessage("addstaff-invalid-rank", Map.of("rank", addRank)));
                    return true;
                }
                // Kiểm tra nếu rank hợp lệ từ LuckPerms hoặc valid-ranks
                if (opSecCommand.config.useLuckPerms && opSecCommand.getLuckPerms() != null) {
                    // Kiểm tra rank từ LuckPerms
                    if (!opSecCommand.getLuckPerms().getGroupManager().getLoadedGroups().stream()
                            .anyMatch(group -> group.getName().equalsIgnoreCase(addRank))) {
                        sender.sendMessage(opSecCommand.config.getMessage("addstaff-invalid-rank", Map.of("rank", addRank)));
                        return true;
                    }
                }
                opSecCommand.config.addStaffToYml(offlineAddTarget.getName(), addRank);
                String uuid = offlineAddTarget.getUniqueId().toString(); // Lấy UUID từ offline player
                opSecCommand.config.setPassword(uuid, "", addRank, null);
                opSecCommand.config.saveFiles();
                sender.sendMessage(opSecCommand.config.getMessage("addstaff-success", Map.of("player", offlineAddTarget.getName(), "rank", addRank)));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("addstaff-log", Map.of("sender", sender.getName() != null ? sender.getName() : "Console", "player", offlineAddTarget.getName(), "rank", addRank)));
                return true;

            case "removestaff":
                if (!sender.hasPermission("opsecurity.removestaff")) {
                    sender.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(opSecCommand.config.getMessage("removestaff-usage", null));
                    return true;
                }
                String removeRank = args[1];
                targetName = args[2]; // Khai báo targetName rõ ràng trong case này
                target = Bukkit.getPlayer(targetName);
                OfflinePlayer offlineRemoveTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName); // Hỗ trợ player offline
                if (!opSecCommand.config.isValidRank(removeRank)) {
                    sender.sendMessage(opSecCommand.config.getMessage("removestaff-not-in-rank", Map.of("player", targetName, "rank", removeRank)));
                    return true;
                }
                List<String> staff = opSecCommand.config.getStaffConfig().getStringList(removeRank);
                if (staff != null && staff.contains(offlineRemoveTarget.getName())) {
                    staff.remove(offlineRemoveTarget.getName());
                    opSecCommand.config.getStaffConfig().set(removeRank, staff);
                    opSecCommand.config.saveFiles();
                    // Xóa password và rank khỏi data.yml nếu cần
                    uuid = offlineRemoveTarget.getUniqueId().toString();
                    opSecCommand.config.setPassword(uuid, "", "Default", null); // Đặt lại rank về Default
                    sender.sendMessage(opSecCommand.config.getMessage("removestaff-success", Map.of("player", offlineRemoveTarget.getName(), "rank", removeRank)));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("removestaff-log", Map.of("sender", sender.getName() != null ? sender.getName() : "Console", "player", offlineRemoveTarget.getName(), "rank", removeRank)));
                } else {
                    sender.sendMessage(opSecCommand.config.getMessage("removestaff-not-in-rank", Map.of("player", offlineRemoveTarget.getName(), "rank", removeRank)));
                }
                return true;

            case "update":
                sender.sendMessage(opSecCommand.config.getMessage("update-checking", null));
                opSecCommand.updater.manualCheckForUpdates(sender);
                return true;

            case "reset":
                if (!sender.hasPermission("opsecurity.reset")) {
                    sender.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length != 3) {
                    sender.sendMessage(opSecCommand.config.getMessage("reset-usage", null));
                    return true;
                }
                if (!opSecCommand.config.canResetPassword()) {
                    sender.sendMessage(opSecCommand.config.getMessage("reset-disabled", null));
                    return true;
                }
                targetName = args[1]; // Khai báo targetName rõ ràng trong case này
                String newPwd = args[2];
                if (!opSecCommand.config.isPasswordValid(newPwd)) {
                    sender.sendMessage(opSecCommand.config.getMessage("reset-invalid-password", Map.of("maxLength", String.valueOf(opSecCommand.config.maxPasswordLength))));
                    return true;
                }
                target = Bukkit.getPlayer(targetName); // Khai báo target rõ ràng trong case này
                OfflinePlayer offlineResetTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName);
                if (target == null && !offlineResetTarget.hasPlayedBefore()) {
                    sender.sendMessage(opSecCommand.config.getMessage("reset-offline", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.perms.isStaff(offlineResetTarget)) {  // Sử dụng phiên bản mới của isStaff cho OfflinePlayer
                    sender.sendMessage(opSecCommand.config.getMessage("reset-not-staff", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.loginMgr.isRegistered(offlineResetTarget)) {  // Sử dụng phiên bản mới của isRegistered cho OfflinePlayer
                    sender.sendMessage(opSecCommand.config.getMessage("reset-not-staff", Map.of("player", targetName)));
                    return true;
                }
                uuid = offlineResetTarget.getUniqueId().toString(); // Lấy UUID từ offline player
                opSecCommand.config.setPassword(uuid, newPwd, opSecCommand.config.getRank(uuid), new Date());
                sender.sendMessage(opSecCommand.config.getMessage("reset-success", Map.of("player", offlineResetTarget.getName())));
                if (target != null) { // Nếu player online, gửi thông báo
                    target.sendMessage(opSecCommand.config.getMessage("reset-notify", null));
                }
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("reset-log", Map.of("sender", sender.getName() != null ? sender.getName() : "Console", "player", offlineResetTarget.getName(), "rank", opSecCommand.config.getRank(uuid))));
                return true;

            default:
                sender.sendMessage(opSecCommand.config.getMessage("invalid-usage", null));
                return true;
        }
    }
}