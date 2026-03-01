# 🛠️ StaffHelp - Advanced Staff Management Plugin

## 📋 Overview
StaffHelp is a comprehensive ticket management system for Minecraft Spigot/Paper servers (1.20+). It allows players to request help through different priority levels while providing staff members with intuitive GUI menus and real-time compass tracking.

---

## ✨ Features

### 🎫 Three Ticket Types
| Type | Command | Description | Staff Action |
|------|---------|-------------|--------------|
| **SOS** | `/sos <reason>` | General help request | Teleport to player |
| **EMERGENCY** | `/911 <reason>` | High-priority emergency | Get compass to location |
| **QRR** | `/qrr` | Quick response request | Real-time player tracking |

### 🎮 Staff Features
- **Interactive GUI Menus** with player heads
- **Real-time alerts** when players request help
- **Automatic compass tracking** for EMERGENCY and QRR
- **One-click teleport** for SOS tickets
- **Compass auto-removal** when destination reached
- **Multi-world support** with cross-dimension tracking

### ⚡ Technical Features
- ✅ Fully asynchronous operations (zero lag)
- ✅ Thread-safe data structures
- ✅ World caching for performance
- ✅ Auto cleanup of old data
- ✅ Configurable messages
- ✅ Reload command with `/staffhelp reload`
- ✅ Data persistence in `data.dat`

---

## 📁 File Structure

plugins/
└── StaffHelp/
├── config.yml # Message and color configuration
└── data.dat # Ticket storage (auto-generated)


---

## 🔧 Installation

1. **Download** the StaffHelp.jar
2. **Place** in your server's `plugins/` folder
3. **Restart** your server or use `/reload confirm`
4. **Configure** `plugins/StaffHelp/config.yml` (optional)
5. **Enjoy!** The plugin is ready to use

---

## 📝 Commands

### Player Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/sos <reason>` | `staffhelp.player.sos` | Request help from staff |
| `/911 <reason>` | `staffhelp.player.911` | Send emergency alert |
| `/qrr` | `staffhelp.player.qrr` | Request quick response |

### Staff Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/staff` | `staffhelp.staff.menu` | Open SOS tickets menu |
| `/911gui` | `staffhelp.staff.menu` | Open EMERGENCY tickets menu |
| `/qrrmenu` | `staffhelp.staff.menu` | Open QRR tickets menu |
| `/staffhelp reload` | `staffhelp.staff.reload` | Reload configuration |

---

## 🔐 Permissions

### Staff Permissions

staffhelp.staff:
description: Full staff access
default: op
children:
staffhelp.staff.sos: true
staffhelp.staff.911: true
staffhelp.staff.qrr: true
staffhelp.staff.menu: true
staffhelp.staff.reload: true

### Player Permissions

staffhelp.player:
description: Basic player permissions
default: true
children:
staffhelp.player.sos: true
staffhelp.player.qrr: true
staffhelp.player.911: true


---

## ⚙️ Configuration (config.yml)

```yaml
# StaffHelp Configuration
messages:
  prefix: "&8[&bStaffHelp&8]"
  no-permission: "&cYou don't have permission to use this command!"
  
  # SOS Messages
  sos-sent: "&aYour help request has been sent to the staff team!"
  sos-alert: "&b%player% &7needs help: &f%reason%"
  sos-answered: "&aYou answered &b%player%'s &acall!"
  sos-gui-title: "&8SOS Tickets - Click to attend"
  
  # 911 Emergency Messages
  911-sent: "&aYour emergency request has been sent!"
  911-alert: "&c&lEMERGENCY! &r&c%player% &7needs help: &f%reason%"
  911-gui-title: "&4&lEMERGENCY 911"
  911-compass-name: "&c&lEmergency Compass &7- %player%"
  911-compass-lore: "&7Follow this compass to &c%player%"
  
  # QRR Messages
  qrr-sent: "&aQRR sent! Staff will track your location"
  qrr-alert: "&e&lQRR! &e%player% &7needs assistance"
  qrr-gui-title: "&e&lQRR - Assistance Requests"
  qrr-compass-name: "&e&lQRR Compass &7- %player%"
  qrr-compass-lore: "&7Follow this compass to &e%player%"
  
  # Reload Message
  reload-success: "&aConfiguration reloaded successfully!"

colors:
  primary: "&b"
  secondary: "&7"

  ```

  🎯 How It Works
🔴 SOS Tickets
Player types /sos I need help!

Staff receive alert with reason and location

Staff opens /staff menu

Click on player head → Instant teleport

Ticket automatically removed

🟡 EMERGENCY 911
Player types /911 Fire at spawn!

Staff receive urgent alert

Staff opens /911gui

Click on player head → Receives compass

Compass points to location (updates every 2 sec)

Compass auto-removes within 5 blocks

🟢 QRR (Quick Response)
Player types /qrr

Staff receive alert

Staff opens /qrrmenu

Click on player head → Receives tracking compass

Compass follows the player in real-time

Auto-removes when within 5 blocks

🚀 Performance Optimizations
✅ Async file operations - No server lag

✅ Thread-safe maps - ConcurrentHashMap

✅ World caching - Reduces lookups

✅ Optimized compass updates - Every 2 seconds

✅ Auto cache cleanup - Every 5 minutes

✅ Lightweight - Minimal memory usage


📊 Data Storage
Tickets are stored in plugins/StaffHelp/data.dat:
```
sos:
  player-uuid-1:
    player: "PlayerName"
    reason: "Help reason"
    world: "world"
    x: 100.0
    y: 64.0
    z: 200.0
    timestamp: 123456789
911:
  # Similar structure
qrr:
  # Similar structure

  ```

  Thanks for downloading!

  :D
