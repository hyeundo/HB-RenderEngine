package com.yourname.hb3d.render;

import com.yourname.hb3d.Hb3dMod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class IrisCompatibility {
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static Method getInstanceMethod;
    private static Method isShaderPackInUseMethod;
    private static boolean reflectionResolved;
    private static boolean reflectionFailureLogged;

    private IrisCompatibility() {
    }

    public static boolean isIrisLoaded() {
        return IRIS_LOADED;
    }

    public static boolean isShaderPackInUse() {
        if (!IRIS_LOADED) {
            return false;
        }
        try {
            resolveApi();
            Object irisApi = getInstanceMethod.invoke(null);
            return (boolean) isShaderPackInUseMethod.invoke(irisApi);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            if (!reflectionFailureLogged) {
                reflectionFailureLogged = true;
                Hb3dMod.LOGGER.warn("Iris was detected but its public API could not be queried; enabling compatibility mode", exception);
            }
            return true;
        }
    }

    private static void resolveApi() throws ClassNotFoundException, NoSuchMethodException {
        if (reflectionResolved) {
            return;
        }
        Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        getInstanceMethod = irisApiClass.getMethod("getInstance");
        isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
        reflectionResolved = true;
    }
}
