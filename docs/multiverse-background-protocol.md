# HB3D Multiverse Background Protocol

HB3D treats a background as one server-authoritative static visual for the player's current Bukkit world.
The Paper/Multiverse integration must send a sync on join, `PlayerChangedWorldEvent`, and whenever an
administrator changes the placement. Minecraft dimension IDs are not sufficient because multiple Multiverse
worlds can all appear to the client as `minecraft:overworld`.

## Channel: `hb3d:background_sync`

The channel is clientbound. Numbers use Minecraft/Netty big-endian encoding. Strings use a VarInt UTF-8 byte
length followed by their bytes.

| Field | Encoding | Notes |
| --- | --- | --- |
| `worldName` | String, max 128 bytes | Exact Bukkit/Multiverse world name |
| `enabled` | Boolean byte | `0` removes the current background and ends the packet |
| `modelId` | String, max 128 bytes | Present only when enabled; e.g. `example_background` |
| `x`, `y`, `z` | Three doubles | World-space anchor |
| `yaw`, `pitch`, `scale` | Three floats | `scale=1` multiplies the model JSON `renderScale` |

Sending another enabled packet for the same `worldName` and `modelId` updates position, rotation, and scale
without uploading the GLB again. Sending a different world/model replaces the background. Sending
`enabled=false` is required for worlds without a configured background.

## Multiverse/Paper lifecycle

1. Register `hb3d:background_sync` as an outgoing plugin channel.
2. On player join, schedule a sync one tick later.
3. On `PlayerChangedWorldEvent`, send the destination world's placement or a disabled packet.
4. After `/hbbackground position`, `/hbbackground scale`, or configuration reload, resend the placement to
   every player currently in that Bukkit world.
5. Use `World#getName()` as `worldName`; do not send the environment or Minecraft dimension type.

The existing client hello is `hb3d:boss_hello`. A server plugin may use it as the capability signal before
sending background packets.
