package mc.sourcecode54.opSecurity.command;

import mc.sourcecode54.opSecurity.ConfigManager;
import mc.sourcecode54.opSecurity.OpSecCommand;
import mc.sourcecode54.opSecurity.OpSecurity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PlayerCommandHandler {
    private final OpSecCommand opSecCommand;

    public PlayerCommandHandler(OpSecCommand opSecCommand) {
        this.opSecCommand = opSecCommand;
    }

    public boolean handleCommand(Player player, String subCommand, String[] args) {
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
                String uuid = player.getUniqueId().toString(); // Khai báo uuid trong case này
                opSecCommand.config.setPassword(uuid, pwd, opSecCommand.config.getDefaultOrValidRank(opSecCommand.perms.getPlayerRank(player)), null);
                opSecCommand.config.addStaffToYml(player.getName(), opSecCommand.config.getRank(uuid));
                opSecCommand.config.saveFiles();
                String rank = opSecCommand.config.getRank(uuid); // Khai báo rank trong case này
                player.sendMessage(opSecCommand.config.getMessage("register-success", Map.of("rank", rank)));
                opSecCommand.loginMgr.setAuthenticated(player); // Sử dụng setAuthenticated
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("register-log", Map.of("player", player.getName(), "rank", rank)));
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
                uuid = player.getUniqueId().toString(); // Khai báo uuid trong case này
                String stored = opSecCommand.config.getPassword(uuid);
                if (args[1].equals(stored)) {
                    rank = opSecCommand.config.getRank(uuid); // Khai báo rank trong case này
                    opSecCommand.loginMgr.setAuthenticated(player); // Sử dụng setAuthenticated
                    player.sendMessage(opSecCommand.config.getMessage("login-success", Map.of("rank", rank)));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("register-log", Map.of("player", player.getName(), "rank", rank)));
                } else {
                    player.sendMessage(opSecCommand.config.getMessage("login-failure", null));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("login-log-failure", Map.of("player", player.getName())));
                }
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
                rank = opSecCommand.config.getRank(player.getUniqueId().toString()); // Khai báo rank trong case này
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
                Player target = Bukkit.getPlayer(args[1]); // Khai báo target rõ ràng trong case này
                if (target == null) {
                    player.sendMessage(opSecCommand.config.getMessage("check-offline", Map.of("player", args[1])));
                    return true;
                }
                rank = opSecCommand.config.getRank(target.getUniqueId().toString()); // Khai báo rank trong case này
                player.sendMessage(opSecCommand.config.getMessage("check-result", Map.of("player", target.getName(), "rank", rank)));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("check-log", Map.of("sender", player.getName(), "player", target.getName(), "rank", rank)));
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
                if (target == null) {
                    player.sendMessage(opSecCommand.config.getMessage("addstaff-offline", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.config.isValidRank(addRank)) {
                    player.sendMessage(opSecCommand.config.getMessage("addstaff-invalid-rank", Map.of("rank", addRank)));
                    return true;
                }
                opSecCommand.config.addStaffToYml(targetName, addRank);
                uuid = target.getUniqueId().toString(); // Khai báo uuid trong case này
                opSecCommand.config.setPassword(uuid, "", addRank, null);
                player.sendMessage(opSecCommand.config.getMessage("addstaff-success", Map.of("player", targetName, "rank", addRank)));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("addstaff-log", Map.of("sender", player.getName(), "player", targetName, "rank", addRank)));
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
                if (target == null) {
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-offline", Map.of("player", targetName)));
                    return true;
                }
                List<String> staff = opSecCommand.config.getStaffConfig().getStringList(removeRank);
                if (staff != null && staff.contains(targetName)) {
                    staff.remove(targetName);
                    opSecCommand.config.getStaffConfig().set(removeRank, staff);
                    opSecCommand.config.saveFiles();
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-success", Map.of("player", targetName, "rank", removeRank)));
                    opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("removestaff-log", Map.of("sender", player.getName(), "player", targetName, "rank", removeRank)));
                } else {
                    player.sendMessage(opSecCommand.config.getMessage("removestaff-not-in-rank", Map.of("player", targetName, "rank", removeRank)));
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
                if (target == null) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-offline", Map.of("player", targetName)));
                    return true;
                }
                if (!opSecCommand.perms.isStaff(target) || !opSecCommand.loginMgr.isRegistered(target)) {
                    player.sendMessage(opSecCommand.config.getMessage("reset-not-staff", Map.of("player", targetName)));
                    return true;
                }
                uuid = target.getUniqueId().toString(); // Khai báo uuid trong case này
                opSecCommand.config.setPassword(uuid, newPwd, opSecCommand.config.getRank(uuid), new Date());
                player.sendMessage(opSecCommand.config.getMessage("reset-success", Map.of("player", targetName)));
                target.sendMessage(opSecCommand.config.getMessage("reset-notify", null));
                opSecCommand.config.logSecurityEvent(opSecCommand.config.getMessage("reset-log", Map.of("sender", player.getName(), "player", targetName, "rank", opSecCommand.config.getRank(uuid))));
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