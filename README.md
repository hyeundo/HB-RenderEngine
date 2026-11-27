# HB Render Engine (HB3D) — High-Fidelity Animated 3D Rendering for Minecraft Fabric

HB3D is an open-source, MIT-licensed 3D rendering engine for Minecraft Java Edition 1.21.4 (Fabric). It lets mod developers integrate custom binary glTF (`.glb`) models exported from Blender, including rigged characters, animated bosses and 3D world backgrounds, directly into their mod JARs.

**High-fidelity character and boss rendering is a core capability of HB3D.** In-game development testing has demonstrated detailed character meshes, intricate textured wings, elaborate costumes and skeletal animation, with visually stable results reported by the developer. HB3D enables RPG-style character presentation beyond the conventional Minecraft block-model aesthetic.

HB3D separates visual presentation from gameplay logic. It provides model loading, skeletal animation, GPU skinning, a CPU fallback renderer, socket queries, animation events, server-driven visuals and world backgrounds. Iris compatibility code is included; shader-pack compatibility is not guaranteed.

**Status:** experimental v0.1.0. No bundled character or environment assets.

## Features and use cases

| Feature | What it enables |
| --- | --- |
| GLB model loading | Render highly detailed character, boss and environment meshes with their authored textures and geometry |
| Skeletal animation and GPU skinning | Bring complex rigged RPG characters and bosses to life with skeletal animation; GPU skinning and CPU fallback renderer included |
| Animation states and events | Map idle, movement, attack and death clips to gameplay states and receive timed events |
| Model sockets | Query attachment transforms for weapons and visual effects |
| Server-driven visuals | Synchronize custom boss visuals through the documented network protocol |
| World backgrounds | Place custom 3D scenery with optional Paper integration |
| Shader compatibility | Iris compatibility implementation, developed with Complementary Unbound |

HB3D targets Java mod developers and custom RPG server projects with a Fabric client mod. It is a rendering framework, not a standalone model editor or a complete boss gameplay system.

## Early development showcase

[Watch the original technical demonstration on YouTube](https://www.youtube.com/watch?v=dkFnCKsmeX0)

HB3D began as a technical experiment for a FINAL FANTASY XIV fan project in Minecraft and evolved into a general-purpose GLB rendering engine. The early demonstration contains third-party game assets used during development. Those assets have been removed from the public source and distribution builds and are not covered by the MIT license. This independent project is not affiliated with or endorsed by Square Enix.

## Requirements

- Minecraft 1.21.4
- Java 21
- Fabric Loader 0.16.10 or later
- Fabric API for Minecraft 1.21.4

## Shader compatibility

HB3D was developed primarily with Complementary Unbound Shaders, using Iris 1.8.8 and Sodium 0.6.9.

| Component | Status |
| --- | --- |
| Complementary Unbound | Primary development shader pack; public preview startup and world entry verified |
| Iris 1.8.8 | Compatibility implementation |
| Sodium 0.6.9 | Development environment |
| Complementary Reimagined and other shader packs | Not separately validated |

The early development showcase above demonstrates GLB rendering and animation with Complementary Shaders enabled. The initial public preview smoke test did not include GLB assets. Subsequent in-game testing of the public build confirmed successful GLB model rendering. Full rendering compatibility across shader versions and settings is not guaranteed.

## Build

Windows: `gradlew.bat build`

Linux/macOS: `./gradlew build`

The mod JAR is produced in `build/libs/`. Run unit tests with the Gradle `test` task.

For the optional Paper integration, run `gradlew.bat -p paper-integration build` (or `./gradlew -p paper-integration build`).

## Model integration

Supply your own licensed GLB assets and model definitions before building. Place GLB files under `src/main/resources/assets/<namespace>/models/` and built-in JSON definitions under `src/main/resources/data/hb3d/hb3d_models/`. GLB identifiers resolve to classpath resources inside the mod JAR; ordinary resource-pack GLB loading is not supported. See `examples/model-definition.json` for an illustrative definition (no GLB is bundled). Definitions require an idle base state and a death action state. See the runtime specification for state and socket mappings.

The Java visual API is exposed by `Hb3dMod.getVisualApi()`. It supports binding models to entity IDs, setting base/action states, querying sockets and subscribing to animation events.

The optional Paper plugin synchronizes world backgrounds using `/hbbackground set <model-id>`. The referenced model must be available to the client.

## Quick start: how to use a Blender GLB model in Minecraft

1. Export your licensed model from Blender as glTF 2.0 binary (`.glb`), including the required animation clips for an animated character.
2. Save it as `src/main/resources/assets/example/models/entity/character.glb`.
3. Copy [the example model definition](examples/model-definition.json) to `src/main/resources/data/hb3d/hb3d_models/example_character.json`. Match the animation clip and socket node names to your actual model.
4. Build with `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS using Java 21.
5. Load the resulting mod in the Fabric client environment and use the visual API from your gameplay integration to bind the model to an entity.

Example Java API calls (with an existing entity ID):

```java
var visuals = Hb3dMod.getVisualApi();
visuals.bindModel(entityId, "example_character");
visuals.setBaseState(entityId, "idle");
visuals.setActionState(entityId, "attack_primary", 1L);
```

These calls illustrate the API, not a complete standalone mod. Import `com.yourname.hb3d.Hb3dMod` and integrate them into your own gameplay code.

## Frequently asked questions

### Can I import Blender models into Minecraft without converting them to vanilla model JSON?

Yes. HB3D loads binary glTF (GLB) assets directly. A separate HB3D model-definition JSON maps the asset, animation clips and sockets; it is not vanilla mesh geometry JSON.

### Does HB3D load FBX, OBJ or external resource-pack GLB files?

The documented model pipeline uses GLB assets embedded in the mod JAR. Direct FBX, OBJ and ordinary resource-pack GLB loading are not supported by this pipeline. Export other model formats to GLB and rebuild the mod.

### Can I make animated custom bosses for a Paper server?

HB3D provides a boss visual synchronization protocol and an optional Paper world-background plugin. Clients need the Fabric rendering mod and matching assets. Boss AI, combat logic and server-side boss integration are your responsibility; the included Paper plugin does not implement a complete boss system.

### Does it work with Iris and Complementary Shaders?

Complementary Unbound is the primary development shader pack. See the shader compatibility section and validation notes for the early rendering demonstration, initial asset-free smoke test and subsequent successful in-game GLB rendering test.

### Is HB3D a finished production-ready engine?

No. Version 0.1.0 is an experimental public preview. Fabric and Paper builds and two transform tests passed; in-game GLB rendering has also been verified. Dedicated-server and Paper runtime validation remain outstanding. No performance benchmark is claimed.

## Documentation

- [Runtime specification](docs/HB3D_RUNTIME_SPEC_V1.md)
- [Boss protocol](docs/server-boss-protocol.md)
- [World background protocol](docs/multiverse-background-protocol.md)
- [Release validation and known test limitations](docs/RELEASE_VALIDATION.md)
- [한국어 README](README.ko.md)

## AI-assisted development

HB3D was developed by HB over approximately one to two months with assistance from ChatGPT and OpenAI Codex. The project documents an iterative human–AI engineering workflow. It is an independent project, not an official OpenAI or Minecraft product.

## License

Released under the [MIT License](LICENSE). No third-party game assets are distributed with this project.