package com.staffhelp;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffHelp extends JavaPlugin implements Listener {
    
    private static StaffHelp instance;
    private File dataFile;
    private FileConfiguration dataConfig;
    private String prefix;
    
    // Thread-safe maps para evitar problemas de concorrência
    private final Map<UUID, CompassTarget> compassTargets = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> compassTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> qrrTargetPlayers = new ConcurrentHashMap<>(); // staffUUID -> targetPlayerUUID
    
    // Cache de mundos para evitar lookups repetidos
    private final Map<String, World> worldCache = new ConcurrentHashMap<>();
    
    // ================== CLASSES INTERNAS ==================
    
    public enum TipoOcorrencia {
        SOS, EMERGENCY, QRR
    }
    
    public class Ocorrencia {
        private final UUID playerUUID;
        private final String playerName;
        private final String reason;
        private final Location location;
        private final long timestamp;
        private final TipoOcorrencia type;
        
        public Ocorrencia(UUID playerUUID, String playerName, String reason, Location location, TipoOcorrencia type) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.reason = reason;
            this.location = location.clone(); // Clone para segurança
            this.timestamp = System.currentTimeMillis();
            this.type = type;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getReason() { return reason; }
        public Location getLocation() { return location.clone(); }
        public long getTimestamp() { return timestamp; }
        public TipoOcorrencia getType() { return type; }
    }
    
    public class CompassTarget {
        private final Location location;
        private final String targetName;
        private final String type;
        private final UUID targetPlayerId; // Para QRR (rastreamento de jogador)
        private long lastUpdate;
        
        public CompassTarget(Location location, String targetName, String type) {
            this(location, targetName, type, null);
        }
        
        public CompassTarget(Location location, String targetName, String type, UUID targetPlayerId) {
            this.location = location.clone();
            this.targetName = targetName;
            this.type = type;
            this.targetPlayerId = targetPlayerId;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public Location getLocation() { return location.clone(); }
        public String getTargetName() { return targetName; }
        public String getType() { return type; }
        public UUID getTargetPlayerId() { return targetPlayerId; }
        public long getLastUpdate() { return lastUpdate; }
        public void updateLastUpdate() { this.lastUpdate = System.currentTimeMillis(); }
    }
    
    // ================== COLOR UTILS ==================
    
    public String colorize(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }
    
    // ================== MENU MANAGER ==================
    
    public class MenuManager {
        
        public void openSOSMenu(Player player) {
            // Criar inventário async
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.sos-gui-title", "&8SOS Tickets")));
                    
                    if (dataConfig.contains("sos")) {
                        for (String key : dataConfig.getConfigurationSection("sos").getKeys(false)) {
                            String path = "sos." + key;
                            String playerName = dataConfig.getString(path + ".player");
                            String reason = dataConfig.getString(path + ".reason", "No reason provided");
                            String world = dataConfig.getString(path + ".world");
                            double x = dataConfig.getDouble(path + ".x");
                            double y = dataConfig.getDouble(path + ".y");
                            double z = dataConfig.getDouble(path + ".z");
                            
                            if (playerName == null || world == null) continue;
                            
                            World bukkitWorld = getCachedWorld(world);
                            if (bukkitWorld == null) continue;
                            
                            ItemStack head = createPlayerHead(playerName, reason, world, x, y, z, "SOS");
                            if (head != null) {
                                inv.addItem(head);
                            }
                        }
                    }
                    
                    // Voltar para main thread para abrir inventory
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(inv);
                        }
                    }.runTask(StaffHelp.this);
                }
            }.runTaskAsynchronously(StaffHelp.this);
        }
        
        public void open911Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.911-gui-title", "&4&lEMERGENCY 911")));
                    
                    if (dataConfig.contains("911")) {
                        for (String key : dataConfig.getConfigurationSection("911").getKeys(false)) {
                            String path = "911." + key;
                            String playerName = dataConfig.getString(path + ".player");
                            String reason = dataConfig.getString(path + ".reason", "No reason provided");
                            String world = dataConfig.getString(path + ".world");
                            double x = dataConfig.getDouble(path + ".x");
                            double y = dataConfig.getDouble(path + ".y");
                            double z = dataConfig.getDouble(path + ".z");
                            
                            if (playerName == null || world == null) continue;
                            
                            World bukkitWorld = getCachedWorld(world);
                            if (bukkitWorld == null) continue;
                            
                            ItemStack head = createPlayerHead(playerName, reason, world, x, y, z, "911");
                            if (head != null) {
                                inv.addItem(head);
                            }
                        }
                    }
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(inv);
                        }
                    }.runTask(StaffHelp.this);
                }
            }.runTaskAsynchronously(StaffHelp.this);
        }
        
        public void openQRRMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.qrr-gui-title", "&e&lQRR - Assistance")));
                    
                    if (dataConfig.contains("qrr")) {
                        for (String key : dataConfig.getConfigurationSection("qrr").getKeys(false)) {
                            String path = "qrr." + key;
                            String playerName = dataConfig.getString(path + ".player");
                            String world = dataConfig.getString(path + ".world");
                            double x = dataConfig.getDouble(path + ".x");
                            double y = dataConfig.getDouble(path + ".y");
                            double z = dataConfig.getDouble(path + ".z");
                            
                            if (playerName == null || world == null) continue;
                            
                            World bukkitWorld = getCachedWorld(world);
                            if (bukkitWorld == null) continue;
                            
                            ItemStack head = createPlayerHead(playerName, null, world, x, y, z, "QRR");
                            if (head != null) {
                                inv.addItem(head);
                            }
                        }
                    }
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(inv);
                        }
                    }.runTask(StaffHelp.this);
                }
            }.runTaskAsynchronously(StaffHelp.this);
        }
        
        private ItemStack createPlayerHead(String playerName, String reason, String world, double x, double y, double z, String type) {
            try {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                
                switch (type) {
                    case "SOS":
                        meta.setDisplayName(colorize("&b" + playerName));
                        break;
                    case "911":
                        meta.setDisplayName(colorize("&c&l" + playerName));
                        break;
                    case "QRR":
                        meta.setDisplayName(colorize("&e&l" + playerName));
                        break;
                }
                
                List<String> lore = new ArrayList<>();
                if (reason != null && !type.equals("QRR")) {
                    lore.add(colorize("&7Reason: &f" + reason));
                }
                lore.add(colorize("&7Location: &f" + world + " " + (int)x + " " + (int)y + " " + (int)z));
                lore.add("");
                
                switch (type) {
                    case "SOS":
                        lore.add(colorize("&aClick to attend and teleport"));
                        break;
                    case "911":
                    case "QRR":
                        lore.add(colorize("&aClick to receive compass"));
                        break;
                }
                
                meta.setLore(lore);
                head.setItemMeta(meta);
                return head;
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    // ================== ALERT MANAGER ==================
    
    public class AlertManager {
        
        public void alertStaff(Ocorrencia ocorrencia) {
            // Executar alertas async para não travar o servidor
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("staffhelp.staff")) {
                            String message = "";
                            String location = "";
                            
                            switch (ocorrencia.getType()) {
                                case SOS:
                                    message = colorize("&8[&bStaffHelp&8] &b" + ocorrencia.getPlayerName() + 
                                              " &7needs help: &f" + ocorrencia.getReason());
                                    
                                    if (ocorrencia.getLocation() != null && ocorrencia.getLocation().getWorld() != null) {
                                        location = colorize("&7Location: &f" + 
                                            ocorrencia.getLocation().getWorld().getName() + " " +
                                            (int)ocorrencia.getLocation().getX() + " " +
                                            (int)ocorrencia.getLocation().getY() + " " +
                                            (int)ocorrencia.getLocation().getZ());
                                    }
                                    
                                    sendStaffMessage(staff, message, location, 
                                        colorize("&aUse /staff to open the menu and attend!"));
                                    break;
                                    
                                case EMERGENCY:
                                    message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY! &r&c" + 
                                              ocorrencia.getPlayerName() + " &7needs help: &f" + 
                                              ocorrencia.getReason());
                                    
                                    if (ocorrencia.getLocation() != null && ocorrencia.getLocation().getWorld() != null) {
                                        location = colorize("&7Location: &f" + 
                                            ocorrencia.getLocation().getWorld().getName() + " " +
                                            (int)ocorrencia.getLocation().getX() + " " +
                                            (int)ocorrencia.getLocation().getY() + " " +
                                            (int)ocorrencia.getLocation().getZ());
                                    }
                                    
                                    sendStaffMessage(staff, message, location, 
                                        colorize("&aUse /911gui to open the menu and get the compass!"));
                                    break;
                                    
                                case QRR:
                                    message = colorize("&8[&bStaffHelp&8] &e&lQRR! &e" + 
                                              ocorrencia.getPlayerName() + " &7needs assistance");
                                    
                                    sendStaffMessage(staff, message, "", 
                                        colorize("&aUse /qrrmenu to open the menu and get the compass!"));
                                    break;
                            }
                        }
                    }
                }
            }.runTaskAsynchronously(StaffHelp.this);
        }
        
        private void sendStaffMessage(Player staff, String message, String location, String instruction) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    staff.sendMessage(message);
                    if (!location.isEmpty()) {
                        staff.sendMessage(location);
                    }
                    staff.sendMessage(instruction);
                }
            }.runTask(StaffHelp.this);
        }
    }
    
    // ================== COMMAND EXECUTORS ==================
    
    public class StaffCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.staff.menu")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            new MenuManager().openSOSMenu(player);
            return true;
        }
    }
    
    public class SosCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.sos")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            if (args.length == 0) {
                player.sendMessage(colorize(getPrefix() + " &cUse: /sos <reason>"));
                return true;
            }
            
            String reason = String.join(" ", args);
            
            // Salvar dados async
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(),
                        player.getName(),
                        reason,
                        player.getLocation(),
                        TipoOcorrencia.SOS
                    );
                    
                    String path = "sos." + player.getUniqueId().toString();
                    dataConfig.set(path + ".player", player.getName());
                    dataConfig.set(path + ".reason", reason);
                    dataConfig.set(path + ".world", player.getWorld().getName());
                    dataConfig.set(path + ".x", player.getLocation().getX());
                    dataConfig.set(path + ".y", player.getLocation().getY());
                    dataConfig.set(path + ".z", player.getLocation().getZ());
                    dataConfig.set(path + ".timestamp", System.currentTimeMillis());
                    
                    // Salvar em main thread para evitar corrupção
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            saveData();
                        }
                    }.runTask(StaffHelp.this);
                    
                    player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.sos-sent", "&aHelp request sent!")));
                    
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            
            return true;
        }
    }
    
    public class NineOneOneCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.911")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            if (args.length == 0) {
                player.sendMessage(colorize(getPrefix() + " &cUse: /911 <reason>"));
                return true;
            }
            
            String reason = String.join(" ", args);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(),
                        player.getName(),
                        reason,
                        player.getLocation(),
                        TipoOcorrencia.EMERGENCY
                    );
                    
                    String path = "911." + player.getUniqueId().toString();
                    dataConfig.set(path + ".player", player.getName());
                    dataConfig.set(path + ".reason", reason);
                    dataConfig.set(path + ".world", player.getWorld().getName());
                    dataConfig.set(path + ".x", player.getLocation().getX());
                    dataConfig.set(path + ".y", player.getLocation().getY());
                    dataConfig.set(path + ".z", player.getLocation().getZ());
                    dataConfig.set(path + ".timestamp", System.currentTimeMillis());
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            saveData();
                        }
                    }.runTask(StaffHelp.this);
                    
                    player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.911-sent", "&aEmergency request sent!")));
                    
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            
            return true;
        }
    }
    
    public class NineOneOneGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.staff.menu")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            new MenuManager().open911Menu(player);
            return true;
        }
    }
    
    public class QrrCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.qrr")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(),
                        player.getName(),
                        "QRR - Assistance requested",
                        player.getLocation(),
                        TipoOcorrencia.QRR
                    );
                    
                    String path = "qrr." + player.getUniqueId().toString();
                    dataConfig.set(path + ".player", player.getName());
                    dataConfig.set(path + ".reason", "QRR - Assistance requested");
                    dataConfig.set(path + ".world", player.getWorld().getName());
                    dataConfig.set(path + ".x", player.getLocation().getX());
                    dataConfig.set(path + ".y", player.getLocation().getY());
                    dataConfig.set(path + ".z", player.getLocation().getZ());
                    dataConfig.set(path + ".timestamp", System.currentTimeMillis());
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            saveData();
                        }
                    }.runTask(StaffHelp.this);
                    
                    player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.qrr-sent", "&aQRR sent!")));
                    
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            
            return true;
        }
    }
    
    public class QrrMenuCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.staff.menu")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            new MenuManager().openQRRMenu(player);
            return true;
        }
    }
    
    public class StaffHelpCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("staffhelp.staff.reload")) {
                    sender.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                    return true;
                }
                
                // Reload async
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        reloadConfig();
                        loadConfig();
                        reloadData();
                        worldCache.clear(); // Limpar cache de mundos
                        
                        sender.sendMessage(colorize(getPrefix() + getConfig().getString("messages.reload-success", "&aConfiguration reloaded!")));
                    }
                }.runTaskAsynchronously(StaffHelp.this);
                
                return true;
            }
            
            sender.sendMessage(colorize("&e=== StaffHelp v1.0 ==="));
            sender.sendMessage(colorize("&7/staff &f- Open tickets menu"));
            sender.sendMessage(colorize("&7/sos <reason> &f- Request help"));
            sender.sendMessage(colorize("&7/911 <reason> &f- Emergency"));
            sender.sendMessage(colorize("&7/911gui &f- Emergency menu"));
            sender.sendMessage(colorize("&7/qrr &f- QRR"));
            sender.sendMessage(colorize("&7/qrrmenu &f- QRR menu"));
            sender.sendMessage(colorize("&7/staffhelp reload &f- Reload config"));
            return true;
        }
    }
    
    // ================== LISTENER ==================
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        String sosTitle = colorize(getConfig().getString("messages.sos-gui-title", "&8SOS Tickets"));
        String emergencyTitle = colorize(getConfig().getString("messages.911-gui-title", "&4&lEMERGENCY 911"));
        String qrrTitle = colorize(getConfig().getString("messages.qrr-gui-title", "&e&lQRR - Assistance"));
        
        if (title.equals(sosTitle) || title.equals(emergencyTitle) || title.equals(qrrTitle)) {
            
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                ItemStack head = event.getCurrentItem();
                if (!head.hasItemMeta() || head.getItemMeta().getDisplayName() == null) return;
                
                String displayName = head.getItemMeta().getDisplayName();
                String playerName = displayName.replace("§b", "").replace("§c§l", "").replace("§e§l", "").trim();
                
                // Processar clique async
                String finalPlayerName = playerName;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (title.equals(sosTitle) && dataConfig.contains("sos")) {
                            processSOSClick(player, finalPlayerName);
                        } else if (title.equals(emergencyTitle) && dataConfig.contains("911")) {
                            processEmergencyClick(player, finalPlayerName);
                        } else if (title.equals(qrrTitle) && dataConfig.contains("qrr")) {
                            processQRRClick(player, finalPlayerName);
                        }
                    }
                }.runTaskAsynchronously(StaffHelp.this);
            }
        }
    }
    
    private void processSOSClick(Player player, String playerName) {
        for (String key : dataConfig.getConfigurationSection("sos").getKeys(false)) {
            String savedPlayer = dataConfig.getString("sos." + key + ".player");
            if (savedPlayer != null && savedPlayer.equals(playerName)) {
                String world = dataConfig.getString("sos." + key + ".world");
                if (world == null) continue;
                
                World bukkitWorld = getCachedWorld(world);
                if (bukkitWorld == null) continue;
                
                double x = dataConfig.getDouble("sos." + key + ".x");
                double y = dataConfig.getDouble("sos." + key + ".y");
                double z = dataConfig.getDouble("sos." + key + ".z");
                
                Location loc = new Location(bukkitWorld, x, y, z);
                
                // Teleportar na main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(loc);
                        
                        dataConfig.set("sos." + key, null);
                        saveData();
                        
                        player.sendMessage(colorize(getPrefix() + 
                            getConfig().getString("messages.sos-answered", "&aYou answered %player%'s call!").replace("%player%", playerName)));
                        
                        player.closeInventory();
                    }
                }.runTask(StaffHelp.this);
                break;
            }
        }
    }
    
    private void processEmergencyClick(Player player, String playerName) {
        for (String key : dataConfig.getConfigurationSection("911").getKeys(false)) {
            String savedPlayer = dataConfig.getString("911." + key + ".player");
            if (savedPlayer != null && savedPlayer.equals(playerName)) {
                String world = dataConfig.getString("911." + key + ".world");
                if (world == null) continue;
                
                World bukkitWorld = getCachedWorld(world);
                if (bukkitWorld == null) continue;
                
                double x = dataConfig.getDouble("911." + key + ".x");
                double y = dataConfig.getDouble("911." + key + ".y");
                double z = dataConfig.getDouble("911." + key + ".z");
                
                Location loc = new Location(bukkitWorld, x, y, z);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        giveCompass(player, playerName, "911", loc, null);
                        
                        dataConfig.set("911." + key, null);
                        saveData();
                        
                        player.closeInventory();
                    }
                }.runTask(StaffHelp.this);
                break;
            }
        }
    }
    
    private void processQRRClick(Player player, String playerName) {
        for (String key : dataConfig.getConfigurationSection("qrr").getKeys(false)) {
            String savedPlayer = dataConfig.getString("qrr." + key + ".player");
            if (savedPlayer != null && savedPlayer.equals(playerName)) {
                String world = dataConfig.getString("qrr." + key + ".world");
                if (world == null) continue;
                
                World bukkitWorld = getCachedWorld(world);
                if (bukkitWorld == null) continue;
                
                double x = dataConfig.getDouble("qrr." + key + ".x");
                double y = dataConfig.getDouble("qrr." + key + ".y");
                double z = dataConfig.getDouble("qrr." + key + ".z");
                
                Location loc = new Location(bukkitWorld, x, y, z);
                
                // Para QRR, precisamos do UUID do jogador alvo para rastreamento em tempo real
                UUID targetPlayerId = UUID.fromString(key);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        giveCompass(player, playerName, "qrr", loc, targetPlayerId);
                        
                        dataConfig.set("qrr." + key, null);
                        saveData();
                        
                        player.closeInventory();
                        
                        // Mensagem especial para QRR
                        player.sendMessage(colorize(getPrefix() + " &aYou are now tracking &e" + playerName + "&a! The compass will update in real time."));
                    }
                }.runTask(StaffHelp.this);
                break;
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            
            // Verificar se é QRR (rastreamento de jogador)
            if (target.getType().equals("qrr") && target.getTargetPlayerId() != null) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    // Atualizar localização para a posição atual do jogador
                    Location newLoc = targetPlayer.getLocation();
                    player.setCompassTarget(newLoc);
                    
                    // Verificar distância até o jogador
                    if (player.getLocation().distance(newLoc) < 5) {
                        removeCompass(player);
                        player.sendMessage(colorize(getPrefix() + " &aYou have found &e" + targetPlayer.getName() + "&a!"));
                    }
                } else {
                    // Jogador alvo offline, remover bússola
                    removeCompass(player);
                    player.sendMessage(colorize(getPrefix() + " &cThe player you were tracking went offline!"));
                }
            } 
            // Para 911 (localização fixa)
            else if (target.getLocation() != null) {
                if (player.getLocation().distance(target.getLocation()) < 5) {
                    removeCompass(player);
                    player.sendMessage(colorize(getPrefix() + " &aYou have arrived at the location!"));
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Atualizar bússola imediatamente após teleporte
        Player player = event.getPlayer();
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            if (target.getType().equals("qrr") && target.getTargetPlayerId() != null) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    player.setCompassTarget(targetPlayer.getLocation());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Atualizar bússola ao mudar de mundo
        Player player = event.getPlayer();
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            if (target.getType().equals("qrr") && target.getTargetPlayerId() != null) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    player.setCompassTarget(targetPlayer.getLocation());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeCompass(player);
        
        // Limpar referências
        qrrTargetPlayers.remove(player.getUniqueId());
    }
    
    private void giveCompass(Player player, String targetName, String type, Location targetLoc, UUID targetPlayerId) {
        removeCompass(player);
        
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        
        if (type.equals("911")) {
            meta.setDisplayName(colorize(getConfig().getString("messages.911-compass-name", "&c&lEmergency Compass &7- %player%").replace("%player%", targetName)));
            meta.setLore(java.util.Arrays.asList(
                colorize(getConfig().getString("messages.911-compass-lore", "&7Follow this compass to &c%player%").replace("%player%", targetName))
            ));
        } else {
            meta.setDisplayName(colorize(getConfig().getString("messages.qrr-compass-name", "&e&lQRR Compass &7- %player%").replace("%player%", targetName)));
            meta.setLore(java.util.Arrays.asList(
                colorize(getConfig().getString("messages.qrr-compass-lore", "&7Follow this compass to &e%player%").replace("%player%", targetName))
            ));
        }
        
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);
        
        // Para QRR, o alvo é o jogador (não a localização fixa)
        if (type.equals("qrr") && targetPlayerId != null) {
            Player targetPlayer = Bukkit.getPlayer(targetPlayerId);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                player.setCompassTarget(targetPlayer.getLocation());
                qrrTargetPlayers.put(player.getUniqueId(), targetPlayerId);
            } else {
                // Fallback para localização fixa se jogador estiver offline
                player.setCompassTarget(targetLoc);
            }
        } else {
            player.setCompassTarget(targetLoc);
        }
        
        compassTargets.put(player.getUniqueId(), new CompassTarget(targetLoc, targetName, type, targetPlayerId));
        
        // Iniciar tracker otimizado (menos frequente para QRR já que atualizamos no move)
        if (!type.equals("qrr")) {
            startCompassTracker(player, targetLoc, targetName);
        }
    }
    
    private void startCompassTracker(Player player, Location targetLoc, String targetName) {
        if (compassTrackers.containsKey(player.getUniqueId())) {
            compassTrackers.get(player.getUniqueId()).cancel();
        }
        
        BukkitTask tracker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !compassTargets.containsKey(player.getUniqueId())) {
                    this.cancel();
                    compassTrackers.remove(player.getUniqueId());
                    return;
                }
                
                CompassTarget target = compassTargets.get(player.getUniqueId());
                if (target != null && !target.getType().equals("qrr")) {
                    player.setCompassTarget(targetLoc);
                }
            }
        }.runTaskTimer(this, 20L, 40L); // Atualizar a cada 2 segundos (menos frequente para reduzir lag)
        
        compassTrackers.put(player.getUniqueId(), tracker);
    }
    
    private void removeCompass(Player player) {
        // Remover itens de bússola do inventário (otimizado)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.COMPASS) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Resetar o alvo da bússola para o padrão (spawn)
        Location spawnLoc = player.getBedSpawnLocation();
        if (spawnLoc != null) {
            player.setCompassTarget(spawnLoc);
        } else {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
        
        compassTargets.remove(player.getUniqueId());
        qrrTargetPlayers.remove(player.getUniqueId());
        
        if (compassTrackers.containsKey(player.getUniqueId())) {
            compassTrackers.get(player.getUniqueId()).cancel();
            compassTrackers.remove(player.getUniqueId());
        }
    }
    
    private World getCachedWorld(String worldName) {
        return worldCache.computeIfAbsent(worldName, Bukkit::getWorld);
    }
    
    // ================== MAIN METHODS ==================
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        saveDefaultConfig();
        loadConfig();
        
        dataFile = new File(getDataFolder(), "data.dat");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        saveData();
        
        // Registrar comandos
        getCommand("staff").setExecutor(new StaffCommand());
        getCommand("sos").setExecutor(new SosCommand());
        getCommand("911").setExecutor(new NineOneOneCommand());
        getCommand("911gui").setExecutor(new NineOneOneGuiCommand());
        getCommand("qrr").setExecutor(new QrrCommand());
        getCommand("qrrmenu").setExecutor(new QrrMenuCommand());
        getCommand("staffhelp").setExecutor(new StaffHelpCommand());
        
        // Registrar eventos
        getServer().getPluginManager().registerEvents(this, this);
        
        // Timer para limpar cache antigo (a cada 5 minutos)
        new BukkitRunnable() {
            @Override
            public void run() {
                worldCache.clear();
            }
        }.runTaskTimer(this, 6000L, 6000L); // 5 minutos = 6000 ticks
        
        getLogger().info("StaffHelp enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        // Cancelar todas as tasks
        for (BukkitTask tracker : compassTrackers.values()) {
            tracker.cancel();
        }
        compassTrackers.clear();
        compassTargets.clear();
        qrrTargetPlayers.clear();
        worldCache.clear();
        
        saveData();
        getLogger().info("StaffHelp disabled!");
    }
    
    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        
        prefix = colorize(config.getString("messages.prefix", "&8[&bStaffHelp&8]"));
    }
    
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void reloadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public static StaffHelp getInstance() {
        return instance;
    }
}
