# HB_Renderengine Boss Protocol v1

Static per-world GLB backgrounds use the separate
[`hb3d:background_sync` protocol](multiverse-background-protocol.md).

The server owns hitboxes, combat, lifetime, and authoritative boss state. The client mod only renders visuals.

All packets are clientbound Minecraft custom payloads. Numbers use Minecraft/Netty big-endian encoding. A string is a VarInt UTF-8 byte length followed by its bytes.

## `hb3d:boss_hello` (client to server)

| Field | Encoding |
| --- | --- |
| `protocolVersion` | VarInt, currently `1` |
| `modVersion` | String, max 32 bytes |

The plugin should register this incoming channel and only send boss payloads after receiving a compatible hello.

## `hb3d:boss_spawn`

| Field | Encoding |
| --- | --- |
| `visualId` | VarInt |
| `generation` | VarLong |
| `modelId` | String, max 128 bytes |
| `x`, `y`, `z` | three doubles |
| `yaw`, `pitch`, `scale` | three floats |
| `baseState` | String, max 64 bytes |

The ID must be unique for the player's current connection. Sending spawn again replaces the visual state for that ID. `scale=1` uses the model definition's configured scale.

## `hb3d:boss_transform`

| Field | Encoding |
| --- | --- |
| `visualId` | VarInt |
| `generation` | VarLong |
| `x`, `y`, `z` | three doubles |
| `yaw`, `pitch`, `scale` | three floats |
| `teleport` | boolean byte |

Send ordinary transforms at the server tick rate. The client interpolates them between ticks. Set `teleport=true` for warps, respawns, or corrections that must not interpolate.

## `hb3d:boss_animation`

| Field | Encoding |
| --- | --- |
| `visualId` | VarInt |
| `generation` | VarLong |
| `baseState` | String, max 64 bytes |
| `actionState` | String, max 64 bytes |
| `actionSequence` | VarLong |

States resolve through the model JSON. Use `none` for no action. Increment `actionSequence` whenever the same action must restart, such as consecutive attacks.

## `hb3d:boss_despawn`

| Field | Encoding |
| --- | --- |
| `visualId` | VarInt |
| `generation` | VarLong |

Increment generation whenever an ID is reused. Updates and despawns with an older generation are ignored. Always send despawn when the boss becomes irrelevant to a player. The client also clears all server visuals when the connection closes.

## Recommended Flow

1. Send spawn when the player starts tracking the boss.
2. Send transform updates at 20 TPS or whenever the authoritative transform changes.
3. Send animation only when state or action sequence changes.
4. Send despawn when tracking stops, the boss dies, or the player changes to an unrelated world.
