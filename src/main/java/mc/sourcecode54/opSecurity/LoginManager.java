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

import java.util.*;

public class LoginManager implements CommandExecutor, TabCompleter, Listener {
    private final OpSecurity plugin; // Plugin chính để truy cập server
    private final ConfigManager configManager; // Truy cập config và file, quan trọng cho staff.yml và hộp thư
    private final PermissionHandler permissionHandler; // Truy cập quyền và rank
    private final Map<UUID, Boolean> authenticated = new HashMap<>(); // Trạng thái đăng nhập của player
    private final Map<UUID, Inventory> loginGUIs = new HashMap<>(); // Lưu GUI đăng nhập
    private final Map<UUID, StringBuilder> passwordInputs = new HashMap<>(); // Lưu mật khẩu đang nhập
    public static final List<String> PLUGIN_COMMANDS = Arrays.asList("opsec"); // Lệnh plugin cho phép khi chưa đăng nhập
    private static final List<String> SUB_COMMANDS = Arrays.asList("register", "login", "forgot", "contactadmin"); // Sub-commands của /opsec

    public LoginManager(OpSecurity plugin, ConfigManager configManager, PermissionHandler permissionHandler) { // Khởi tạo
        this.plugin = plugin;
        this.configManager = configManager;
        this.permissionHandler = permissionHandler;
    }

    public boolean isAuthenticated(Player player) { return authenticated.getOrDefault(player.getUniqueId(), false); } // Kiểm tra đã đăng nhập chưa
    public boolean isRegistered(Player player) { return configManager.getDataConfig().contains(player.getUniqueId().toString() + ".password"); } // Kiểm tra đã đăng ký chưa

    public void openLoginGUI(Player player) { // Mở GUI đăng nhập, quan trọng cho giao diện
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

    public void playLoginEffects(Player player) { // Hiệu ứng khi đăng nhập thành công, quan trọng cho giao diện
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
                    public void run() { fw.detonate(); }
                }.runTaskLater(plugin, 10L);
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) { // Xử lý click GUI, quan trọng cho đăng nhập
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
            for (Player admin : plugin.getServer().getOnlinePlayers()) {
                if (permissionHandler.isStaff(admin)) {
                    admin.sendMessage(ChatColor.YELLOW + player.getName() + " yêu cầu reset mật khẩu!");
                }
            }
            player.sendMessage(ChatColor.GREEN + "Yêu cầu đã được gửi tới admin!");
            configManager.logSecurityEvent(player.getName() + " đã yêu cầu reset mật khẩu qua GUI.");
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) { // Xử lý nhập mật khẩu qua chat, quan trọng cho đăng nhập
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!passwordInputs.containsKey(uuid)) return;
        event.setCancelled(true);
        String message = event.getMessage();
        StringBuilder passwordInput = passwordInputs.get(uuid);
        passwordInput.append(message);
        String storedPassword = configManager.getDataConfig().getString(uuid.toString() + ".password");
        if (passwordInput.toString().equals(storedPassword)) {
            authenticated.put(uuid, true);
            player.sendMessage(ChatColor.GREEN + "Đăng nhập thành công! Rank hiện tại: " + permissionHandler.getPlayerRank(player));
            configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua GUI.");
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
                    if (player.isOnline() && !isAuthenticated(player)) openLoginGUI(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) { // Xử lý khi đóng GUI, quan trọng cho giao diện
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (loginGUIs.containsKey(uuid) && !isAuthenticated(player)) {
            player.sendMessage(ChatColor.YELLOW + "Vui lòng nhập mật khẩu vào chat hoặc dùng /opsec login!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) { // Kiểm tra tin nhắn khi staff join, quan trọng cho hộp thư
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player)) {
            List<String> pendingMessages = configManager.getPendingMessages();
            if (!pendingMessages.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Bạn có tin nhắn chưa đọc:");
                for (String msg : pendingMessages) player.sendMessage(msg);
                configManager.clearPendingMessages();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) { // Xử lý lệnh /opsec
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Chỉ người chơi mới dùng được lệnh này!");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Sử dụng: /opsec [register|login|forgot|contactadmin] [args]");
            return true;
        }
        String subCommand = args[0].toLowerCase();
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
                configManager.getDataConfig().set(player.getUniqueId().toString() + ".password", password);
                configManager.addStaffToYml(player.getName(), "Staff"); // Thêm staff vào staff.yml với rank "Staff", quan trọng cho tối ưu
                configManager.saveFiles();
                player.sendMessage(ChatColor.GREEN + "Đăng ký thành công!");
                authenticated.put(player.getUniqueId(), true);
                configManager.logSecurityEvent(player.getName() + " đã đăng ký thành công.");
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
                    authenticated.put(player.getUniqueId(), true);
                    player.sendMessage(ChatColor.GREEN + "Đăng nhập thành công! Rank hiện tại: " + permissionHandler.getPlayerRank(player));
                    configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua lệnh.");
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
                for (Player admin : plugin.getServer().getOnlinePlayers()) {
                    if (permissionHandler.isStaff(admin)) {
                        admin.sendMessage(ChatColor.YELLOW + player.getName() + " yêu cầu reset mật khẩu!");
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Yêu cầu đã được gửi tới admin!");
                configManager.logSecurityEvent(player.getName() + " đã yêu cầu reset mật khẩu.");
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
                boolean adminOnline = false;
                for (Player admin : plugin.getServer().getOnlinePlayers()) {
                    if (permissionHandler.isStaff(admin)) {
                        admin.sendMessage(ChatColor.YELLOW + "[Liên hệ từ " + player.getName() + "]: " + message);
                        adminOnline = true;
                    }
                }
                if (!adminOnline) { // Lưu tin nhắn vào hộp thư nếu không có admin online, quan trọng cho tối ưu
                    configManager.addMessageToAdmin(player.getName(), message);
                    player.sendMessage(ChatColor.YELLOW + "Không có admin online. Tin nhắn đã được lưu vào hộp thư.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Tin nhắn đã gửi tới admin!");
                }
                configManager.logSecurityEvent(player.getName() + " đã gửi tin nhắn tới admin: " + message);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Lệnh không hợp lệ! Dùng: /opsec [register|login|forgot|contactadmin]");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) { // Gợi ý lệnh
        if (!(sender instanceof Player)) return null;
        Player player = (Player) sender;
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    if (sub.equals("register") && permissionHandler.isStaff(player) && !isRegistered(player)) suggestions.add(sub);
                    else if (sub.equals("login") && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                    else if ((sub.equals("forgot") || sub.equals("contactadmin")) && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("register") && permissionHandler.isStaff(player) && !isRegistered(player)) suggestions.add("<mật khẩu>");
            else if (subCommand.equals("login") && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add("<mật khẩu>");
            else if (subCommand.equals("contactadmin") && permissionHandler.isStaff(player) && isRegistered(player)) suggestions.add("<tin nhắn>");
        }
        return suggestions;
    }

    public void clearPlayerData(Player player) { // Xóa dữ liệu player khi rời server
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        loginGUIs.remove(uuid);
        passwordInputs.remove(uuid);
    }

    private Material getCompatibleMaterial(String modern, String legacy) { // Tương thích material cho 1.8-1.21
        try { return Material.valueOf(modern); } catch (IllegalArgumentException e) { return Material.valueOf(legacy); }
    }

    private Sound getCompatibleSound(String modern, String legacy) { // Tương thích sound cho 1.8-1.21
        try { return Sound.valueOf(modern); } catch (IllegalArgumentException e) { return Sound.valueOf(legacy); }
    }
}