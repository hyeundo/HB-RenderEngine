package com.yourname.hb3d.runtime;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.animation.AnimationStateResolver;
import com.yourname.hb3d.animation.ResolvedAnimationState;
import com.yourname.hb3d.api.Hb3dAnimationListener;
import com.yourname.hb3d.api.Hb3dVisualApi;
import com.yourname.hb3d.api.SocketTransform;
import com.yourname.hb3d.asset.AnimationEventDefinition;
import com.yourname.hb3d.asset.GlbModelLoader;
import com.yourname.hb3d.asset.ModelDefinition;
import com.yourname.hb3d.asset.ModelAssetCache;
import com.yourname.hb3d.asset.ModelRegistry;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.render.SkeletonPoseCalculator;
import com.yourname.hb3d.render.SocketResolver;
import com.yourname.hb3d.util.Hb3dState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class VisualController implements Hb3dVisualApi {
    private final ModelRegistry modelRegistry;
    private final ModelAssetCache assetCache = new ModelAssetCache();
    private final GlbModelLoader glbModelLoader = new GlbModelLoader();
    private final VisualInstanceManager instanceManager = new VisualInstanceManager();
    private final List<Hb3dAnimationListener> listeners = new CopyOnWriteArrayList<>();
    private final AnimationStateResolver stateResolver = new AnimationStateResolver();
    private final SkeletonPoseCalculator poseCalculator = new SkeletonPoseCalculator();
    private final SocketResolver socketResolver = new SocketResolver();

    public VisualController(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void bindModel(int entityId, String modelId) {
        VisualInstance instance = instanceManager.getOrCreate(entityId);
        instance.setModelId(modelId);
        instance.setBaseState(Hb3dState.IDLE);
        instance.setActionState(Hb3dState.NONE);
        instance.setActionSequence(0L);
        ensureAssetLoaded(modelId);
        applyResolvedState(instance, true);
    }

    @Override
    public void clearModel(int entityId) {
        instanceManager.remove(entityId);
    }

    @Override
    public void setBaseState(int entityId, String stateName) {
        VisualInstance instance = instanceManager.getOrCreate(entityId);
        instance.setBaseState(stateName);
        applyResolvedState(instance, false);
    }

    @Override
    public void setActionState(int entityId, String stateName, long actionSequence) {
        VisualInstance instance = instanceManager.getOrCreate(entityId);
        String previousAction = instance.actionState();
        long previousSequence = instance.actionSequence();
        instance.setActionState(stateName);
        instance.setActionSequence(actionSequence);
        boolean forceRestart = !Objects.equals(stateName, previousAction) || actionSequence != previousSequence;
        applyResolvedState(instance, forceRestart);
    }

    @Override
    public void clearActionState(int entityId) {
        instanceManager.get(entityId).ifPresent(instance -> {
            instance.setActionState(Hb3dState.NONE);
            applyResolvedState(instance, true);
        });
    }

    @Override
    public Optional<SocketTransform> getSocketTransform(int entityId, String socketName) {
        return instanceManager.get(entityId)
                .flatMap(instance -> instance.socketPoseCache().get(socketName));
    }

    @Override
    public Optional<String> getCurrentClip(int entityId) {
        return instanceManager.get(entityId)
                .flatMap(instance -> instance.animationPlayer().getActiveClip());
    }

    @Override
    public boolean isClipPlaying(int entityId, String clipName) {
        return instanceManager.get(entityId)
                .map(instance -> instance.animationPlayer().isPlaying(clipName))
                .orElse(false);
    }

    @Override
    public void registerAnimationListener(Hb3dAnimationListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterAnimationListener(Hb3dAnimationListener listener) {
        listeners.remove(listener);
    }

    public VisualInstanceManager getInstanceManager() {
        return instanceManager;
    }

    public List<Hb3dAnimationListener> getListeners() {
        return listeners;
    }

    public Optional<RuntimeModelAsset> debugGetAsset(int entityId) {
        return instanceManager.get(entityId)
                .map(VisualInstance::modelId)
                .filter(modelId -> modelId != null && !modelId.isBlank())
                .map(this::ensureAssetLoaded);
    }

    public Optional<VisualInstance> debugGetInstance(int entityId) {
        return instanceManager.get(entityId);
    }

    public boolean debugPlayClip(int entityId, String clipName, boolean loop, boolean holdLastFrame, int transitionTicks) {
        if (clipName == null || clipName.isBlank()) {
            return false;
        }
        Optional<VisualInstance> optionalInstance = instanceManager.get(entityId);
        if (optionalInstance.isEmpty()) {
            return false;
        }
        VisualInstance instance = optionalInstance.get();
        String modelId = instance.modelId();
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        RuntimeModelAsset asset = ensureAssetLoaded(modelId);
        if (!asset.clipNames().contains(clipName)) {
            return false;
        }

        instance.animationPlayer().play(clipName, loop, holdLastFrame, transitionTicks);
        instance.clearCompletedClip();
        instance.eventTracker().reset();
        notifyAnimationStarted(instance, clipName);
        poseCalculator.updatePose(instance, asset, 0.0F);
        syncPreviousLocalPose(instance);
        modelRegistry.get(modelId).ifPresent(definition -> socketResolver.populateSockets(instance, definition, asset));
        return true;
    }

    public void tick(float deltaSeconds) {
        for (VisualInstance instance : instanceManager.all()) {
            instance.advanceWorldTransform();
            updateInstance(instance, deltaSeconds);
        }
    }

    public void spawnServerVisual(
            int visualId,
            long generation,
            String modelId,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float scale,
            String baseState
    ) {
        validateIdentifier(modelId, "modelId", 128);
        validateIdentifier(baseState, "baseState", 64);
        validateTransform(x, y, z, yaw, pitch, scale);
        ensureAssetLoaded(modelId);
        bindModel(visualId, modelId);
        VisualInstance instance = instanceManager.getOrCreate(visualId);
        instance.setServerControlled(true);
        instance.setServerGeneration(generation);
        instance.setWorldTransform(x, y, z, yaw, pitch, scale, true);
        setBaseState(visualId, baseState);
    }

    public void updateServerTransform(
            int visualId,
            long generation,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float scale,
            boolean teleport
    ) {
        validateTransform(x, y, z, yaw, pitch, scale);
        instanceManager.get(visualId)
                .filter(instance -> instance.serverGeneration() == generation)
                .ifPresent(instance -> instance.setWorldTransform(x, y, z, yaw, pitch, scale, teleport));
    }

    public void updateServerAnimation(
            int visualId,
            long generation,
            String baseState,
            String actionState,
            long actionSequence
    ) {
        validateIdentifier(baseState, "baseState", 64);
        validateIdentifier(actionState, "actionState", 64);
        instanceManager.get(visualId).filter(instance -> instance.serverGeneration() == generation).ifPresent(instance -> {
            setBaseState(visualId, baseState);
            if (actionState == null || actionState.isBlank() || Hb3dState.NONE.equals(actionState)) {
                clearActionState(visualId);
            } else {
                setActionState(visualId, actionState, actionSequence);
            }
        });
    }

    public void despawnServerVisual(int visualId, long generation) {
        instanceManager.get(visualId)
                .filter(instance -> instance.serverGeneration() == generation)
                .ifPresent(instance -> clearModel(visualId));
    }

    public void clearServerVisuals() {
        instanceManager.clearServerControlled();
    }

    public List<VisualInstance> serverVisuals() {
        return instanceManager.all().stream()
                .filter(VisualInstance::isServerControlled)
                .filter(VisualInstance::hasWorldTransform)
                .toList();
    }

    public Optional<RuntimeModelAsset> getAsset(VisualInstance instance) {
        if (instance == null || instance.modelId() == null || instance.modelId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ensureAssetLoaded(instance.modelId()));
    }

    public float getDefinitionScale(VisualInstance instance) {
        if (instance == null || instance.modelId() == null) return 1.0F;
        return modelRegistry.get(instance.modelId()).map(ModelDefinition::renderScale).orElse(1.0F);
    }

    private void validateTransform(double x, double y, double z, float yaw, float pitch, float scale) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(scale)) {
            throw new IllegalArgumentException("Boss transform contains a non-finite value");
        }
        if (Math.abs(x) > 30_000_000.0D || Math.abs(z) > 30_000_000.0D || Math.abs(y) > 4096.0D) {
            throw new IllegalArgumentException("Boss transform is outside supported world bounds");
        }
        if (scale < 0.001F || scale > 100.0F) {
            throw new IllegalArgumentException("Boss scale must be between 0.001 and 100");
        }
    }

    private void validateIdentifier(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || !value.matches("[A-Za-z0-9_./:-]+")) {
            throw new IllegalArgumentException("Invalid boss " + name);
        }
    }

    public void invalidateAssets() {
        assetCache.clear();
    }

    private void applyResolvedState(VisualInstance instance, boolean forceRestart) {
        String modelId = instance.modelId();
        if (modelId == null || modelId.isBlank()) {
            return;
        }

        ModelDefinition definition = modelRegistry.get(modelId)
                .orElseThrow(() -> new IllegalStateException("Unknown model id: " + modelId));
        ensureAssetLoaded(modelId);
        ResolvedAnimationState resolvedState = stateResolver.resolve(definition, instance.baseState(), instance.actionState());
        RuntimeModelAsset asset = ensureAssetLoaded(modelId);
        String activeClip = resolveAvailableClip(asset, resolvedState);

        instance.setResolvedBaseClip(resolvedState.resolvedBaseClip());
        instance.setResolvedActionClip(resolvedState.resolvedActionClip());

        if (activeClip == null || activeClip.isBlank()) {
            return;
        }

        boolean alreadyPlaying = instance.animationPlayer().isPlaying(activeClip);
        if (!forceRestart && alreadyPlaying) {
            return;
        }

        instance.animationPlayer().play(
                activeClip,
                resolvedState.loop(),
                resolvedState.holdLastFrame(),
                resolvedState.transitionTicks()
        );
        instance.clearCompletedClip();
        instance.eventTracker().reset();
        notifyAnimationStarted(instance, activeClip);
    }

    private void updateInstance(VisualInstance instance, float deltaSeconds) {
        String modelId = instance.modelId();
        if (modelId == null || modelId.isBlank()) {
            return;
        }

        Optional<String> activeClipOptional = instance.animationPlayer().getActiveClip();
        if (activeClipOptional.isEmpty()) {
            applyResolvedState(instance, true);
            activeClipOptional = instance.animationPlayer().getActiveClip();
            if (activeClipOptional.isEmpty()) {
                return;
            }
        }

        String activeClip = activeClipOptional.get();
        ModelDefinition definition = modelRegistry.get(modelId)
                .orElseThrow(() -> new IllegalStateException("Unknown model id: " + modelId));
        RuntimeModelAsset asset = ensureAssetLoaded(modelId);
        float previousTime = instance.animationPlayer().getClipTimeSeconds();

        syncPreviousLocalPose(instance);
        instance.animationPlayer().tick(deltaSeconds);
        float currentTime = instance.animationPlayer().getClipTimeSeconds();
        poseCalculator.updatePose(instance, asset, deltaSeconds);
        socketResolver.populateSockets(instance, definition, asset);

        emitDueMarkers(instance, definition, activeClip, previousTime, currentTime);

        float durationSeconds = asset.clipDurationSeconds(activeClip).orElse(Float.MAX_VALUE);
        if (durationSeconds == Float.MAX_VALUE) {
            return;
        }

        if (instance.animationPlayer().isLoop()) {
            if (currentTime >= durationSeconds) {
                float loopedTime = currentTime % durationSeconds;
                instance.animationPlayer().setClipTimeSeconds(loopedTime);
                instance.eventTracker().reset();
            }
            return;
        }

        if (currentTime < durationSeconds) {
            return;
        }

        if (Objects.equals(instance.completedClip(), activeClip) && instance.completedClipSequence() == instance.actionSequence()) {
            return;
        }

        if (instance.animationPlayer().isHoldLastFrame()) {
            instance.animationPlayer().setClipTimeSeconds(durationSeconds);
        }

        instance.markClipCompleted(activeClip, instance.actionSequence());
        notifyAnimationFinished(instance, activeClip);

        if (!Hb3dState.NONE.equals(instance.actionState()) && !Hb3dState.DEATH.equals(instance.actionState())) {
            instance.setActionState(Hb3dState.NONE);
            applyResolvedState(instance, true);
        }
    }

    private String resolveAvailableClip(RuntimeModelAsset asset, ResolvedAnimationState resolvedState) {
        String activeClip = resolvedState.activeClip();
        if (activeClip != null && asset.clipNames().contains(activeClip)) {
            return activeClip;
        }

        String resolvedBaseClip = resolvedState.resolvedBaseClip();
        if (resolvedBaseClip != null && asset.clipNames().contains(resolvedBaseClip)) {
            return resolvedBaseClip;
        }

        if (!asset.clipNames().isEmpty()) {
            return asset.clipNames().get(0);
        }

        return activeClip;
    }

    private void syncPreviousLocalPose(VisualInstance instance) {
        instance.previousLocalNodePoseCache().clear();
        instance.localNodePoseCache().snapshot()
                .forEach((nodeName, transform) -> instance.previousLocalNodePoseCache().put(nodeName, transform));
    }

    private void emitDueMarkers(VisualInstance instance, ModelDefinition definition, String clipName, float previousTime, float currentTime) {
        definition.events().getOrDefault(clipName, List.of()).stream()
                .sorted(Comparator.comparing(AnimationEventDefinition::timeSeconds))
                .filter(event -> event.timeSeconds() > previousTime && event.timeSeconds() <= currentTime)
                .filter(event -> instance.eventTracker().tryMarkFired(event.name()))
                .forEach(event -> notifyMarker(instance, clipName, event));
    }

    private RuntimeModelAsset ensureAssetLoaded(String modelId) {
        return assetCache.get(modelId)
                .orElseGet(() -> {
                    ModelDefinition definition = modelRegistry.get(modelId)
                            .orElseThrow(() -> new IllegalStateException("Unknown model id: " + modelId));
                    RuntimeModelAsset asset = glbModelLoader.load(definition);
                    assetCache.put(asset);
                    return asset;
                });
    }

    private void notifyAnimationStarted(VisualInstance instance, String clipName) {
        for (Hb3dAnimationListener listener : listeners) {
            listener.onAnimationStarted(
                    instance.entityId(),
                    instance.modelId(),
                    clipName,
                    instance.actionSequence()
            );
        }
    }

    private void notifyMarker(VisualInstance instance, String clipName, AnimationEventDefinition event) {
        for (Hb3dAnimationListener listener : listeners) {
            listener.onMarker(
                    instance.entityId(),
                    instance.modelId(),
                    clipName,
                    event.name(),
                    event.timeSeconds(),
                    instance.actionSequence()
            );
        }
    }

    private void notifyAnimationFinished(VisualInstance instance, String clipName) {
        for (Hb3dAnimationListener listener : listeners) {
            listener.onAnimationFinished(
                    instance.entityId(),
                    instance.modelId(),
                    clipName,
                    instance.actionSequence()
            );
        }
    }
}
