# v0.1.0 validation

- Java 21: Fabric build and remapped JAR generation passed.
- JUnit: 2 tests passed (transform interpolation and teleport snapshots).
- Paper integration: clean build passed; no automated tests.
- User-provided runtime log: Minecraft 1.21.4, Fabric Loader 0.19.3, Fabric API 0.119.4, Iris 1.8.8, Sodium 0.6.9, Complementary Unbound r5.1.1, RTX 4060, Windows 11. 104 loaded mods. HB3D initialized, reloaded an empty model registry, entered a singleplayer world and shut down normally. No HB3D exception observed.
- Empty model definitions and missing legacy data pack warnings were observed. Other mods and resource packs produced unrelated warnings/errors.
- Subsequent developer-reported in-game test: GLB models rendered successfully in the public build, with visually satisfactory results. Developer-supplied screenshots demonstrate detailed character geometry, intricate wing textures and elaborate costume details in Minecraft. The developer also reports stable skeletal animation playback. Screenshots alone do not establish temporal stability or performance. Exact test configuration and independent GPU skinning validation were not supplied.
- Not validated separately: GPU skinning, standalone dedicated server, Paper runtime, Iris-free environment, remote CI.
- Raw runtime logs are intentionally not distributed because they contain local paths and user identifiers.
