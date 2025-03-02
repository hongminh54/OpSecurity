package mc.sourcecode54.opSecurity.command;

import mc.sourcecode54.opSecurity.ConfigManager;
import mc.sourcecode54.opSecurity.OpSecCommand;
import mc.sourcecode54.opSecurity.OpSecurity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class PlayerCommandHandler {
    private final OpSecCommand opSecCommand;
    private String uuid;  // Khai báo uuid ở cấp phương thức để tái sử dụng
    private String rank;  // Khai báo rank ở cấp phương thức để tái sử dụng

    public PlayerCommandHandler(OpSecCommand opSecCommand) {
        this.opSecCommand = opSecCommand;
    }

    public boolean handleCommand(Player player, String subCommand, String[] args) {
        uuid = null;  // Khởi tạo lại uuid
        rank = null;  // Khởi tạo lại rank

        switch (subCommand.toLowerCase()) {  // Đảm bảo so sánh không phân biệt chữ hoa/thường
            case "register":
                // Không kiểm tra isStaff cho register, chỉ cần quyền opsecurity.register
                if (opSecCommand.loginMgr.isRegistered(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("already-registered", null));
                    return true;
                }
                if (!player.hasPermission("opsecurity.register")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(opSecCommand.config.getMessage("register-usage", null));
                    return true;
                }
                String pwd = args[1];
                if (pwd.length() > opSecCommand.config.maxPasswordLength) {
                    player.sendMessage(opSecCommand.config.getMessage("password-too-long", Map.of("maxLength", String.valueOf(opSecCommand.config.maxPasswordLength))));
                    return true;
                }
                uuid = player.getUniqueId().toString(); // Gán uuid
                if (opSecCommand.config.useLuckPerms && opSecCommand.getLuckPerms() != null) {
                    // Lấy rank từ LuckPerms
                    rank = opSecCommand.perms.getPlayerRank(player);
                    if (!opSecCommand.config.isValidRank(rank)) {
                        rank = opSecCommand.config.getDefaultOrValidRank(rank);  // Fallback nếu rank không hợp lệ
                    }
                } else {
                    // Fallback về rank từ valid-ranks hoặc Default
                    rank = opSecCommand.config.getDefaultOrValidRank(opSecCommand.perms.getPlayerRank(player));
                }
                opSecCommand.config.setPassword(uuid, pwd, rank, null);
                opSecCommand.config.addStaffToYml(player.getName(), rank);
                opSecCommand.config.saveFiles();
                player.sendMessage(opSecCommand.config.getMessage("register-success", Map.of("rank", rank != null ? rank : "Default")));
                opSecCommand.loginMgr.setAuthenticated(player); // Sử dụng setAuthenticated
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("register-log", Map.of("player", player.getName(), "rank", rank != null ? rank : "Default")));
                return true;

            case "login":
                // Chỉ kiểm tra quyền opsecurity.login, không chặn nếu không phải staff
                if (!opSecCommand.loginMgr.isRegistered(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("not-registered", null));
                    return true;
                }
                if (!player.hasPermission("opsecurity.login")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (opSecCommand.loginMgr.isAuthenticated(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("already-logged-in", null));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(opSecCommand.config.getMessage("login-usage", null));
                    return true;
                }
                uuid = player.getUniqueId().toString(); // Gán uuid
                String stored = opSecCommand.config.getPassword(uuid);
                if (args[1].equals(stored)) {
                    rank = opSecCommand.config.getRank(uuid); // Gán rank
                    opSecCommand.loginMgr.setAuthenticated(player); // Sử dụng setAuthenticated
                    // Sử dụng HashMap thay vì Map.of để tránh NullPointerException khi rank là null
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("rank", rank != null ? rank : "Default");
                    player.sendMessage(opSecCommand.config.getMessage("login-success", placeholders));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("register-log", Map.of("player", player.getName(), "rank", rank != null ? rank : "Default")));
                } else {
                    player.sendMessage(opSecCommand.config.getMessage("login-failure", null));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("login-log-failure", Map.of("player", player.getName())));
                }
                return true;

            case "changepassword": // Giữ nguyên logic nhưng chỉ dùng /opsec changepassword
                if (!player.hasPermission("opsecurity.changepassword")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (!opSecCommand.loginMgr.isAuthenticated(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("login-required-command", null));
                    return true;
                }
                if (args.length != 3) {
                    player.sendMessage(opSecCommand.config.getMessage("changepassword-usage", null));
                    return true;
                }
                String oldPassword = args[1];
                String newPassword = args[2];
                uuid = player.getUniqueId().toString(); // Gán uuid
                String currentPassword = opSecCommand.config.getPassword(uuid);
                if (currentPassword == null || !currentPassword.equals(oldPassword)) {
                    player.sendMessage(opSecCommand.config.getMessage("changepassword-failure", Map.of("reason", "Mật khẩu cũ sai")));
                    return true;
                }
                if (!opSecCommand.config.isPasswordValid(newPassword)) {
                    player.sendMessage(opSecCommand.config.getMessage("changepassword-failure", Map.of("reason", "Mật khẩu mới không hợp lệ (≤ " + opSecCommand.config.maxPasswordLength + " ký tự)")));
                    return true;
                }
                if (oldPassword.equals(newPassword)) {
                    player.sendMessage(opSecCommand.config.getMessage("changepassword-failure", Map.of("reason", "Mật khẩu mới phải khác mật khẩu cũ")));
                    return true;
                }
                rank = opSecCommand.config.getRank(uuid); // Gán rank trước khi lưu
                opSecCommand.config.setPassword(uuid, newPassword, rank, new Date());
                opSecCommand.config.saveFiles();
                player.sendMessage(opSecCommand.config.getMessage("changepassword-success", Map.of("player", player.getName())));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("changepassword-log", Map.of("player", player.getName(), "rank", rank != null ? rank : "Default")));
                return true;

            case "forgot":
                if (!opSecCommand.loginMgr.isRegistered(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("forgot-not-needed", null));
                    return true;
                }
                if (!player.hasPermission("opsecurity.forgot")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                opSecCommand.loginMgr.sendContactRequest(player, opSecCommand.config.getDefaultOrValidRank(opSecCommand.perms.getPlayerRank(player)), "forgot", null);
                return true;

            case "contactadmin":
                if (!opSecCommand.loginMgr.isRegistered(player)) {
                    player.sendMessage(opSecCommand.config.getMessage("forgot-not-needed", null));
                    return true;
                }
                if (!player.hasPermission("opsecurity.contactadmin")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(opSecCommand.config.getMessage("contactadmin-usage", null));
                    return true;
                }
                String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                rank = opSecCommand.config.getRank(player.getUniqueId().toString()); // Gán rank
                opSecCommand.loginMgr.sendContactRequest(player, rank, "contact", msg);
                return true;

            case "check":
                if (!player.hasPermission("opsecurity.check")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(opSecCommand.config.getMessage("check-usage", null));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]); // Khai báo target online trước
                OfflinePlayer offlineTarget = target != null ? target : Bukkit.getOfflinePlayer(args[1]); // Hỗ trợ player offline
                if (target == null && !offlineTarget.hasPlayedBefore()) {
                    player.sendMessage(opSecCommand.config.getMessage("check-offline", Map.of("player", args[1])));
                    return true;
                }
                rank = opSecCommand.config.getRank(offlineTarget.getUniqueId().toString()); // Lấy rank từ UUID, gán rank
                player.sendMessage(opSecCommand.config.getMessage("check-result", Map.of("player", offlineTarget.getName(), "rank", rank != null ? rank : "Default")));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("check-log", Map.of("sender", player.getName(), "player", offlineTarget.getName(), "rank", rank != null ? rank : "Default")));
                return true;

            case "addstaff":
                if (!player.hasPermission("opsecurity.addstaff")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(opSecCommand.config.getMessage("addstaff-usage", null));
                    return true;
                }
                String addRank = args[1];
                String targetName = args[2]; // Khai báo targetName rõ ràng trong case này
                target = Bukkit.getPlayer(targetName);
                OfflinePlayer offlineAddTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName); // Hỗ trợ player offline
                if (!opSecCommand.config.isValidRank(addRank)) {
                    player.sendMessage(opSecCommand.config.getMessage("addstaff-invalid-rank", Map.of("rank", addRank)));
                    return true;
                }
                // Kiểm tra nếu rank hợp lệ từ LuckPerms hoặc valid-ranks
                if (opSecCommand.config.useLuckPerms && opSecCommand.getLuckPerms() != null) {
                    // Kiểm tra rank từ LuckPerms
                    if (!opSecCommand.getLuckPerms().getGroupManager().getLoadedGroups().stream()
                            .anyMatch(group -> group.getName().equalsIgnoreCase(addRank))) {
                        player.sendMessage(opSecCommand.config.getMessage("addstaff-invalid-rank", Map.of("rank", addRank)));
                        return true;
                    }
                }
                opSecCommand.config.addStaffToYml(offlineAddTarget.getName(), addRank);
                uuid = offlineAddTarget.getUniqueId().toString(); // Gán uuid
                opSecCommand.config.setPassword(uuid, "", addRank, null);
                opSecCommand.config.saveFiles();
                player.sendMessage(opSecCommand.config.getMessage("addstaff-success", Map.of("player", offlineAddTarget.getName(), "rank", addRank)));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("addstaff-log", Map.of("sender", player.getName(), "player", offlineAddTarget.getName(), "rank", addRank)));
                return true;

            case "removestaff":
                if (!player.hasPermission("opsecurity.removestaff")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-usage", null));
                    return true;
                }
                String removeRank = args[1];
                targetName = args[2]; // Khai báo targetName rõ ràng trong case này
                target = Bukkit.getPlayer(targetName);
                OfflinePlayer offlineRemoveTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName); // Hỗ trợ player offline
                if (!opSecCommand.config.isValidRank(removeRank)) {
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-not-in-rank", Map.of("player", targetName, "rank", removeRank)));
                    return true;
                }
                List<String> staff = opSecCommand.config.getStaffConfig().getStringList(removeRank);
                if (staff != null && staff.contains(offlineRemoveTarget.getName())) {
                    staff.remove(offlineRemoveTarget.getName());
                    opSecCommand.config.getStaffConfig().set(removeRank, staff);
                    opSecCommand.config.saveFiles();
                    // Xóa password và rank khỏi data.yml nếu cần
                    uuid = offlineRemoveTarget.getUniqueId().toString(); // Gán uuid
                    opSecCommand.config.setPassword(uuid, "", "Default", null); // Đặt lại rank về Default
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-success", Map.of("player", offlineRemoveTarget.getName(), "rank", removeRank)));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("removestaff-log", Map.of("sender", player.getName(), "player", offlineRemoveTarget.getName(), "rank", removeRank)));
                } else {
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-not-in-rank", Map.of("player", offlineRemoveTarget.getName(), "rank", removeRank)));
                }
                return true;

            case "reset":
                if (!player.hasPermission("opsecurity.reset")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                if (args.length != 3) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-usage", null));
                    return true;
                }
                if (!opSecCommand.config.canResetPassword()) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-disabled", null));
                    return true;
                }
                targetName = args[1]; // Khai báo targetName rõ ràng trong case này
                String newPwd = args[2];
                if (!opSecCommand.config.isPasswordValid(newPwd)) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-invalid-password", Map.of("maxLength", String.valueOf(opSecCommand.config.maxPasswordLength))));
                    return true;
                }
                target = Bukkit.getPlayer(targetName); // Khai báo target rõ ràng trong case này
                OfflinePlayer offlineResetTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName);
                if (target == null && !offlineResetTarget.hasPlayedBefore()) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-offline", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.perms.isStaff(offlineResetTarget)) {  // Sử dụng phiên bản mới của isStaff cho OfflinePlayer
                    player.sendMessage(opSecCommand.config.getMessage("reset-not-staff", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.loginMgr.isRegistered(offlineResetTarget)) {  // Sử dụng phiên bản mới của isRegistered cho OfflinePlayer
                    player.sendMessage(opSecCommand.config.getMessage("reset-not-staff", Map.of("player", targetName)));
                    return true;
                }
                uuid = offlineResetTarget.getUniqueId().toString(); // Gán uuid
                rank = opSecCommand.config.getRank(uuid); // Gán rank trước khi lưu
                opSecCommand.config.setPassword(uuid, newPwd, rank, new Date());
                player.sendMessage(opSecCommand.config.getMessage("reset-success", Map.of("player", offlineResetTarget.getName())));
                if (target != null) { // Nếu player online, gửi thông báo
                    target.sendMessage(opSecCommand.config.getMessage("reset-notify", null));
                }
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("reset-log", Map.of("sender", player.getName(), "player", offlineResetTarget.getName(), "rank", rank != null ? rank : "Default")));
                return true;

            case "update":
                if (!player.hasPermission("opsecurity.update")) {
                    player.sendMessage(opSecCommand.config.getMessage("no-permission", null));
                    return true;
                }
                player.sendMessage(opSecCommand.config.getMessage("update-checking", null));
                opSecCommand.updater.manualCheckForUpdates(player);
                return true;

            default:
                player.sendMessage(opSecCommand.config.getMessage("invalid-usage", null));
                return true;
        }
    }
}