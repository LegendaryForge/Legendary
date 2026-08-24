# Hytale Integration Notes

> **Last verified:** 2026-08-24 against the sources in `mod/hytale/`.
> **Module:** `mod/hytale/` — the only module in this repository that may import `com.hypixel.*`
> (enforced by `:core:checkNoPlatformImports`).

This document records what has been learned about the Hytale server API. Every package and
class name below is taken from imports that currently compile in `mod/hytale/src/main/java`,
not from inspection notes — the previous version of this file was written from exploratory
JAR inspection in February and several of its claims had drifted.

---

## Consuming the server API

The Hytale server JAR is **not** a published artifact. It is read from the local game install:

```kotlin
compileOnly(files(hytaleServerJar))
```

`mod/hytale/build.gradle.kts` locates it under the Flatpak or `~/.local/share` install path,
or an explicit `-Phytale_home=`. When no install is found the module excludes its Hytale
sources and the rest of the build proceeds — see the README for what that means for CI.

**Do not pin the game version in documentation.** It moves without notice: an auto-update on
2026-08-17 shipped a Java 25 jar against a build targeting Java 21 and broke compilation
project-wide. `:mod:hytale:checkHytaleJarVersion` now reads the jar's class-file version and
fails with one explicit message instead of a cascade. To see the version in use, inspect the
jar the build resolves.

---

## Package structure

The API lives under `com.hypixel.hytale.*`, but not where a plugin author would first guess —
there is no `com.hypixel.hytale.plugin.*`. Verified imports currently in use:

**Plugin lifecycle**
```java
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
```

**ECS** — closely matches this project's own architectural assumptions
```java
import com.hypixel.hytale.component.Ref;                       // entity reference
import com.hypixel.hytale.component.Store;                     // the ECS store
import com.hypixel.hytale.component.system.tick.TickingSystem; // per-tick system base
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
```

**World, players, transforms**
```java
import com.hypixel.hytale.server.core.universe.World;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
```

**Events**
```java
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
```

Registration idiom:
```java
getEventRegistry().registerGlobal(PlayerConnectEvent.class, listener::method);
```

**Commands**
```java
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
```

**Messaging**
```java
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.FormattedMessage;
```

**Weather** — used by the Stormseeker weather reader
```java
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
```

### Not everything is under `com.hypixel`

Vectors come from **JOML**, not from a Hytale package:

```java
import org.joml.Vector3d;
```

This one has bitten the project. `Vector3d` was previously at
`com.hypixel.hytale.math.vector.Vector3d`; it moved, and because the integration layer had no
build that could compile it at the time, **nothing noticed for roughly six months**. When
tracking down a missing type, check whether it has moved to a third-party library before
assuming it was removed.

---

## Integration shape

Superseding the February plan, which proposed building a standalone plugin that bundled the
other projects inside its JAR and wrestled with `includeBuild` and a `scaffoldit` plugin:
**that problem no longer exists.** All code lives in one Gradle multi-project and internal
dependencies are plain `project(":core")` / `project(":quests:stormseeker")`. The `scaffoldit`
plugin is gone.

The layering is:

- `core/` and `quests/stormseeker/` are **engine-agnostic** and must stay so. No `com.hypixel`
  import may appear under `core/src`; the build fails if one does.
- `mod/hytale/` holds every adapter that bridges the two — the plugin entrypoint, commands,
  the tick system, the weather reader, and the progress store.

`core.api.platform.CoreRuntime` exists as a platform seam, but its only implementation is
`DefaultCoreRuntime` inside `core`. There is no Hytale implementation of it; `mod/hytale`
talks to the game directly. Writing one is new construction, deferred until there is a second
platform target or a need to run `mod/hytale` headless in tests.

---

## Known constraints

- **The server JAR is decompiled and ships no sources.** API discovery means inspecting the
  JAR; there is no Javadoc to read.
- **`mod/hytale` has no tests.** Eight source files, zero test files — simultaneously the
  least-covered module and the one most exposed to game churn.
- **The game updates underneath the build.** Treat any compile failure in this module after a
  launcher update as a candidate API drift, and check `checkHytaleJarVersion`'s output first.
