package com.yourname.hb3d.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class VisualInstanceTransformTest {
    @Test
    void interpolatesPositionScaleAndShortestYawPath() {
        VisualInstance instance = new VisualInstance(7);
        instance.setWorldTransform(0.0, 10.0, 0.0, 170.0F, 0.0F, 1.0F, true);
        instance.setWorldTransform(10.0, 20.0, 30.0, -170.0F, 20.0F, 3.0F, false);
        instance.advanceWorldTransform();

        VisualInstance.WorldTransform transform = instance.interpolatedWorldTransform(0.5F);
        assertEquals(5.0, transform.x(), 0.0001);
        assertEquals(15.0, transform.y(), 0.0001);
        assertEquals(15.0, transform.z(), 0.0001);
        assertEquals(180.0F, transform.yaw(), 0.0001F);
        assertEquals(10.0F, transform.pitch(), 0.0001F);
        assertEquals(2.0F, transform.scale(), 0.0001F);
    }

    @Test
    void teleportSnapsBothSnapshots() {
        VisualInstance instance = new VisualInstance(8);
        instance.setWorldTransform(1.0, 2.0, 3.0, 45.0F, 5.0F, 2.0F, true);

        VisualInstance.WorldTransform transform = instance.interpolatedWorldTransform(0.0F);
        assertEquals(1.0, transform.x(), 0.0001);
        assertEquals(45.0F, transform.yaw(), 0.0001F);
        assertEquals(2.0F, transform.scale(), 0.0001F);
    }
}
