package com.yourname.hb3d.runtime;

import com.yourname.hb3d.animation.AnimationEventTracker;
import com.yourname.hb3d.animation.AnimationPlayer;

public final class VisualInstance {
    private final int entityId;
    private final AnimationPlayer animationPlayer = new AnimationPlayer();
    private final AnimationEventTracker eventTracker = new AnimationEventTracker();
    private final SocketPoseCache socketPoseCache = new SocketPoseCache();
    private final NodePoseCache previousLocalNodePoseCache = new NodePoseCache();
    private final NodePoseCache localNodePoseCache = new NodePoseCache();
    private final NodePoseCache worldNodePoseCache = new NodePoseCache();

    private String modelId;
    private String baseState = "idle";
    private String actionState = "none";
    private long actionSequence;
    private String resolvedBaseClip;
    private String resolvedActionClip;
    private String completedClip;
    private long completedClipSequence = Long.MIN_VALUE;
    private boolean serverControlled;
    private long serverGeneration;
    private boolean worldTransformInitialized;
    private double previousX;
    private double previousY;
    private double previousZ;
    private double currentX;
    private double currentY;
    private double currentZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private float previousYaw;
    private float currentYaw;
    private float targetYaw;
    private float previousPitch;
    private float currentPitch;
    private float targetPitch;
    private float previousScale = 1.0F;
    private float currentScale = 1.0F;
    private float targetScale = 1.0F;

    public VisualInstance(int entityId) {
        this.entityId = entityId;
    }

    public int entityId() {
        return entityId;
    }

    public String modelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String baseState() {
        return baseState;
    }

    public void setBaseState(String baseState) {
        this.baseState = baseState;
    }

    public String actionState() {
        return actionState;
    }

    public void setActionState(String actionState) {
        this.actionState = actionState;
    }

    public long actionSequence() {
        return actionSequence;
    }

    public void setActionSequence(long actionSequence) {
        this.actionSequence = actionSequence;
    }

    public String resolvedBaseClip() {
        return resolvedBaseClip;
    }

    public void setResolvedBaseClip(String resolvedBaseClip) {
        this.resolvedBaseClip = resolvedBaseClip;
    }

    public String resolvedActionClip() {
        return resolvedActionClip;
    }

    public void setResolvedActionClip(String resolvedActionClip) {
        this.resolvedActionClip = resolvedActionClip;
    }

    public AnimationPlayer animationPlayer() {
        return animationPlayer;
    }

    public AnimationEventTracker eventTracker() {
        return eventTracker;
    }

    public SocketPoseCache socketPoseCache() {
        return socketPoseCache;
    }

    public NodePoseCache localNodePoseCache() {
        return localNodePoseCache;
    }

    public NodePoseCache previousLocalNodePoseCache() {
        return previousLocalNodePoseCache;
    }

    public NodePoseCache worldNodePoseCache() {
        return worldNodePoseCache;
    }

    public String completedClip() {
        return completedClip;
    }

    public long completedClipSequence() {
        return completedClipSequence;
    }

    public void markClipCompleted(String clipName, long sequence) {
        this.completedClip = clipName;
        this.completedClipSequence = sequence;
    }

    public void clearCompletedClip() {
        this.completedClip = null;
        this.completedClipSequence = Long.MIN_VALUE;
    }

    public boolean isServerControlled() {
        return serverControlled;
    }

    public void setServerControlled(boolean serverControlled) {
        this.serverControlled = serverControlled;
    }

    public long serverGeneration() {
        return serverGeneration;
    }

    public void setServerGeneration(long serverGeneration) {
        this.serverGeneration = serverGeneration;
    }

    public boolean hasWorldTransform() {
        return worldTransformInitialized;
    }

    public void setWorldTransform(
            double x, double y, double z,
            float yaw, float pitch, float scale,
            boolean teleport
    ) {
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = wrapDegrees(yaw);
        targetPitch = pitch;
        targetScale = Math.max(0.001F, scale);
        if (!worldTransformInitialized || teleport) {
            previousX = currentX = targetX;
            previousY = currentY = targetY;
            previousZ = currentZ = targetZ;
            previousYaw = currentYaw = targetYaw;
            previousPitch = currentPitch = targetPitch;
            previousScale = currentScale = targetScale;
            worldTransformInitialized = true;
        }
    }

    public void advanceWorldTransform() {
        if (!worldTransformInitialized) return;
        previousX = currentX;
        previousY = currentY;
        previousZ = currentZ;
        previousYaw = currentYaw;
        previousPitch = currentPitch;
        previousScale = currentScale;
        currentX = targetX;
        currentY = targetY;
        currentZ = targetZ;
        currentYaw = targetYaw;
        currentPitch = targetPitch;
        currentScale = targetScale;
    }

    public WorldTransform interpolatedWorldTransform(float alpha) {
        float t = Math.max(0.0F, Math.min(1.0F, alpha));
        return new WorldTransform(
                lerp(previousX, currentX, t),
                lerp(previousY, currentY, t),
                lerp(previousZ, currentZ, t),
                previousYaw + shortestDegrees(previousYaw, currentYaw) * t,
                previousPitch + (currentPitch - previousPitch) * t,
                previousScale + (currentScale - previousScale) * t
        );
    }

    private static double lerp(double from, double to, float alpha) {
        return from + (to - from) * alpha;
    }

    private static float shortestDegrees(float from, float to) {
        return wrapDegrees(to - from);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public record WorldTransform(double x, double y, double z, float yaw, float pitch, float scale) {
    }
}
