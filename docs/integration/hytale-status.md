# Hytale Integration Status

> **Last Updated:** 2026-02-09
> **Repository:** https://github.com/LegendaryForge/LegendaryHytale

## Current State

### LegendaryHytale Plugin

**Status:** Initial setup complete, builds successfully

**What exists:**
- Hytale plugin project created from official template
- Configured for `io.github.legendaryforge` organization
- Main plugin class: `LegendaryHytalePlugin`
- Event listener: `PlayerJoinListener` (listens to `PlayerConnectEvent`)
- Successfully compiles to JAR

**What works:**
- Plugin loads in Hytale
- Can register and respond to game events
- Logging infrastructure operational

**What does NOT work yet:**
- No actual quest logic integrated
- Not tested in live Hytale server
- No connection to LegendaryCore/Legendary code

---

## Hytale API Discoveries

### Package Structure

Hytale uses different package naming than initially assumed:

```java
// CORRECT packages:
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;

// NOT (these dont exist):
// import com.hypixel.hytale.plugin.* 
```

### ECS Structure

**Hytale's ECS closely matches our architectural assumptions:**

- `EntityStore` - Wraps the core ECS Store
- `Store<EntityStore>` - The actual ECS store
- `Ref<EntityStore>` - Entity references (like we assumed)
- `ComponentRegistry<EntityStore>` - Component type registration
- `CommandBuffer<EntityStore>` - Thread-safe mutation queue
- `RefSystem` - System base class for entity lifecycle
- `Query<EntityStore>` - Entity filtering by components

**Key classes located:**
```
com.hypixel.hytale.server.core.universe.world.storage.EntityStore
com.hypixel.hytale.component.Store
com.hypixel.hytale.component.Ref
com.hypixel.hytale.component.CommandBuffer
com.hypixel.hytale.component.system.RefSystem
```

### Events

**Available player events:**
- `PlayerConnectEvent` - When player connects
- `PlayerDisconnectEvent` - When player disconnects  
- `PlayerInteractEvent` - Player interactions
- `PlayerCraftEvent` - Crafting events

**Event registration:**
```java
getEventRegistry().registerGlobal(PlayerConnectEvent.class, listener::method);
```

---

## Integration Strategy

### Current Approach: Standalone First

**Decision:** Build LegendaryHytale as a standalone plugin initially, bundling LegendaryCore and Legendary inside the JAR.

**Rationale:**
- Simpler for players (one file to install)
- Easier to develop and test
- Can extract LegendaryCore as separate library later if needed

### Dependency Challenge

Attempted to use Gradle `includeBuild` to reference local Legendary projects, but the scaffoldit plugin interfered. 

**Current solution:** Develop integration code directly in LegendaryHytale until we figure out clean dependency wiring.

**Future solution:** Add Maven publishing to LegendaryCore/Legendary, or copy necessary classes.

---

## Next Steps

### Immediate (Next Session)

1. **Test the plugin in Hytale server**
   - Run local dev server from IntelliJ
   - Verify plugin loads
   - Confirm player connect event fires

2. **Map Legendary ECS to Hytale ECS**
   - Understand how to create custom components
   - Learn how to register systems
   - Figure out entity creation/mutation

3. **Start Phase 1 integration proof-of-concept**
   - Create a minimal "Storm Attunement" trigger
   - Prove we can track quest progress
   - Test milestone emission

### Medium Term

- Integrate LegendaryCore dependency properly
- Map `StormseekerProgress` to Hytale components
- Implement Phase 1 tick loop in Hytale
- Create Hytale-specific presentation (particles, UI)

### Long Term

- Full Phase 0-2 integration
- Design Phase 3+
- Player testing and feedback

---

## Technical Notes

### Build Configuration

**Java Version:** Java 25 (required by Hytale)

**Gradle:** Using scaffoldit plugin v0.2.+

**Hytale Server Dependency:**
```kotlin
implementation("com.hypixel.hytale:Server:+")
```

This pulls the latest Hytale server version (currently `2026.02.06-aa1b071c2`).

### Development Environment

- **OS:** Garuda Linux (Arch-based)
- **IDE:** IntelliJ IDEA Community Edition
- **Desktop:** X11 (Hytale has Wayland scroll bugs)
- **Terminal:** Konsole with Fish shell

### Known Issues

- Hytale server JAR is decompiled (no source), must inspect via JAR inspection
- JetBrains JDK not installed (DCEVM hot-swap unavailable)
- Branch protection enabled on all repos (must use PR workflow)
