package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.AnimationSamplerData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;

public final class SkeletonPoseCalculator {
    public void updatePose(VisualInstance instance, RuntimeModelAsset asset, float tickDelta) {
        instance.localNodePoseCache().clear();
        instance.worldNodePoseCache().clear();

        for (String nodeName : asset.nodeNames()) {
            asset.nodeTransform(nodeName).ifPresent(transform -> instance.localNodePoseCache().put(nodeName, transform));
        }

        String activeClip = instance.animationPlayer().getActiveClip().orElse(null);
        if (activeClip == null) {
            return;
        }

        float clipDuration = asset.clipDurationSeconds(activeClip).orElse(1.0F);
        float clipTime = instance.animationPlayer().getClipTimeSeconds();
        float sampleTime = clipDuration > 0.0F && instance.animationPlayer().isLoop()
                ? clipTime % clipDuration
                : clipTime;

        Map<String, NodeTransform> overrides = new HashMap<>();
        for (AnimationSamplerData sampler : asset.clipSamplers(activeClip)) {
            NodeTransform current = overrides.getOrDefault(
                    sampler.targetNode(),
                    asset.nodeTransform(sampler.targetNode()).orElse(NodeTransform.identity(sampler.targetNode()))
            );
            NodeTransform animated = applySampler(current, sampler, sampleTime);
            overrides.put(sampler.targetNode(), animated);
        }

        overrides.forEach((nodeName, transform) -> instance.localNodePoseCache().put(nodeName, transform));

        Map<String, NodeTransform> worldPoses = new HashMap<>();
        for (String nodeName : asset.nodeNames()) {
            resolveWorldPose(nodeName, instance, asset, worldPoses);
        }
        worldPoses.forEach((nodeName, transform) -> instance.worldNodePoseCache().put(nodeName, transform));
    }

    public Map<String, NodeTransform> interpolateLocalPoses(
            RuntimeModelAsset asset,
            Map<String, NodeTransform> previous,
            Map<String, NodeTransform> current,
            float alpha
    ) {
        float clampedAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        if (previous.isEmpty()) {
            return new HashMap<>(current);
        }
        if (clampedAlpha <= 0.000001F) {
            return new HashMap<>(previous);
        }
        if (clampedAlpha >= 0.999999F) {
            return new HashMap<>(current);
        }

        Map<String, NodeTransform> interpolated = new HashMap<>();
        for (String nodeName : asset.nodeNames()) {
            NodeTransform currentTransform = current.getOrDefault(
                    nodeName,
                    asset.nodeTransform(nodeName).orElse(NodeTransform.identity(nodeName))
            );
            NodeTransform previousTransform = previous.getOrDefault(nodeName, currentTransform);
            interpolated.put(nodeName, interpolate(previousTransform, currentTransform, clampedAlpha));
        }
        return interpolated;
    }

    private NodeTransform applySampler(NodeTransform current, AnimationSamplerData sampler, float sampleTime) {
        if (sampler.keyTimes().isEmpty() || sampler.values().isEmpty() || sampler.componentWidth() <= 0) {
            return current;
        }

        float[] sampled = sampleValues(sampler, sampleTime);
        return switch (sampler.targetPath()) {
            case "translation" -> new NodeTransform(
                    current.name(),
                    component(sampled, 0, current.tx()),
                    component(sampled, 1, current.ty()),
                    component(sampled, 2, current.tz()),
                    current.rx(), current.ry(), current.rz(), current.rw(),
                    current.sx(), current.sy(), current.sz()
            );
            case "rotation" -> {
                float[] normalized = normalizeQuaternion(
                        component(sampled, 0, current.rx()),
                        component(sampled, 1, current.ry()),
                        component(sampled, 2, current.rz()),
                        component(sampled, 3, current.rw())
                );
                yield new NodeTransform(
                        current.name(),
                        current.tx(), current.ty(), current.tz(),
                        normalized[0], normalized[1], normalized[2], normalized[3],
                        current.sx(), current.sy(), current.sz()
                );
            }
            case "scale" -> new NodeTransform(
                    current.name(),
                    current.tx(), current.ty(), current.tz(),
                    current.rx(), current.ry(), current.rz(), current.rw(),
                    component(sampled, 0, current.sx()),
                    component(sampled, 1, current.sy()),
                    component(sampled, 2, current.sz())
            );
            default -> current;
        };
    }

