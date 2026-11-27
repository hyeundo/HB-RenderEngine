package com.yourname.hb3d.asset;

import com.yourname.hb3d.Hb3dMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

public final class ModelDefinitionLoader {
    public void loadBuiltinDefinitions(ModelRegistry registry) {
        registry.clear();
        List<String> builtinPaths = findBuiltinDefinitionPaths();

        for (String builtinPath : builtinPaths) {
            String fileName = builtinPath.substring(builtinPath.lastIndexOf('/') + 1);
            Identifier builtinId = Identifier.of(Hb3dMod.MOD_ID, "hb3d_models/" + fileName);
            try (BufferedReader reader = openBuiltin(builtinPath)) {
                registry.register(parseDefinition(builtinId, reader));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read builtin model definition: " + builtinId, exception);
            }
        }
    }

    public void reload(ResourceManager resourceManager, ModelRegistry registry) {
        registry.clear();
        final int[] loadedCount = {0};
        resourceManager.findResources("hb3d_models", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (BufferedReader reader = resource.getReader()) {
                        registry.register(parseDefinition(id, reader));
                        loadedCount[0]++;
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read model definition: " + id, exception);
                    }
                });

        if (loadedCount[0] == 0) {
            Hb3dMod.LOGGER.warn("No HB_Renderengine model definitions were found during reload; restoring builtin definitions");
            loadBuiltinDefinitions(registry);
        }
    }

    private BufferedReader openBuiltin(String classpathPath) {
        var stream = ModelDefinitionLoader.class.getClassLoader().getResourceAsStream(classpathPath);
        if (stream == null) {
            throw new IllegalStateException("Missing builtin model definition: " + classpathPath);
        }

        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private List<String> findBuiltinDefinitionPaths() {
        final String prefix = "data/hb3d/hb3d_models/";
        URL directoryUrl = ModelDefinitionLoader.class.getClassLoader().getResource("data/hb3d/hb3d_models");
        if (directoryUrl == null) {
            return List.of();
        }

        try {
            if ("file".equals(directoryUrl.getProtocol())) {
                try (var stream = Files.list(Path.of(directoryUrl.toURI()))) {
                    return stream
                            .filter(path -> path.getFileName().toString().endsWith(".json"))
                            .map(path -> prefix + path.getFileName())
                            .sorted()
                            .toList();
                }
            }

            if ("jar".equals(directoryUrl.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) directoryUrl.openConnection();
                try (JarFile jarFile = connection.getJarFile()) {
                    return jarFile.stream()
                            .map(entry -> entry.getName())
                            .filter(name -> name.startsWith(prefix))
                            .filter(name -> name.endsWith(".json"))
                            .sorted()
                            .toList();
                }
            }
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to enumerate builtin model definitions", exception);
        }

        return List.of();
    }

    private ModelDefinition parseDefinition(Identifier sourceId, BufferedReader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

        String modelId = requireNonBlank(JsonHelper.getString(root, "modelId"), sourceId, "modelId");
        Identifier glbId = Identifier.of(JsonHelper.getString(root, "glb"));
        JsonObject states = JsonHelper.getObject(root, "states");
        JsonObject baseStates = JsonHelper.getObject(states, "base");
        JsonObject actionStates = JsonHelper.getObject(states, "action");

        Map<String, String> baseMap = readStringMap(baseStates);
        Map<String, String> actionMap = readStringMap(actionStates);
        Map<String, String> socketMap = readStringMap(JsonHelper.getObject(root, "sockets"));
        Map<String, List<AnimationEventDefinition>> eventMap = readEventMap(getOptionalObject(root, "events"));

        JsonObject playbackObject = getOptionalObject(root, "playback");
        JsonObject transitionsObject = getOptionalObject(root, "transitions");

        validateStateMappings(sourceId, baseMap, actionMap);

        return new ModelDefinition(
                modelId,
                glbId,
                JsonHelper.getFloat(root, "renderScale", 1.0F),
                baseMap,
                actionMap,
                socketMap,
                eventMap,
                new ModelDefinition.PlaybackDefinition(
                        readStringArray(playbackObject, "loop"),
                        readStringArray(playbackObject, "holdLastFrame")
                ),
                new ModelDefinition.TransitionDefinition(
                        JsonHelper.getInt(transitionsObject, "defaultTicks", 4),
                        JsonHelper.getInt(transitionsObject, "actionTicks", 2),
                        JsonHelper.getInt(transitionsObject, "deathTicks", 1)
                )
        );
    }

    private Map<String, String> readStringMap(JsonObject object) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return values;
    }

    private Map<String, List<AnimationEventDefinition>> readEventMap(JsonObject object) {
        Map<String, List<AnimationEventDefinition>> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            List<AnimationEventDefinition> events = new ArrayList<>();
            JsonArray array = entry.getValue().getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject eventObject = element.getAsJsonObject();
                events.add(new AnimationEventDefinition(
                        JsonHelper.getFloat(eventObject, "time"),
                        JsonHelper.getString(eventObject, "name")
                ));
            }
            values.put(entry.getKey(), List.copyOf(events));
        }
        return values;
    }

    private List<String> readStringArray(JsonObject object, String key) {
        if (!object.has(key)) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        JsonArray array = JsonHelper.getArray(object, key);
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private JsonObject getOptionalObject(JsonObject root, String key) {
        return root.has(key) ? JsonHelper.getObject(root, key) : new JsonObject();
    }

    private void validateStateMappings(Identifier sourceId, Map<String, String> baseMap, Map<String, String> actionMap) {
        requireKey(sourceId, baseMap, "idle");
        requireKey(sourceId, actionMap, "death");
    }

    private void requireKey(Identifier sourceId, Map<String, String> values, String key) {
        if (!values.containsKey(key) || values.get(key).isBlank()) {
            throw new IllegalStateException("Missing required state mapping '" + key + "' in " + sourceId);
        }
    }

    private String requireNonBlank(String value, Identifier sourceId, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required field '" + key + "' in " + sourceId);
        }
        return value;
    }
}
