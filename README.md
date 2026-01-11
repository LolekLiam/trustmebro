# trustmebro - Simple IP‑aware auth for cracked Paper servers

A lightweight Paper (Minecraft) plugin that adds password authentication for offline/cracked servers and auto‑authenticates players when they rejoin from the same IP address.

If a player joins from a new IP, they must login with their password; if they join from their last authenticated IP, they are automatically let in.

> Built for Paper 1.21.x, Kotlin, Java 21.


## Why?
On cracked servers, anyone can spoof a username. If your account is OP or has elevated permissions, an attacker could join as you. `trustmebro` protects accounts by requiring a one‑time password registration and subsequent login when the IP changes.


## Features
- First‑time `/register` to set a password (salted PBKDF2 storage; no plaintext)
- Auto‑authenticate when the player’s current IP equals their last authenticated IP
- Require `/login` only when joining from a different IP
- `/changepassword` command for players to rotate credentials
- Blocks gameplay for unauthenticated players until they register/login:
  - Movement between blocks, chat, inventory actions, item pickup/drop, interactions
  - Only allows `/register`, `/login`, `/changepassword` before auth
- Simple Yaml storage at `plugins/trustmebro/users.yml`
- Clean, programmatic command registration (no YAML commands for Paper)


## Commands
- `/register <password> <confirm>`
  - First-time setup. Saves a salted PBKDF2 hash and your current IP as `lastIp`.
- `/login <password>`
  - Authenticate when joining from a new IP. Updates `lastIp`.
- `/changepassword <old> <new> <confirm>`
  - Change your password. Re-authenticates you and updates `lastIp`.

Notes:
- Commands are player-only (not console).
- Minimum password length: 4 characters.


## How it works
1. Player joins.
   - Not registered → prompted to `/register` and blocked from most actions until done.
   - Registered and `currentIp == lastIp` → auto-authenticated.
   - Registered and `currentIp != lastIp` → must `/login <password>`.
2. After successful register/login/change, the player is marked authenticated for the session.


## Storage format
`plugins/trustmebro/users.yml`
```
users:
  username_lower:
    hash: <base64 PBKDF2 hash>
    salt: <base64 random salt>
    lastIp: 203.0.113.42
```
- KDF: PBKDF2WithHmacSHA256, 65,536 iterations, 256-bit key
- Salt: 16 bytes per user (random)


## Requirements
- Server: Paper 1.21.x
- Java: 21


## Install
1. Download the plugin JAR (build it yourself or from Releases).
2. Place it into your server’s `plugins/` folder.
3. Start the server. The plugin will create `plugins/trustmebro/users.yml` on first run.
4. Join the server:
   - New players: `/register <password> <confirm>`
   - Returning from new IP: `/login <password>`


## Build from source
This project uses Gradle + Kotlin + Shadow (shaded) JAR.

Prerequisites: Java 21, Internet access for dependencies.

- Build: `./gradlew build` (or `gradlew.bat build` on Windows)
- Output: `build/libs/trustmebro-<version>-all.jar`

Gradle highlights:
- Paper API: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- Kotlin toolchain: 21
- ShadowJar wired as a build dependency


## Configuration
There is no external configuration yet. Behavior is intentionally simple:
- Only the three auth commands are allowed pre-auth
- Everything else is blocked until authentication completes

If you want config options (messages, password policy, kick timeout, rate limits, etc.), feel free to open an issue or PR.


## Security notes
- Passwords are never stored in plaintext; only salted PBKDF2 hashes are persisted.
- Auto-login by IP is a convenience/security tradeoff; if your ISP changes your IP frequently, you’ll be prompted to `/login` more often. If your environment shares IPs (e.g., LAN/cafes), consider additional hardening.
- Optional hardening ideas (not yet implemented):
  - Login timeout with kick if unauthenticated
  - Max failed attempts with temporary lockout
  - Admin command to reset a user’s password
  - Permissions & localization
  - Alternative storage backends (SQLite/MySQL)


## Compatibility
- Paper: 1.21.x (tested against API 1.21.11)
- Should not be used on online-mode servers; it’s designed for offline/cracked setups.


## Acknowledgements
- PaperMC API and documentation: https://docs.papermc.io/


## FAQ
- Q: Why not AuthMe?
  - A: This plugin focuses on a minimal, IP-aware flow with a small footprint and modern Kotlin code. AuthMe offers many more features; use it if you need them.
- Q: Does this protect me if someone spoofs my IP?
  - A: No. IP-based auto-login is a convenience and a basic safety check. For higher security, require login every session or add MFA/lockouts.