    private float[] sampleValues(AnimationSamplerData sampler, float sampleTime) {
        List<Float> keyTimes = sampler.keyTimes();
        int componentWidth = sampler.componentWidth();
        int lastIndex = keyTimes.size() - 1;

        if (lastIndex <= 0) {
            return readFrameValues(sampler.values(), 0, componentWidth);
        }

        if (sampleTime <= keyTimes.get(0)) {
            return readFrameValues(sampler.values(), 0, componentWidth);
        }
        if (sampleTime >= keyTimes.get(lastIndex)) {
            return readFrameValues(sampler.values(), lastIndex, componentWidth);
        }

        for (int i = 0; i < lastIndex; i++) {
            float startTime = keyTimes.get(i);
            float endTime = keyTimes.get(i + 1);
            if (sampleTime < startTime || sampleTime > endTime) {
                continue;
            }

            float[] startValues = readFrameValues(sampler.values(), i, componentWidth);
            if ("STEP".equalsIgnoreCase(sampler.interpolation()) || endTime <= startTime) {
                return startValues;
            }

            float[] endValues = readFrameValues(sampler.values(), i + 1, componentWidth);
            float alpha = (sampleTime - startTime) / (endTime - startTime);
            if ("rotation".equals(sampler.targetPath()) && componentWidth >= 4) {
                return slerpQuaternion(startValues, endValues, alpha);
            }
            return lerp(startValues, endValues, alpha);
        }

        return readFrameValues(sampler.values(), lastIndex, componentWidth);
    }

    private float[] readFrameValues(List<Float> values, int frameIndex, int componentWidth) {
        float[] frame = new float[componentWidth];
        int baseIndex = frameIndex * componentWidth;
        for (int i = 0; i < componentWidth; i++) {
            int valueIndex = baseIndex + i;
            frame[i] = valueIndex < values.size() ? values.get(valueIndex) : 0.0F;
        }
        return frame;
    }

    private float[] lerp(float[] startValues, float[] endValues, float alpha) {
        int length = Math.min(startValues.length, endValues.length);
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = startValues[i] + ((endValues[i] - startValues[i]) * alpha);
        }
        return result;
    }

    private float[] slerpQuaternion(float[] startValues, float[] endValues, float alpha) {
        Quaternionf start = new Quaternionf(
                component(startValues, 0, 0.0F),
                component(startValues, 1, 0.0F),
                component(startValues, 2, 0.0F),
                component(startValues, 3, 1.0F)
        ).normalize();
        Quaternionf end = new Quaternionf(
                component(endValues, 0, 0.0F),
                component(endValues, 1, 0.0F),
                component(endValues, 2, 0.0F),
                component(endValues, 3, 1.0F)
        ).normalize();
        Quaternionf result = new Quaternionf(start).slerp(end, alpha).normalize();
        return new float[] {result.x, result.y, result.z, result.w};
    }

    private NodeTransform interpolate(NodeTransform start, NodeTransform end, float alpha) {
        float[] rotation = slerpQuaternion(
                new float[] {start.rx(), start.ry(), start.rz(), start.rw()},
                new float[] {end.rx(), end.ry(), end.rz(), end.rw()},
                alpha
        );
        return new NodeTransform(
                end.name(),
                lerp(start.tx(), end.tx(), alpha),
                lerp(start.ty(), end.ty(), alpha),
                lerp(start.tz(), end.tz(), alpha),
                rotation[0],
                rotation[1],
                rotation[2],
                rotation[3],
                lerp(start.sx(), end.sx(), alpha),
                lerp(start.sy(), end.sy(), alpha),
                lerp(start.sz(), end.sz(), alpha)
        );
    }

    private float lerp(float start, float end, float alpha) {
        return start + ((end - start) * alpha);
    }

    private float component(float[] values, int index, float fallback) {
        return index < values.length ? values[index] : fallback;
    }

    private float[] normalizeQuaternion(float x, float y, float z, float w) {
        float length = (float) Math.sqrt((x * x) + (y * y) + (z * z) + (w * w));
        if (length < 0.000001F) {
            return new float[] {0.0F, 0.0F, 0.0F, 1.0F};
        }
        return new float[] {x / length, y / length, z / length, w / length};
    }

    private NodeTransform resolveWorldPose(
            String nodeName,
            VisualInstance instance,
            RuntimeModelAsset asset,
            Map<String, NodeTransform> worldPoses
    ) {
        return WorldTransformResolver.resolveWorldTransform(
                asset,
                nodeName,
                name -> instance.localNodePoseCache().get(name)
                        .or(() -> asset.nodeTransform(name))
                        .orElse(NodeTransform.identity(name)),
                worldPoses
        );
    }
}
