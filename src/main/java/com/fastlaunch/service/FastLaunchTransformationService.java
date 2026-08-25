package com.fastlaunch.service;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModLauncher 最上位最初期レイヤーで動作する ITransformationService。
 * スレッド負荷ゼロで Win32 API DisableProcessWindowsGhosting() を JVM 起動直後に直接発動。
 */
public class FastLaunchTransformationService implements ITransformationService {
    private static final AtomicBoolean GHOSTING_DISABLED = new AtomicBoolean(false);

    @Override
    public @NotNull String name() {
        return "fastlaunch_transform";
    }

    @Override
    public void initialize(IEnvironment environment) {
        disableWindowsGhosting();
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        disableWindowsGhosting();
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.emptyList();
    }

    public static void disableWindowsGhosting() {
        if (!GHOSTING_DISABLED.compareAndSet(false, true)) {
            return;
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                try {
                    // JNA NativeLibrary 経由で User32.DisableProcessWindowsGhosting() を直接呼び出し
                    Class<?> nativeLibraryClass = Class.forName("com.sun.jna.NativeLibrary");
                    Method getInstanceMethod = nativeLibraryClass.getMethod("getInstance", String.class);
                    Object user32Lib = getInstanceMethod.invoke(null, "user32");

                    Method getFunctionMethod = nativeLibraryClass.getMethod("getFunction", String.class);
                    Object func = getFunctionMethod.invoke(user32Lib, "DisableProcessWindowsGhosting");

                    Method invokeVoidMethod = func.getClass().getMethod("invokeVoid", Object[].class);
                    invokeVoidMethod.invoke(func, (Object) new Object[]{});

                    System.out.println("=======================================================================");
                    System.out.println("[FastLaunch/ITransformationService] 🛡️ Win32 API: DisableProcessWindowsGhosting() ACTIVE!");
                    System.out.println("[FastLaunch/ITransformationService] 🛡️ ModLauncher Pre-Init: Ghost Window completely disabled (0% CPU overhead)!");
                    System.out.println("=======================================================================");
                } catch (Throwable t) {
                    System.out.println("[FastLaunch/ITransformationService] User32 JNA invocation note: " + t.getMessage());
                }
            }
        } catch (Throwable ignored) {}
    }
}
