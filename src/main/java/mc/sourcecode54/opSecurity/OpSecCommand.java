package mc.sourcecode54.opSecurity;

import mc.sourcecode54.opSecurity.command.ConsoleCommandHandler;
import mc.sourcecode54.opSecurity.command.PlayerCommandHandler;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OpSecCommand implements CommandExecutor, TabCompleter {
    public final OpSecurity plugin;
    public final ConfigManager config;
    public final PermissionHandler perms;
    public final AutoUpdater updater;
    public final LoginManager loginMgr;
    private final ConsoleCommandHandler consoleHandler;
    private final PlayerCommandHandler playerHandler;
    private final LuckPerms luckPerms;

    public static final List<String> PLUGIN_COMMANDS = List.of("opsec", "addstaff", "removestaff");

    public OpSecCommand(OpSecurity plugin, ConfigManager config, PermissionHandler perms, AutoUpdater updater, LoginManager loginMgr) {
        this.plugin = plugin;
        this.config = config;
        this.perms = perms;
        this.updater = updater;
        this.loginMgr = loginMgr;
        this.consoleHandler = new ConsoleCommandHandler(this);
        this.playerHandler = new PlayerCommandHandler(this);
        this.luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (this.luckPerms == null) {
            plugin.getLogger().severe("LuckPerms không được tìm thấy! Vui lòng cài đặt plugin LuckPerms.");
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(config.getMessage("luckperms-disabled", null)));
        }
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public UUID getUUIDFromPlayerName(String playerName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        return offlinePlayer.getUniqueId();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                showHelp((Player) sender);
            } else {
                sender.sendMessage(config.getMessage("console-only-commands", null));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        boolean isConsole = !(sender instanceof Player);

        if (isConsole && !ConsoleCommandHandler.SUB_COMMANDS.contains(subCommand) && !PLUGIN_COMMANDS.contains(subCommand)) {
            sender.sendMessage(config.getMessage("console-only-commands", null));
            return true;
        }

        if (isConsole) {
            return consoleHandler.handleCommand(sender, subCommand, args);
        } else {
            return playerHandler.handleCommand((Player) sender, subCommand, args);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> cmds = ConsoleCommandHandler.SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            if (sender.hasPermission("opsecurity.addstaff")) cmds.add("addstaff");
            if (sender.hasPermission("opsecurity.removestaff")) cmds.add("removestaff");
            if (sender.hasPermission("opsecurity.check")) cmds.add("check");
            if (sender.hasPermission("opsecurity.reset")) cmds.add("reset");
            if (sender.hasPermission("opsecurity.update")) cmds.add("update");
            if (sender.hasPermission("opsecurity.reload")) cmds.add("reload");
            if (sender.hasPermission("opsecurity.register")) cmds.add("register");
            if (sender.hasPermission("opsecurity.login")) cmds.add("login");
            if (sender.hasPermission("opsecurity.forgot")) cmds.add("forgot");
            if (sender.hasPermission("opsecurity.contactadmin")) cmds.add("contactadmin");
            return cmds.stream().distinct().sorted().collect(Collectors.toList());
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "addstaff": case "removestaff":
                    if (sender.hasPermission("opsecurity." + args[0].toLowerCase())) {
                        return getLuckPermsRanks().stream()
                                .filter(r -> r.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    break;
                case "check": case "reset":
                    if (sender.hasPermission("opsecurity." + args[0].toLowerCase())) {
                        List<String> players = new ArrayList<>();
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            players.add(onlinePlayer.getName());
                        }
                        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                            players.add(offlinePlayer.getName());
                        }
                        return players.stream()
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList());
                    }
                    break;
                case "register":
                    if (sender.hasPermission("opsecurity.register")) {
                        return List.of("<mật khẩu>");
                    }
                    break;
                case "login":
                    if (sender.hasPermission("opsecurity.login")) {
                        return List.of("<mật khẩu>");
                    }
                    break;
                case "contactadmin":
                    if (sender.hasPermission("opsecurity.contactadmin")) {
                        return List.of("<tin nhắn>");
                    }
                    break;
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("addstaff") || args[0].equalsIgnoreCase("removestaff"))) {
            if (sender.hasPermission("opsecurity." + args[0].toLowerCase())) {
                List<String> players = new ArrayList<>();
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    players.add(onlinePlayer.getName());
                }
                for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    players.add(offlinePlayer.getName());
                }
                return players.stream()
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("opsecurity.reset")) {
            return List.of("<password>");
        }
        return List.of();
    }

    private List<String> getLuckPermsRanks() {
        if (luckPerms == null) return new ArrayList<>();
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .map(Group::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.DARK_GRAY + "┌───────────" + ChatColor.GOLD + " OpSecurity v1.1 by" + ChatColor.YELLOW + "TYBZI" + ChatColor.DARK_GRAY + "───────────┐");
        player.sendMessage(ChatColor.GRAY + "Lệnh chính: /opsec hoặc /os");
        if (player.hasPermission("opsecurity.register")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec register <mật khẩu>" + ChatColor.GRAY + " - Đăng ký tài khoản staff.");
        }
        if (player.hasPermission("opsecurity.login")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec login <mật khẩu>" + ChatColor.GRAY + " - Đăng nhập vào tài khoản.");
        }
        if (player.hasPermission("opsecurity.forgot")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec forgot" + ChatColor.GRAY + " - Yêu cầu reset mật khẩu.");
        }
        if (player.hasPermission("opsecurity.contactadmin")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec contactadmin <tin nhắn>" + ChatColor.GRAY + " - Liên hệ Admin.");
        }
        if (player.hasPermission("opsecurity.check")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec check <player>" + ChatColor.GRAY + " - Kiểm tra rank của Player.");
        }
        if (player.hasPermission("opsecurity.addstaff")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/addstaff <rank> <player> hoặc /opsec addstaff <rank> <player>" + ChatColor.GRAY + " - Thêm staff vào rank.");
        }
        if (player.hasPermission("opsecurity.removestaff")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/removestaff <rank> <player> hoặc /opsec removestaff <rank> <player>" + ChatColor.GRAY + " - Xóa staff khỏi rank.");
        }
        if (player.hasPermission("opsecurity.reset")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec reset <player> <password>" + ChatColor.GRAY + " - Reset mật khẩu staff.");
        }
        if (player.hasPermission("opsecurity.update")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec update" + ChatColor.GRAY + " - Kiểm tra và cập nhật plugin (Plugin sẽ tự động update khi có bản mới).");
        }
        if (player.hasPermission("opsecurity.reload")) {
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.GREEN + "/opsec reload" + ChatColor.GRAY + " - Tải lại cấu hình (chỉ áp dụng cho console).");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "└──────────────────────────────────┘");
    }
}