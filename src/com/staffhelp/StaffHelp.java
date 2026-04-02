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
    private final Map<UUID, UUID> trackingTargetPlayers = new ConcurrentHashMap<>();
    
    // Cache de mundos para evitar lookups repetidos
    private final Map<String, World> worldCache = new ConcurrentHashMap<>();
    
    // ================== CLASSES INTERNAS ==================
    
    public enum TipoOcorrencia {
        SOS, EMERGENCY, QRR, MEDIC, EMERGENCY912, POLICE190, MECHANIC, SUPPORT, HELPER, TAXI, EMERGENCY918, EMERGENCY900, EMERGENCY920, EMERGENCY910
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
            this.location = location.clone();
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
        private final UUID targetPlayerId;
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
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.sos-gui-title", "&8SOS Tickets")));
                    
                    if (dataConfig.contains("sos")) {
                        for (String key : dataConfig.getConfigurationSection("sos").getKeys(false)) {
                            addRequestToInventory(inv, key, "sos", "SOS");
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
        
        public void open911Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.911-gui-title", "&4&lEMERGENCY 911")));
                    
                    if (dataConfig.contains("911")) {
                        for (String key : dataConfig.getConfigurationSection("911").getKeys(false)) {
                            addRequestToInventory(inv, key, "911", "911");
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
                            addRequestToInventory(inv, key, "qrr", "QRR");
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
        
        public void openMedicMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.medic-gui-title", "&c&lMEDIC - Emergency")));
                    
                    if (dataConfig.contains("medic")) {
                        for (String key : dataConfig.getConfigurationSection("medic").getKeys(false)) {
                            addRequestToInventory(inv, key, "medic", "MEDIC");
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
        
        public void open912Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.912-gui-title", "&c&lEMERGENCY 912")));
                    
                    if (dataConfig.contains("912")) {
                        for (String key : dataConfig.getConfigurationSection("912").getKeys(false)) {
                            addRequestToInventory(inv, key, "912", "912");
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
        
        public void open190Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.190-gui-title", "&9&lPOLICE 190")));
                    
                    if (dataConfig.contains("190")) {
                        for (String key : dataConfig.getConfigurationSection("190").getKeys(false)) {
                            addRequestToInventory(inv, key, "190", "190");
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
        
        public void openMecanicMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.mecanic-gui-title", "&7&lMECHANIC - Assistance")));
                    
                    if (dataConfig.contains("mecanic")) {
                        for (String key : dataConfig.getConfigurationSection("mecanic").getKeys(false)) {
                            addRequestToInventory(inv, key, "mecanic", "MECANIC");
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
        
        public void openSupportMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.support-gui-title", "&a&lSUPPORT - Help")));
                    
                    if (dataConfig.contains("support")) {
                        for (String key : dataConfig.getConfigurationSection("support").getKeys(false)) {
                            addRequestToInventory(inv, key, "support", "SUPPORT");
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
        
        public void openHelperMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.helper-gui-title", "&d&lHELPER - Assistance")));
                    
                    if (dataConfig.contains("helper")) {
                        for (String key : dataConfig.getConfigurationSection("helper").getKeys(false)) {
                            addRequestToInventory(inv, key, "helper", "HELPER");
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
        
        public void openTaxiMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.taxi-gui-title", "&e&lTAXI - Transport")));
                    
                    if (dataConfig.contains("taxi")) {
                        for (String key : dataConfig.getConfigurationSection("taxi").getKeys(false)) {
                            addRequestToInventory(inv, key, "taxi", "TAXI");
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
        
        public void open918Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.918-gui-title", "&4&lEMERGENCY 918")));
                    
                    if (dataConfig.contains("918")) {
                        for (String key : dataConfig.getConfigurationSection("918").getKeys(false)) {
                            addRequestToInventory(inv, key, "918", "918");
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
        
        public void open900Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.900-gui-title", "&c&lEMERGENCY 900")));
                    
                    if (dataConfig.contains("900")) {
                        for (String key : dataConfig.getConfigurationSection("900").getKeys(false)) {
                            addRequestToInventory(inv, key, "900", "900");
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
        
        public void open920Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.920-gui-title", "&c&lEMERGENCY 920")));
                    
                    if (dataConfig.contains("920")) {
                        for (String key : dataConfig.getConfigurationSection("920").getKeys(false)) {
                            addRequestToInventory(inv, key, "920", "920");
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
        
        public void open910Menu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.910-gui-title", "&c&lEMERGENCY 910")));
                    
                    if (dataConfig.contains("910")) {
                        for (String key : dataConfig.getConfigurationSection("910").getKeys(false)) {
                            addRequestToInventory(inv, key, "910", "910");
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
        
        private void addRequestToInventory(Inventory inv, String key, String dataSection, String displayType) {
            String path = dataSection + "." + key;
            String playerName = dataConfig.getString(path + ".player");
            String reason = dataConfig.getString(path + ".reason", "No reason provided");
            String world = dataConfig.getString(path + ".world");
            double x = dataConfig.getDouble(path + ".x");
            double y = dataConfig.getDouble(path + ".y");
            double z = dataConfig.getDouble(path + ".z");
            
            if (playerName == null || world == null) return;
            
            World bukkitWorld = getCachedWorld(world);
            if (bukkitWorld == null) return;
            
            ItemStack head = createPlayerHead(playerName, reason, world, x, y, z, displayType);
            if (head != null) {
                inv.addItem(head);
            }
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
                    case "MEDIC":
                        meta.setDisplayName(colorize("&c&l[MEDIC] &f" + playerName));
                        break;
                    case "912":
                        meta.setDisplayName(colorize("&c&l[912] &f" + playerName));
                        break;
                    case "190":
                        meta.setDisplayName(colorize("&9&l[190] &f" + playerName));
                        break;
                    case "MECANIC":
                        meta.setDisplayName(colorize("&7&l[MECANIC] &f" + playerName));
                        break;
                    case "SUPPORT":
                        meta.setDisplayName(colorize("&a&l[SUPPORT] &f" + playerName));
                        break;
                    case "HELPER":
                        meta.setDisplayName(colorize("&d&l[HELPER] &f" + playerName));
                        break;
                    case "TAXI":
                        meta.setDisplayName(colorize("&e&l[TAXI] &f" + playerName));
                        break;
                    case "918":
                        meta.setDisplayName(colorize("&4&l[918] &f" + playerName));
                        break;
                    case "900":
                        meta.setDisplayName(colorize("&c&l[900] &f" + playerName));
                        break;
                    case "920":
                        meta.setDisplayName(colorize("&c&l[920] &f" + playerName));
                        break;
                    case "910":
                        meta.setDisplayName(colorize("&c&l[910] &f" + playerName));
                        break;
                }
                
                List<String> lore = new ArrayList<>();
                if (reason != null && !type.equals("QRR") && !type.equals("TAXI")) {
                    lore.add(colorize("&7Reason: &f" + reason));
                }
                lore.add(colorize("&7Location: &f" + world + " " + (int)x + " " + (int)y + " " + (int)z));
                lore.add("");
                lore.add(colorize("&aClick to receive compass"));
                
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
            new BukkitRunnable() {
                @Override
                public void run() {
                    String permission = getPermissionForType(ocorrencia.getType());
                    String message = "";
                    String location = "";
                    String instruction = "";
                    
                    switch (ocorrencia.getType()) {
                        case SOS:
                            message = colorize("&8[&bStaffHelp&8] &b" + ocorrencia.getPlayerName() + 
                                      " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /staff to open the menu and attend!");
                            break;
                            
                        case EMERGENCY:
                            message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY! &r&c" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /911gui to open the menu and get the compass!");
                            break;
                            
                        case QRR:
                            message = colorize("&8[&bStaffHelp&8] &e&lQRR! &e" + 
                                      ocorrencia.getPlayerName() + " &7needs assistance");
                            instruction = colorize("&aUse /qrrmenu to open the menu and get the compass!");
                            break;
                            
                        case MEDIC:
                            message = colorize("&8[&bStaffHelp&8] &c&lMEDIC! &c" + 
                                      ocorrencia.getPlayerName() + " &7needs medical assistance: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /medicgui to open the menu and get the compass!");
                            break;
                            
                        case EMERGENCY912:
                            message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY 912! &c" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /912gui to open the menu and get the compass!");
                            break;
                            
                        case POLICE190:
                            message = colorize("&8[&bStaffHelp&8] &9&lPOLICE 190! &9" + 
                                      ocorrencia.getPlayerName() + " &7needs police assistance: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /190gui to open the menu and get the compass!");
                            break;
                            
                        case MECHANIC:
                            message = colorize("&8[&bStaffHelp&8] &7&lMECHANIC! &7" + 
                                      ocorrencia.getPlayerName() + " &7needs mechanical assistance: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /mecanicgui to open the menu and get the compass!");
                            break;
                            
                        case SUPPORT:
                            message = colorize("&8[&bStaffHelp&8] &a&lSUPPORT! &a" + 
                                      ocorrencia.getPlayerName() + " &7needs support: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /supportgui to open the menu and get the compass!");
                            break;
                            
                        case HELPER:
                            message = colorize("&8[&bStaffHelp&8] &d&lHELPER! &d" + 
                                      ocorrencia.getPlayerName() + " &7needs assistance: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /helpergui to open the menu and get the compass!");
                            break;
                            
                        case TAXI:
                            message = colorize("&8[&bStaffHelp&8] &e&lTAXI! &e" + 
                                      ocorrencia.getPlayerName() + " &7needs a taxi");
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /taxigui to open the menu and get the compass!");
                            break;
                            
                        case EMERGENCY918:
                            message = colorize("&8[&bStaffHelp&8] &4&lEMERGENCY 918! &4" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /918gui to open the menu and get the compass!");
                            break;
                            
                        case EMERGENCY900:
                            message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY 900! &c" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /900gui to open the menu and get the compass!");
                            break;
                            
                        case EMERGENCY920:
                            message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY 920! &c" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /920gui to open the menu and get the compass!");
                            break;
                            
                        case EMERGENCY910:
                            message = colorize("&8[&bStaffHelp&8] &c&lEMERGENCY 910! &c" + 
                                      ocorrencia.getPlayerName() + " &7needs help: &f" + ocorrencia.getReason());
                            location = getLocationString(ocorrencia);
                            instruction = colorize("&aUse /910gui to open the menu and get the compass!");
                            break;
                    }
                    
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission(permission)) {
                            sendStaffMessage(staff, message, location, instruction);
                        }
                    }
                }
            }.runTaskAsynchronously(StaffHelp.this);
        }
        
        private String getPermissionForType(TipoOcorrencia type) {
            switch (type) {
                case SOS:
                    return "staffhelp.staff.sos";
                case EMERGENCY:
                    return "staffhelp.staff.911";
                case QRR:
                    return "staffhelp.staff.qrr";
                case MEDIC:
                    return "staffhelp.staff.medic";
                case EMERGENCY912:
                    return "staffhelp.staff.912";
                case POLICE190:
                    return "staffhelp.staff.190";
                case MECHANIC:
                    return "staffhelp.staff.mecanic";
                case SUPPORT:
                    return "staffhelp.staff.support";
                case HELPER:
                    return "staffhelp.staff.helper";
                case TAXI:
                    return "staffhelp.staff.taxi";
                case EMERGENCY918:
                    return "staffhelp.staff.918";
                case EMERGENCY900:
                    return "staffhelp.staff.900";
                case EMERGENCY920:
                    return "staffhelp.staff.920";
                case EMERGENCY910:
                    return "staffhelp.staff.910";
                default:
                    return "staffhelp.staff";
            }
        }
        
        private String getLocationString(Ocorrencia ocorrencia) {
            if (ocorrencia.getLocation() != null && ocorrencia.getLocation().getWorld() != null) {
                return colorize("&7Location: &f" + 
                    ocorrencia.getLocation().getWorld().getName() + " " +
                    (int)ocorrencia.getLocation().getX() + " " +
                    (int)ocorrencia.getLocation().getY() + " " +
                    (int)ocorrencia.getLocation().getZ());
            }
            return "";
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
            
            if (!player.hasPermission("staffhelp.staff.sos")) {
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
                    saveOcorrenciaToConfig(path, player, reason);
                    
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
                    saveOcorrenciaToConfig(path, player, reason);
                    
                    player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.911-sent", "&aEmergency request sent!")));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            
            return true;
        }
    }
    
    public class MedicCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.medic")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Medical assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.MEDIC
                    );
                    
                    saveOcorrenciaToConfig("medic." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&cMedical assistance request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class NineOneTwoCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.912")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Emergency assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.EMERGENCY912
                    );
                    
                    saveOcorrenciaToConfig("912." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&cEmergency 912 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class OneNineZeroCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.190")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Police assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.POLICE190
                    );
                    
                    saveOcorrenciaToConfig("190." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&9Police 190 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class MecanicCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.mecanic")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Mechanical assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.MECHANIC
                    );
                    
                    saveOcorrenciaToConfig("mecanic." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&7Mechanical assistance request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class SupportCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.support")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Support needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.SUPPORT
                    );
                    
                    saveOcorrenciaToConfig("support." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&aSupport request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class HelperCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.helper")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Helper assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.HELPER
                    );
                    
                    saveOcorrenciaToConfig("helper." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&dHelper assistance request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class TaxiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.taxi")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), "Taxi request",
                        player.getLocation(), TipoOcorrencia.TAXI
                    );
                    
                    saveOcorrenciaToConfig("taxi." + player.getUniqueId().toString(), player, "Taxi request");
                    player.sendMessage(colorize(getPrefix() + "&eTaxi request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class NineOneEightCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.918")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Emergency 918 assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.EMERGENCY918
                    );
                    
                    saveOcorrenciaToConfig("918." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&4Emergency 918 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class NineHundredCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.900")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Emergency 900 assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.EMERGENCY900
                    );
                    
                    saveOcorrenciaToConfig("900." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&cEmergency 900 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class NineTwentyCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.920")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Emergency 920 assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.EMERGENCY920
                    );
                    
                    saveOcorrenciaToConfig("920." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&cEmergency 920 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    public class NineTenCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("staffhelp.player.910")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            
            String reason = args.length > 0 ? String.join(" ", args) : "Emergency 910 assistance needed";
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    Ocorrencia ocorrencia = new Ocorrencia(
                        player.getUniqueId(), player.getName(), reason,
                        player.getLocation(), TipoOcorrencia.EMERGENCY910
                    );
                    
                    saveOcorrenciaToConfig("910." + player.getUniqueId().toString(), player, reason);
                    player.sendMessage(colorize(getPrefix() + "&cEmergency 910 request sent!"));
                    new AlertManager().alertStaff(ocorrencia);
                }
            }.runTaskAsynchronously(StaffHelp.this);
            return true;
        }
    }
    
    // GUI Command Executors
    
    public class NineOneOneGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.911")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open911Menu(player);
            return true;
        }
    }
    
    public class QrrMenuCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.qrr")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openQRRMenu(player);
            return true;
        }
    }
    
    public class MedicGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.medic")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openMedicMenu(player);
            return true;
        }
    }
    
    public class NineOneTwoGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.912")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open912Menu(player);
            return true;
        }
    }
    
    public class OneNineZeroGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.190")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open190Menu(player);
            return true;
        }
    }
    
    public class MecanicGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.mecanic")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openMecanicMenu(player);
            return true;
        }
    }
    
    public class SupportGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.support")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openSupportMenu(player);
            return true;
        }
    }
    
    public class HelperGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.helper")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openHelperMenu(player);
            return true;
        }
    }
    
    public class TaxiGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.taxi")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().openTaxiMenu(player);
            return true;
        }
    }
    
    public class NineOneEightGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.918")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open918Menu(player);
            return true;
        }
    }
    
    public class NineHundredGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.900")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open900Menu(player);
            return true;
        }
    }
    
    public class NineTwentyGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.920")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open920Menu(player);
            return true;
        }
    }
    
    public class NineTenGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("staffhelp.staff.910")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                return true;
            }
            new MenuManager().open910Menu(player);
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
    
    public class StaffHelpCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("staffhelp.staff.reload")) {
                    sender.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", "&cNo permission")));
                    return true;
                }
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        reloadConfig();
                        loadConfig();
                        reloadData();
                        worldCache.clear();
                        sender.sendMessage(colorize(getPrefix() + getConfig().getString("messages.reload-success", "&aConfiguration reloaded!")));
                    }
                }.runTaskAsynchronously(StaffHelp.this);
                
                return true;
            }
            
            sender.sendMessage(colorize("&e=== StaffHelp v1.5 ==="));
            sender.sendMessage(colorize("&7/staff &f- Open SOS tickets menu (SOS)"));
            sender.sendMessage(colorize("&7/sos <reason> &f- Request SOS help"));
            sender.sendMessage(colorize("&7/911 <reason> &f- Emergency 911"));
            sender.sendMessage(colorize("&7/911gui &f- Emergency 911 menu"));
            sender.sendMessage(colorize("&7/qrr &f- QRR request"));
            sender.sendMessage(colorize("&7/qrrmenu &f- QRR menu"));
            sender.sendMessage(colorize("&7/medic <reason> &f- Medical assistance"));
            sender.sendMessage(colorize("&7/medicgui &f- Medical menu"));
            sender.sendMessage(colorize("&7/912 <reason> &f- Emergency 912"));
            sender.sendMessage(colorize("&7/912gui &f- Emergency 912 menu"));
            sender.sendMessage(colorize("&7/190 <reason> &f- Police 190"));
            sender.sendMessage(colorize("&7/190gui &f- Police menu"));
            sender.sendMessage(colorize("&7/mecanic <reason> &f- Mechanic"));
            sender.sendMessage(colorize("&7/mecanicgui &f- Mechanic menu"));
            sender.sendMessage(colorize("&7/support <reason> &f- Support"));
            sender.sendMessage(colorize("&7/supportgui &f- Support menu"));
            sender.sendMessage(colorize("&7/helper <reason> &f- Helper"));
            sender.sendMessage(colorize("&7/helpergui &f- Helper menu"));
            sender.sendMessage(colorize("&7/taxi &f- Taxi request"));
            sender.sendMessage(colorize("&7/taxigui &f- Taxi menu"));
            sender.sendMessage(colorize("&7/918 <reason> &f- Emergency 918"));
            sender.sendMessage(colorize("&7/918gui &f- Emergency 918 menu"));
            sender.sendMessage(colorize("&7/900 <reason> &f- Emergency 900"));
            sender.sendMessage(colorize("&7/900gui &f- Emergency 900 menu"));
            sender.sendMessage(colorize("&7/920 <reason> &f- Emergency 920"));
            sender.sendMessage(colorize("&7/920gui &f- Emergency 920 menu"));
            sender.sendMessage(colorize("&7/910 <reason> &f- Emergency 910"));
            sender.sendMessage(colorize("&7/910gui &f- Emergency 910 menu"));
            sender.sendMessage(colorize("&7/staffhelp reload &f- Reload config"));
            return true;
        }
    }
    
    private void saveOcorrenciaToConfig(String path, Player player, String reason) {
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
        String medicTitle = colorize(getConfig().getString("messages.medic-gui-title", "&c&lMEDIC - Emergency"));
        String title912 = colorize(getConfig().getString("messages.912-gui-title", "&c&lEMERGENCY 912"));
        String title190 = colorize(getConfig().getString("messages.190-gui-title", "&9&lPOLICE 190"));
        String mecanicTitle = colorize(getConfig().getString("messages.mecanic-gui-title", "&7&lMECHANIC - Assistance"));
        String supportTitle = colorize(getConfig().getString("messages.support-gui-title", "&a&lSUPPORT - Help"));
        String helperTitle = colorize(getConfig().getString("messages.helper-gui-title", "&d&lHELPER - Assistance"));
        String taxiTitle = colorize(getConfig().getString("messages.taxi-gui-title", "&e&lTAXI - Transport"));
        String title918 = colorize(getConfig().getString("messages.918-gui-title", "&4&lEMERGENCY 918"));
        String title900 = colorize(getConfig().getString("messages.900-gui-title", "&c&lEMERGENCY 900"));
        String title920 = colorize(getConfig().getString("messages.920-gui-title", "&c&lEMERGENCY 920"));
        String title910 = colorize(getConfig().getString("messages.910-gui-title", "&c&lEMERGENCY 910"));
        
        if (title.equals(sosTitle) || title.equals(emergencyTitle) || title.equals(qrrTitle) ||
            title.equals(medicTitle) || title.equals(title912) || title.equals(title190) ||
            title.equals(mecanicTitle) || title.equals(supportTitle) || title.equals(helperTitle) ||
            title.equals(taxiTitle) || title.equals(title918) || title.equals(title900) ||
            title.equals(title920) || title.equals(title910)) {
            
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                ItemStack head = event.getCurrentItem();
                if (!head.hasItemMeta() || head.getItemMeta().getDisplayName() == null) return;
                
                String displayName = head.getItemMeta().getDisplayName();
                String playerName = displayName.replaceAll("§[0-9a-fk-or]", "").replaceAll("\\[.*?\\]", "").trim();
                
                String finalPlayerName = playerName;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (title.equals(sosTitle) && dataConfig.contains("sos")) {
                            processClick(player, finalPlayerName, "sos", "teleport");
                        } else if (title.equals(emergencyTitle) && dataConfig.contains("911")) {
                            processClick(player, finalPlayerName, "911", "compass");
                        } else if (title.equals(qrrTitle) && dataConfig.contains("qrr")) {
                            processQRRClick(player, finalPlayerName);
                        } else if (title.equals(medicTitle) && dataConfig.contains("medic")) {
                            processClick(player, finalPlayerName, "medic", "compass");
                        } else if (title.equals(title912) && dataConfig.contains("912")) {
                            processClick(player, finalPlayerName, "912", "compass");
                        } else if (title.equals(title190) && dataConfig.contains("190")) {
                            processClick(player, finalPlayerName, "190", "compass");
                        } else if (title.equals(mecanicTitle) && dataConfig.contains("mecanic")) {
                            processClick(player, finalPlayerName, "mecanic", "compass");
                        } else if (title.equals(supportTitle) && dataConfig.contains("support")) {
                            processClick(player, finalPlayerName, "support", "compass");
                        } else if (title.equals(helperTitle) && dataConfig.contains("helper")) {
                            processClick(player, finalPlayerName, "helper", "compass");
                        } else if (title.equals(taxiTitle) && dataConfig.contains("taxi")) {
                            processClick(player, finalPlayerName, "taxi", "compass");
                        } else if (title.equals(title918) && dataConfig.contains("918")) {
                            processClick(player, finalPlayerName, "918", "compass");
                        } else if (title.equals(title900) && dataConfig.contains("900")) {
                            processClick(player, finalPlayerName, "900", "compass");
                        } else if (title.equals(title920) && dataConfig.contains("920")) {
                            processClick(player, finalPlayerName, "920", "compass");
                        } else if (title.equals(title910) && dataConfig.contains("910")) {
                            processClick(player, finalPlayerName, "910", "compass");
                        }
                    }
                }.runTaskAsynchronously(StaffHelp.this);
            }
        }
    }
    
    private void processClick(Player player, String playerName, String dataSection, String action) {
        for (String key : dataConfig.getConfigurationSection(dataSection).getKeys(false)) {
            String savedPlayer = dataConfig.getString(dataSection + "." + key + ".player");
            if (savedPlayer != null && savedPlayer.equals(playerName)) {
                String world = dataConfig.getString(dataSection + "." + key + ".world");
                if (world == null) continue;
                
                World bukkitWorld = getCachedWorld(world);
                if (bukkitWorld == null) continue;
                
                double x = dataConfig.getDouble(dataSection + "." + key + ".x");
                double y = dataConfig.getDouble(dataSection + "." + key + ".y");
                double z = dataConfig.getDouble(dataSection + "." + key + ".z");
                
                Location loc = new Location(bukkitWorld, x, y, z);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (action.equals("teleport")) {
                            player.teleport(loc);
                            player.sendMessage(colorize(getPrefix() + " &aYou teleported to " + playerName));
                        } else {
                            giveCompass(player, playerName, dataSection, loc, null);
                        }
                        
                        dataConfig.set(dataSection + "." + key, null);
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
                UUID targetPlayerId = UUID.fromString(key);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        giveCompass(player, playerName, "qrr", loc, targetPlayerId);
                        dataConfig.set("qrr." + key, null);
                        saveData();
                        player.closeInventory();
                        player.sendMessage(colorize(getPrefix() + " &aYou are now tracking &e" + playerName + "&a!"));
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
            
            if (target.getTargetPlayerId() != null) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    player.setCompassTarget(targetPlayer.getLocation());
                    
                    if (player.getLocation().distance(targetPlayer.getLocation()) < 5) {
                        removeCompass(player);
                        player.sendMessage(colorize(getPrefix() + " &aYou have found &e" + targetPlayer.getName() + "&a!"));
                    }
                } else if (target.getLocation() != null) {
                    if (player.getLocation().distance(target.getLocation()) < 5) {
                        removeCompass(player);
                        player.sendMessage(colorize(getPrefix() + " &aYou have arrived at the location!"));
                    }
                }
            } else if (target.getLocation() != null) {
                if (player.getLocation().distance(target.getLocation()) < 5) {
                    removeCompass(player);
                    player.sendMessage(colorize(getPrefix() + " &aYou have arrived at the location!"));
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            if (target.getTargetPlayerId() != null) {
                Player targetPlayer = Bukkit.getPlayer(target.getTargetPlayerId());
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    player.setCompassTarget(targetPlayer.getLocation());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            if (target.getTargetPlayerId() != null) {
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
        trackingTargetPlayers.remove(player.getUniqueId());
    }
    
    private void giveCompass(Player player, String targetName, String type, Location targetLoc, UUID targetPlayerId) {
        removeCompass(player);
        
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        
        String compassName = getConfig().getString(type + ".compass-name", "&aCompass - %player%");
        String compassLore = getConfig().getString(type + ".compass-lore", "&7Follow this compass to %player%");
        
        meta.setDisplayName(colorize(compassName.replace("%player%", targetName)));
        meta.setLore(java.util.Arrays.asList(colorize(compassLore.replace("%player%", targetName))));
        
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);
        
        if (targetPlayerId != null) {
            Player targetPlayer = Bukkit.getPlayer(targetPlayerId);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                player.setCompassTarget(targetPlayer.getLocation());
                trackingTargetPlayers.put(player.getUniqueId(), targetPlayerId);
            } else {
                player.setCompassTarget(targetLoc);
            }
        } else {
            player.setCompassTarget(targetLoc);
        }
        
        compassTargets.put(player.getUniqueId(), new CompassTarget(targetLoc, targetName, type, targetPlayerId));
    }
    
    private void removeCompass(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                player.getInventory().setItem(i, null);
            }
        }
        
        Location spawnLoc = player.getBedSpawnLocation();
        if (spawnLoc != null) {
            player.setCompassTarget(spawnLoc);
        } else {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
        
        compassTargets.remove(player.getUniqueId());
        trackingTargetPlayers.remove(player.getUniqueId());
        
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
        
        // Register all commands
        getCommand("staff").setExecutor(new StaffCommand());
        getCommand("sos").setExecutor(new SosCommand());
        getCommand("911").setExecutor(new NineOneOneCommand());
        getCommand("911gui").setExecutor(new NineOneOneGuiCommand());
        getCommand("qrr").setExecutor(new QrrCommand());
        getCommand("qrrmenu").setExecutor(new QrrMenuCommand());
        getCommand("medic").setExecutor(new MedicCommand());
        getCommand("medicgui").setExecutor(new MedicGuiCommand());
        getCommand("912").setExecutor(new NineOneTwoCommand());
        getCommand("912gui").setExecutor(new NineOneTwoGuiCommand());
        getCommand("190").setExecutor(new OneNineZeroCommand());
        getCommand("190gui").setExecutor(new OneNineZeroGuiCommand());
        getCommand("mecanic").setExecutor(new MecanicCommand());
        getCommand("mecanicgui").setExecutor(new MecanicGuiCommand());
        getCommand("support").setExecutor(new SupportCommand());
        getCommand("supportgui").setExecutor(new SupportGuiCommand());
        getCommand("helper").setExecutor(new HelperCommand());
        getCommand("helpergui").setExecutor(new HelperGuiCommand());
        getCommand("taxi").setExecutor(new TaxiCommand());
        getCommand("taxigui").setExecutor(new TaxiGuiCommand());
        getCommand("918").setExecutor(new NineOneEightCommand());
        getCommand("918gui").setExecutor(new NineOneEightGuiCommand());
        getCommand("900").setExecutor(new NineHundredCommand());
        getCommand("900gui").setExecutor(new NineHundredGuiCommand());
        getCommand("920").setExecutor(new NineTwentyCommand());
        getCommand("920gui").setExecutor(new NineTwentyGuiCommand());
        getCommand("910").setExecutor(new NineTenCommand());
        getCommand("910gui").setExecutor(new NineTenGuiCommand());
        getCommand("staffhelp").setExecutor(new StaffHelpCommand());
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Timer to clear cache every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                worldCache.clear();
            }
        }.runTaskTimer(this, 6000L, 6000L);
        
        getLogger().info("StaffHelp v1.5 enabled successfully with all request types and permission-based notifications!");
    }
    
    @Override
    public void onDisable() {
        for (BukkitTask tracker : compassTrackers.values()) {
            tracker.cancel();
        }
        compassTrackers.clear();
        compassTargets.clear();
        trackingTargetPlayers.clear();
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
