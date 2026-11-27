# HB Render Engine (HB3D)

A GLB-based visual runtime for Minecraft Fabric 1.21.4.

HB3D separates visual presentation from gameplay logic. It provides model loading, skeletal animation, GPU skinning, a CPU fallback renderer, socket queries, animation events, server-driven visuals and world backgrounds. Iris compatibility code is included; shader-pack compatibility is not guaranteed.

**Status:** experimental v0.1.0. No bundled character or environment assets.

## Early development showcase

[Watch the original technical demonstration on YouTube](https://www.youtube.com/watch?v=dkFnCKsmeX0)

HB3D began as a technical experiment for a FINAL FANTASY XIV fan project in Minecraft and evolved into a general-purpose GLB rendering engine. The early demonstration contains third-party game assets used during development. Those assets have been removed from the public source and distribution builds and are not covered by the MIT license. This independent project is not affiliated with or endorsed by Square Enix.

## Requirements

- Minecraft 1.21.4
- Java 21
- Fabric Loader 0.16.10 or later
- Fabric API for Minecraft 1.21.4

The development configuration also includes Iris 1.8.8 and Sodium 0.6.9.

## Build

Windows: `gradlew.bat build`

Linux/macOS: `./gradlew build`

The mod JAR is produced in `build/libs/`. Run unit tests with the Gradle `test` task.

For the optional Paper integration, run `gradlew.bat -p paper-integration build` (or `./gradlew -p paper-integration build`).

## Model integration

Supply your own licensed GLB assets and model definitions before building. Place GLB files under `src/main/resources/assets/<namespace>/models/` and built-in JSON definitions under `src/main/resources/data/hb3d/hb3d_models/`. GLB identifiers resolve to classpath resources inside the mod JAR; ordinary resource-pack GLB loading is not supported. See `examples/model-definition.json` for an illustrative definition (no GLB is bundled). Definitions require an idle base state and a death action state. See the runtime specification for state and socket mappings.

The Java visual API is exposed by `Hb3dMod.getVisualApi()`. It supports binding models to entity IDs, setting base/action states, querying sockets and subscribing to animation events.

The optional Paper plugin synchronizes world backgrounds using `/hbbackground set <model-id>`. The referenced model must be available to the client.

## Documentation

- [Runtime specification](docs/HB3D_RUNTIME_SPEC_V1.md)
- [Boss protocol](docs/server-boss-protocol.md)
- [World background protocol](docs/multiverse-background-protocol.md)
- [한국어 README](README.ko.md)

## AI-assisted development

HB3D was developed by HB over approximately one to two months with assistance from ChatGPT and OpenAI Codex. The project documents an iterative human–AI engineering workflow. It is an independent project, not an official OpenAI or Minecraft product.

## License

Released under the [MIT License](LICENSE). No third-party game assets are distributed with this project.