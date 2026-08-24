# Female Gender Mod — Paper / Velocity Bridge

A **Paper-only** server companion for [Female Gender Mod](https://github.com/FemaleGenderMod/FemaleGenderMod), targeting **Minecraft 26.2** and the exact sync protocol **1** used by **Female Gender Mod 5.0.0-Beta.4**.

> This project deliberately does **not** support Spigot or BungeeCord. It uses Paper's entity-tracking API for efficient delivery and a separate Velocity bridge for cross-server profile handoff.

## Downloads

Each release has two independent plugin JARs. **Do not install the same JAR on both platforms.**

| File | Install location | Purpose |
|---|---|---|
| **Female-Gender-Mod-Paper-&lt;version&gt;.jar** | Every Paper backend's plugins directory | Receives and delivers Beta.4 protocol-1 profile packets |
| **Female-Gender-Mod-Velocity-&lt;version&gt;.jar** | Velocity proxy's plugins directory | Optional cross-server profile bridge |

Source JARs are for developers only.

## Requirements

| Component | Required version |
|---|---|
| Backend | Paper 26.2+ |
| Paper Java | 25+ |
| Velocity bridge bytecode | Java 17 compatible (class major 61) |
| Client mod | Female Gender Mod 5.0.0-Beta.4 for MC 26.2 (sync protocol 1) |
| Proxy (optional) | Velocity 4.1.0+ |

## Installation / 安装

### Single Paper server

1. Download **Female-Gender-Mod-Paper-&lt;version&gt;.jar** from [Releases](https://github.com/EllanStudio/gendermodspigot/releases).
2. Put it in the Paper server's plugins directory.
3. Restart Paper.
4. Players with the supported client mod sync automatically; no client-side server configuration is required.

### Velocity network

1. Install **Female-Gender-Mod-Paper-&lt;version&gt;.jar** in **every** Paper backend's plugins directory.
2. Install **Female-Gender-Mod-Velocity-&lt;version&gt;.jar** only in Velocity's plugins directory.
3. On first proxy startup, edit **plugins/female-gender-velocity/config.properties** and set **shared-secret** to a long random value.
4. Set the identical value at **velocity.shared-secret** in every Paper plugin's config.yml.
5. Restart the proxy and all backends.

The bridge ignores unsigned or incorrectly signed traffic, so a modded client cannot impersonate the proxy. The bridge uses the private **wildfire_gender:proxy** plugin channel and does not require BungeeCord compatibility mode or a Bungee plugin.

## Synchronisation behaviour / 同步行为

- The exact Beta.4 wire format is used: both directions contain **UUID plus the complete fixed profile**; there is no compact-MALE flag.
- When a player changes their configuration, the backend sends that profile **once per player currently tracking that entity**.
- The originating client is explicitly excluded: it already knows its own configuration, and the official mod ignores self-profile packets.
- When entity tracking begins, the tracker receives the tracked player's current profile once.
- Beta.4's hello runs in the play phase and advertises protocol **1**. The initial handoff uses one one-shot task; there is **no server tick loop, polling, debounce timer, or custom batch frame**.
- Physical packets are necessarily point-to-point, but delivery is limited to nearby/tracking players instead of all online players.

For a Velocity network, the bridge keeps the latest opaque standard profile in memory and returns it when that player joins another backend. It does not persist profiles across proxy restarts.

## Build

~~~powershell
$env:JAVA_HOME = 'D:/graalvm-jdk-25.0.2+10.1' # or another Java 25 JDK
# Paper classes compile to Java 25; the Velocity source set is emitted as Java 17 bytecode.
./gradlew.bat clean build
~~~

Artifacts:

~~~text
build/libs/Female-Gender-Mod-Paper-&lt;version&gt;.jar
build/libs/Female-Gender-Mod-Paper-&lt;version&gt;-sources.jar
build/libs/Female-Gender-Mod-Velocity-&lt;version&gt;.jar
build/libs/Female-Gender-Mod-Velocity-&lt;version&gt;-sources.jar
~~~

## License

This is a community plugin and is not affiliated with Female Gender Mod. Female Gender Mod remains required on clients for synchronisation to work.
