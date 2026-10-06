# Female Gender Mod — Paper / Velocity Bridge

A Paper server companion for Female Gender Mod with **dual official wire-protocol support**. The compile matrix covers Paper 26.2 and 26.3; runtime support still depends on the installed Paper build and client/server protocol compatibility.

- **Protocol 1:** Female Gender Mod 5.0.0-Beta.4 on MC 26.2 (`wildfire_gender`, play-phase hello).
- **Protocol 2:** Female Gender Mod 5.0.0-Beta.5+ / 5.0.0 on MC 26.3 (`female_gender_mod`, configuration-phase hello and compact profile).

The plugin uses Paper's configuration-stage `PlayerConnection` plugin-message overload for configuration-stage handshakes and Paper's entity-tracking API for scoped delivery. It deliberately does not support Spigot or BungeeCord.

## Downloads

Each release has two independent plugin JARs. **Do not install the same JAR on both platforms.**

| File | Install location | Purpose |
|---|---|---|
| **Female-Gender-Mod-Paper-<version>.jar** | Every Paper backend's plugins directory | Receives and delivers protocol 1 or 2 profiles |
| **Female-Gender-Mod-Velocity-<version>.jar** | Velocity proxy's plugins directory | Optional cross-server handoff on the matching protocol channel |

Source JARs are for developers only.

## Requirements

| Component | Required version |
|---|---|
| Backend | Paper 26.2+ (CI compiles against Paper 26.2 and 26.3) |
| Paper Java | 25+ |
| Velocity bridge bytecode | Java 17 compatible (class major 61) |
| Protocol 1 client | Female Gender Mod 5.0.0-Beta.4 for MC 26.2 |
| Protocol 2 client | Female Gender Mod 5.0.0-Beta.5 or 5.0.0 for MC 26.3 |
| Proxy (optional) | Velocity 4.1.0+ |

The build itself can be launched with Java 21 while Gradle selects the Java 25 Paper toolchain. CI builds both Paper API baselines, tests the complete suite, and checks the two produced JARs.

## Installation

### Single Paper server

1. Download **Female-Gender-Mod-Paper-<version>.jar** from [Releases](https://github.com/EllanStudio/gendermodspigot/releases).
2. Put it in the Paper server's plugins directory.
3. Restart Paper.
4. Players with either supported client release negotiate their protocol automatically when the installed Paper/Minecraft runtime accepts that client version.

### Velocity network

1. Install **Female-Gender-Mod-Paper-<version>.jar** in **every** Paper backend's plugins directory.
2. Install **Female-Gender-Mod-Velocity-<version>.jar** only in Velocity's plugins directory.
3. On first proxy startup, edit **plugins/female-gender-velocity/config.properties** and set **shared-secret** to a long random value.
4. Set the identical value at **velocity.shared-secret** in every Paper plugin's config.yml.
5. Restart the proxy and all backends.

The bridge rejects unsigned traffic, accepts messages only from backend connections, and keeps protocol 1 and protocol 2 profile caches on separate private channels.

## Synchronisation behaviour

- Protocol 1 channels remain unchanged: `wildfire_gender:serverbound/hello`, `wildfire_gender:clientbound/hello`, `wildfire_gender:send_gender_info`, and `wildfire_gender:sync`.
- Protocol 2 channels are `female_gender_mod:serverbound/hello`, `female_gender_mod:clientbound/hello`, `female_gender_mod:serverbound/sync`, and `female_gender_mod:clientbound/sync`.
- Protocol 2's configuration hello advertises a one-item supported-version list [2]; the client responds with VarInt 2 before play begins.
- Protocol 2 serverbound packets contain compact AvatarConfig only; clientbound packets contain UUID followed by that compact config. Male configs are represented by a single false/present flag. Physics fields are omitted when disabled.
- A config change is sent once to each player currently tracking that entity, and the source client is excluded because it already owns its local configuration.
- Joining players receive one one-shot initial handoff plus their matching protocol's optional Velocity profile request. There is no polling loop, all-online broadcast, debounce timer, or custom batch frame.

## Build

~~~powershell
$env:JAVA_HOME = 'D:/graalvm-jdk-25.0.2+10.1' # or another Java 25 JDK
# Paper classes compile to Java 25; the Velocity source set is emitted as Java 17 bytecode.
./gradlew.bat clean build
~~~

Artifacts:

~~~text
build/libs/Female-Gender-Mod-Paper-<version>.jar
build/libs/Female-Gender-Mod-Paper-<version>-sources.jar
build/libs/Female-Gender-Mod-Velocity-<version>.jar
build/libs/Female-Gender-Mod-Velocity-<version>-sources.jar
~~~

## License

This is a community plugin and is not affiliated with Female Gender Mod. Female Gender Mod remains required on clients for synchronization to work.