package mc.sourcecode54.opSecurity;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class LoginManager implements Listener {
    private final OpSecurity plugin;
    private final ConfigManager configManager;
    private final PermissionHandler permissionHandler;
    private final Map<UUID, Boolean> authenticated = new HashMap<>();
    private final Map<UUID, Inventory> loginGUIs = new HashMap<>();
    private final Map<UUID, StringBuilder> passwordInputs = new HashMap<>();

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
        Inventory gui = Bukkit.createInventory(null, 9, "§3Đăng nhập Staff");
        ItemStack enter = new ItemStack(getCompatibleMaterial("GREEN_WOOL", "LIME_DYE"));
        ItemMeta enterMeta = enter.getItemMeta();
        enterMeta.setDisplayName("§aNhập mật khẩu (Chat để nhập)");
        enter.setItemMeta(enterMeta);

        ItemStack forgot = new ItemStack(getCompatibleMaterial("RED_WOOL", "RED_DYE"));
        ItemMeta forgotMeta = forgot.getItemMeta();
        forgotMeta.setDisplayName("§cQuên mật khẩu");
        forgot.setItemMeta(forgotMeta);

        gui.setItem(3, enter);
        gui.setItem(5, forgot);

        player.openInventory(gui);
        loginGUIs.put(player.getUniqueId(), gui);
        passwordInputs.put(player.getUniqueId(), new StringBuilder());
        authenticated.put(player.getUniqueId(), false);
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
        if (displayName.equals("§aNhập mật khẩu (Chat để nhập)")) {
            player.sendMessage("§eNhập mật khẩu vào chat:");
            player.closeInventory();
        } else if (displayName.equals("§cQuên mật khẩu")) {
            String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
            sendForgotRequest(player, rank);
            player.closeInventory();
        }
    }

    public void sendForgotRequest(Player player, String rank) {
        String message = player.getName() + " (Rank: " + rank + ") yêu cầu reset mật khẩu. Vui lòng xử lý thủ công qua /opsec reset.";
        boolean adminOnline = false;

        for (Player admin : plugin.getServer().getOnlinePlayers()) {
            if (permissionHandler.isStaff(admin)) {
                admin.sendMessage("§e" + message);
                adminOnline = true;
            }
        }
        if (!adminOnline) {
            configManager.addMessageToAdmin("forgot", player.getName(), "Yêu cầu reset mật khẩu", rank);
            player.sendMessage("§eKhông có admin online. Yêu cầu đã được lưu vào hộp thư.");
        }
        configManager.logSecurityEvent(player.getName() + " đã yêu cầu reset mật khẩu với rank " + rank + ".");
        player.sendMessage("§eVui lòng chờ admin xử lý yêu cầu reset mật khẩu qua /opsec reset.");
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
                player.sendMessage("§aĐăng nhập thành công! Rank hiện tại: " + rank);
                configManager.logSecurityEvent(player.getName() + " đã đăng nhập thành công qua GUI với rank " + rank + ".");
                loginGUIs.remove(uuid);
                passwordInputs.remove(uuid);
            } else {
                player.sendMessage("§cMật khẩu sai! Nhập lại:");
                configManager.logSecurityEvent(player.getName() + " đăng nhập thất bại qua GUI (mật khẩu sai).");
                passwordInputs.put(uuid, new StringBuilder());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !isAuthenticated(player)) {
                        openLoginGUI(player);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (loginGUIs.containsKey(uuid) && !isAuthenticated(player)) {
            player.sendMessage("§eVui lòng nhập mật khẩu vào chat hoặc dùng /opsec login!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (permissionHandler.isStaff(player)) {
            String rank = configManager.getDefaultOrValidRank(permissionHandler.getPlayerRank(player));
            List<String> pendingMessages = configManager.getPendingMessages();
            if (!pendingMessages.isEmpty()) {
                player.sendMessage("§eBạn có tin nhắn chưa đọc (Rank: " + rank + "):");
                for (String msg : pendingMessages) {
                    player.sendMessage(msg);
                }
                configManager.clearPendingMessages();
            }
        }
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

}