package mc.sourcecode54.opSecurity;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class LoginManager implements CommandExecutor, TabCompleter, Listener {
    private final OpSecurity plugin;
    private final ConfigManager configManager;
    private final PermissionHandler permissionHandler;
    private final Map<UUID, Boolean> authenticated = new HashMap<>();
    private final Map<UUID, Inventory> loginGUIs = new HashMap<>();
    private final Map<UUID, StringBuilder> passwordInputs = new HashMap<>();
    public static final List<String> PLUGIN_COMMANDS = Arrays.asList("opsec", "addstaff", "removestaff");
    private static final List<String> SUB_COMMANDS = Arrays.asList("register", "login", "forgot", "check", "contactadmin", "reset");

    public LoginManager(OpSecurity plugin, ConfigManager configManager, PermissionHandler permissionHandler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.permissionHandler = permissionHandler;
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.getOrDefault(player.getUniqueId(), false);
    }

    public boolean isRegistered(Player player) {
        return configManager.getDataConfig().contains(player.getUniqueId().toString() + ".password");
    }

    public void openLoginGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, ChatColor.DARK_AQUA + "Đăng nhập Staff");
        ItemStack enter = new ItemStack(getCompatibleMaterial("GREEN_WOOL", "LIME_DYE"));
        ItemMeta enterMeta = enter.getItemMeta();
        enterMeta.setDisplayName(ChatColor.GREEN + "Nhập mật khẩu (Chat để nhập)");
        enter.setItemMeta(enterMeta);

        ItemStack forgot = new ItemStack(getCompatibleMaterial("RED_WOOL", "RED_DYE"));
        ItemMeta forgotMeta = forgot.getItemMeta();
        forgotMeta.setDisplayName(ChatColor.RED + "Quên mật khẩu");
        forgot.setItemMeta(forgotMeta);

        gui.setItem(3, enter);
        gui.setItem(5, forgot);

        player.openInventory(gui);
        loginGUIs.put(player.getUniqueId(), gui);
        passwordInputs.put(player.getUniqueId(), new StringBuilder());
        authenticated.put(player.getUniqueId(), false);
    }

    public void playLoginEffects(Player player) {
        if (!configManager.enableLoginEffects) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = player.getLocation();
                player.playSound(loc, getCompatibleSound("ENTITY_PLAYER_LEVELUP", "LEVEL_UP"), 1.0f, 1.0f);

                Firework fw = (Firework) player.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().withColor(Color.YELLOW).withFade(Color.ORANGE).with(FireworkEffect.Type.STAR).trail(true).flicker(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        fw.detonate();
                    }
                }.runTaskLater(plugin, 10L);
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        if (!loginGUIs.containsKey(uuid) || event.getInventory() != loginGUIs.get(uuid)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName.equals(ChatColor.GREEN + "Nhập mật khẩu (Chat để nhập)")) {
            player.sendMessage(ChatColor.YELLOW + "Nhập mật khẩu vào chat:");
            player.closeInventory();
        } else if (displayName.equals(ChatColor.RED + "Quên mật khẩu")) {
            String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
            sendForgotRequest(player, rank);
            player.closeInventory();
        }
    }

    private void sendForgotRequest(Player player, String rank) {
        String message = player.getName() + " (Rank: " + rank + ") yêu cầu reset mật khẩu. Vui lòng xử lý thủ công qua /opsec reset.";
        boolean adminOnline = false;

        for (Player admin : plugin.getServer().getOnlinePlayers()) {
            if (permissionHandler.isStaff(admin)) {
                admin.sendMessage(ChatColor.YELLOW + message);
                adminOnline = true;
            }
        }
        if (!adminOnline) {
            configManager.addMessageToAdmin("forgot", player.getName(), "Yêu cầu reset mật khẩu", rank);
            player.sendMessage(ChatColor.YELLOW + "Không có admin online. Yêu cầu đã được lưu vào hộp thư.");
        }
        configManager.logSecurityEvent(player.getName() + " đã yêu cầu reset mật khẩu với rank " + rank + ".");
        player.sendMessage(ChatColor.YELLOW + "Vui lòng chờ admin xử lý yêu cầu reset mật khẩu qua /opsec reset.");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (passwordInputs.containsKey(uuid)) {
            event.setCancelled(true);
            String message = event.getMessage();

            StringBuilder passwordInput = passwordInputs.get(uuid);
            passwordInput.append(message);

            String storedPassword = configManager.getDataConfig().getString(uuid.toString() + ".password");
            if (passwordInput.toString().equals(storedPassword)) {
                String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                authenticated.put(uuid, true);
                player.sendMessage(ChatColor.GREEN + "Đăng nhập thành công! Rank hiện tại: " + rank);
                configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua GUI với rank " + rank + ".");
                playLoginEffects(player);
                loginGUIs.remove(uuid);
                passwordInputs.remove(uuid);
            } else {
                player.sendMessage(ChatColor.RED + "Mật khẩu sai! Nhập lại:");
                configManager.logSecurityEvent(player.getName() + " đăng nhập thất bại qua GUI (mật khẩu sai).");
                passwordInputs.put(uuid, new StringBuilder());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && !isAuthenticated(player)) {
                            openLoginGUI(player);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (loginGUIs.containsKey(uuid) && !isAuthenticated(player)) {
            player.sendMessage(ChatColor.YELLOW + "Vui lòng nhập mật khẩu vào chat hoặc dùng /opsec login!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player)) {
            String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
            List<String> pendingMessages = configManager.getPendingMessages();
            if (!pendingMessages.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Bạn có tin nhắn chưa đọc (Rank: " + rank + "):");
                for (String msg : pendingMessages) {
                    player.sendMessage(msg);
                }
                configManager.clearPendingMessages();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Chỉ người chơi mới dùng được lệnh này!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Sử dụng: /opsec [register|login|forgot|check|contactadmin|reset] [args]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String targetName = null; // Khai báo targetName ở mức cao nhất
        Player target = null; // Khai báo target ở mức cao nhất để tránh trùng lặp

        switch (subCommand) {
            case "register":
                if (!permissionHandler.isStaff(player)) {
                    player.sendMessage(ChatColor.RED + "Bạn không phải staff!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /opsec register <mật khẩu>");
                    return true;
                }
                String password = args[1];
                if (password.length() > configManager.maxPasswordLength) {
                    player.sendMessage(ChatColor.RED + "Mật khẩu không được vượt quá " + configManager.maxPasswordLength + " ký tự!");
                    return true;
                }
                configManager.getDataConfig().set(player.getUniqueId().toString() + ".password", password);
                String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                configManager.addStaffToYml(player.getName(), rank);
                configManager.saveFiles();
                player.sendMessage(ChatColor.GREEN + "Đăng ký thành công với rank " + rank + "!");
                authenticated.put(player.getUniqueId(), true);
                configManager.logSecurityEvent(player.getName() + " đã đăng ký thành công với rank " + rank + ".");
                playLoginEffects(player);
                break;

            case "login":
                if (!permissionHandler.isStaff(player) || !isRegistered(player)) {
                    player.sendMessage(ChatColor.RED + "Bạn không cần đăng nhập!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /opsec login <mật khẩu>");
                    return true;
                }
                String storedPassword = configManager.getDataConfig().getString(player.getUniqueId().toString() + ".password");
                if (args[1].equals(storedPassword)) {
                    rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                    authenticated.put(player.getUniqueId(), true);
                    player.sendMessage(ChatColor.GREEN + "Đăng nhập thành công! Rank hiện tại: " + rank);
                    configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua lệnh với rank " + rank + ".");
                    playLoginEffects(player);
                    loginGUIs.remove(player.getUniqueId());
                    passwordInputs.remove(player.getUniqueId());
                } else {
                    player.sendMessage(ChatColor.RED + "Mật khẩu sai!");
                    configManager.logSecurityEvent(player.getName() + " đăng nhập thất bại (mật khẩu sai).");
                }
                break;

            case "forgot":
                if (!permissionHandler.isStaff(player) || !isRegistered(player)) {
                    player.sendMessage(ChatColor.RED + "Bạn không cần đăng nhập!");
                    return true;
                }
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                sendForgotRequest(player, rank);
                break;

            case "contactadmin":
                if (!permissionHandler.isStaff(player) || !isRegistered(player)) {
                    player.sendMessage(ChatColor.RED + "Bạn không cần đăng nhập!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /opsec contactadmin <tin nhắn>");
                    return true;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
                boolean adminOnline = false;
                for (Player admin : plugin.getServer().getOnlinePlayers()) {
                    if (permissionHandler.isStaff(admin)) {
                        admin.sendMessage(ChatColor.YELLOW + "[Liên hệ từ " + player.getName() + " (Rank: " + rank + ")]: " + message);
                        adminOnline = true;
                    }
                }
                if (!adminOnline) {
                    configManager.addMessageToAdmin("contact", player.getName(), message, rank);
                    player.sendMessage(ChatColor.YELLOW + "Không có admin online. Tin nhắn đã được lưu vào hộp thư.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Tin nhắn đã gửi tới admin!");
                }
                configManager.logSecurityEvent(player.getName() + " đã gửi tin nhắn tới admin với rank " + rank + ": " + message);
                break;

            case "check":
                if (!player.hasPermission("opsecurity.check")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /opsec check <player>");
                    return true;
                }
                targetName = args[1];
                target = Bukkit.getPlayer(targetName); // Sử dụng target đã khai báo
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Người chơi '" + targetName + "' không online!");
                    return true;
                }
                rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(target));
                player.sendMessage(ChatColor.GREEN + targetName + " có rank: " + rank);
                configManager.logSecurityEvent(player.getName() + " đã kiểm tra rank của " + targetName + " là " + rank + ".");
                break;

            case "addstaff":
                if (!player.hasPermission("opsecurity.addstaff")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /addstaff <rank> <player>");
                    return true;
                }
                String addRank = args[1];
                targetName = args[2];
                target = Bukkit.getPlayer(targetName); // Sử dụng target đã khai báo
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Người chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!configManager.isValidRank(addRank)) {
                    player.sendMessage(ChatColor.RED + "Rank '" + addRank + "' không hợp lệ!");
                    return true;
                }
                configManager.addStaffToYml(targetName, addRank);
                player.sendMessage(ChatColor.GREEN + "Đã thêm " + targetName + " vào rank " + addRank + " trong staff.yml!");
                configManager.logSecurityEvent(player.getName() + " đã thêm " + targetName + " vào rank " + addRank + ".");
                break;

            case "removestaff":
                if (!player.hasPermission("opsecurity.removestaff")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /removestaff <rank> <player>");
                    return true;
                }
                String removeRank = args[1];
                targetName = args[2];
                target = Bukkit.getPlayer(targetName); // Sử dụng target đã khai báo
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Người chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!configManager.isValidRank(removeRank)) {
                    player.sendMessage(ChatColor.RED + "Rank '" + removeRank + "' không hợp lệ!");
                    return true;
                }
                List<String> staffList = configManager.getStaffConfig().getStringList(removeRank);
                if (staffList != null && staffList.contains(targetName)) {
                    staffList.remove(targetName);
                    configManager.getStaffConfig().set(removeRank, staffList);
                    configManager.saveFiles();
                    player.sendMessage(ChatColor.GREEN + "Đã xóa " + targetName + " khỏi rank " + removeRank + " trong staff.yml!");
                    configManager.logSecurityEvent(player.getName() + " đã xóa " + targetName + " khỏi rank " + removeRank + ".");
                } else {
                    player.sendMessage(ChatColor.RED + targetName + " không tồn tại trong rank " + removeRank + "!");
                }
                break;

            case "reset":
                if (!player.hasPermission("opsecurity.reset")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này!");
                    return true;
                }
                if (!configManager.canResetPassword()) {
                    player.sendMessage(ChatColor.RED + "Tính năng reset thủ công đã bị tắt trong config.yml!");
                    return true;
                }
                if (args.length != 3) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /opsec reset <player> <password>");
                    return true;
                }
                targetName = args[1];
                String newPassword = args[2];
                if (!configManager.isPasswordValid(newPassword)) {
                    player.sendMessage(ChatColor.RED + "Mật khẩu mới không hợp lệ hoặc vượt quá " + configManager.maxPasswordLength + " ký tự!");
                    return true;
                }
                target = Bukkit.getPlayer(targetName); // Sử dụng target đã khai báo
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Người chơi '" + targetName + "' không online!");
                    return true;
                }
                if (!permissionHandler.isStaff(target) || !isRegistered(target)) {
                    player.sendMessage(ChatColor.RED + targetName + " không phải staff hoặc chưa đăng ký!");
                    return true;
                }
                configManager.getDataConfig().set(target.getUniqueId().toString() + ".password", newPassword);
                configManager.getDataConfig().set(target.getUniqueId().toString() + ".last-reset", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                configManager.saveFiles();
                player.sendMessage(ChatColor.GREEN + "Đã reset mật khẩu cho " + targetName + " thành công!");
                target.sendMessage(ChatColor.GREEN + "Mật khẩu của bạn đã được admin reset. Vui lòng đăng nhập lại với mật khẩu mới: " + newPassword);
                configManager.logSecurityEvent(player.getName() + " đã reset mật khẩu cho " + targetName + " với rank " + configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(target)) + ".");
                break;

            default:
                player.sendMessage(ChatColor.RED + "Lệnh không hợp lệ! Dùng: /opsec [register|login|forgot|check|contactadmin|reset] hoặc /addstaff/removestaff <rank> <player>");
                break;
        }
        return true;
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
            for (String sub : allCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    if (sub.equals("register") && permissionHandler.isStaff(player) && !isRegistered(player)) suggestions.add(sub);
                    else if (sub.equals("login") && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                    else if ((sub.equals("forgot") || sub.equals("contactadmin") || sub.equals("check") || sub.equals("reset")) && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                    else if (player.hasPermission("opsecurity." + sub)) suggestions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("addstaff") || subCommand.equals("removestaff")) {
                if (player.hasPermission("opsecurity." + subCommand)) suggestions.add("<rank>");
            } else if (subCommand.equals("check") || subCommand.equals("reset")) {
                if (player.hasPermission("opsecurity." + subCommand)) suggestions.add("<player>");
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

    public void clearPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        loginGUIs.remove(uuid);
        passwordInputs.remove(uuid);
    }

    private Material getCompatibleMaterial(String modern, String legacy) {
        try { return Material.valueOf(modern); } catch (IllegalArgumentException e) { return Material.valueOf(legacy); }
    }

    private Sound getCompatibleSound(String modern, String legacy) {
        try { return Sound.valueOf(modern); } catch (IllegalArgumentException e) { return Sound.valueOf(legacy); }
    }
}