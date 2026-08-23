# Female-Gender-Mod-Plugin

A Paper / Spigot plugin that syncs player gender data between clients using Wildfire's Female Gender Mod and the server.

Supports MC 26.2+ with mod sync protocol v2 (mod 5.0.0-Beta.4+).

Replaces the old Fabric-only sync on Spigot/Paper servers, with full cross-server support via Velocity or BungeeCord proxies.

## Installation

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Minecraft Server | Paper 26.2+ (or Spigot 1.21.1+) |
| Java | 25+ |
| Female Gender Mod (client) | 5.0.0-Beta.4+ |

### Single Server Setup

1. Download the latest JAR from the Releases page
2. Place `Female-Gender-Mod-Plugin-1.6.0.jar` into your server's `plugins/` directory
3. Restart the server
4. Done! Players with the mod installed will now have their data synced automatically

### Cross-Server Setup (Velocity / BungeeCord)

#### Step 1: Install on each Paper/Spigot backend

Install `Female-Gender-Mod-Plugin-1.6.0.jar` on every backend server.

#### Step 2: Install on the Proxy

**For Velocity:**

1. Download the JAR (same file) to your Velocity plugins directory
2. The plugin registers itself as a Velocity plugin via `@Plugin` annotation
3. Start Velocity - the plugin will automatically handle cross-server forwarding

**For BungeeCord:**

The Paper plugin uses the standard `BungeeCord` plugin messaging channel, which BungeeCord natively supports. No additional plugin needed on BungeeCord.

#### Step 3: Configure Cross-Server

Each backend server needs to be registered in the proxy config as a distinct server. The plugin uses `RegisteredServer.getServerInfo().getName()` to identify which server a message came from and where to forward it.

### Client-Side

Players must have Wildfire's Female Gender Mod 5.0.0-Beta.4+ installed on their client.

## Build Instructions

```bash
git clone https://github.com/dbrighthd/gendermodspigot.git
cd gendermodspigot
gradlew.bat build
```

The built JAR will be at `build/libs/Female-Gender-Mod-Plugin-1.6.0.jar`.

## License

This project is a community plugin and is not affiliated with Wildfire's Female Gender Mod.
The mod is required on the client side for this plugin to function.