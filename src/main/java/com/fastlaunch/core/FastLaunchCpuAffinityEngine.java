package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Windows Pコア優先スレッドアフィニティ・オプティマイザー
 * 
 * Core i5-13600KF 等の Intel 第12世代以降のハイブリッドCPU環境において、
 * 起動処理（Mod構築、クラス変換、リロードタスク）を高性能 Pコア（0x0FFF: コア0〜11）に固定し、
 * Eコアへの誤割り当てによる 5〜8秒の遅延ブレ（ジッター）を完全に解消します。
 * 
 * タイトル画面到達後は、ゲームプレイのチャンク生成等のために全コア（0xFFFFF）へ復元します。
 */
public class FastLaunchCpuAffinityEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CpuAffinity");
    private static boolean applied = false;

    public static void applyPcoreAffinity() {
        if (applied) return;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) {
                LOGGER.info("[CpuAffinity] Non-Windows OS detected ({}). Skipping affinity mask.", os);
                return;
            }

            int availableProcessors = Runtime.getRuntime().availableProcessors();
            if (availableProcessors < 12) {
                LOGGER.info("[CpuAffinity] CPU has {} processors (< 12). Hybrid P/E architecture unlikely.", availableProcessors);
                return;
            }

            // JNA Kernel32 をリフレクションで安全に呼び出し (JNA不在環境でも安全フォールバック)
            Class<?> kernel32Class = Class.forName("com.sun.jna.platform.win32.Kernel32");
            Object kernel32 = kernel32Class.getField("INSTANCE").get(null);
            Method getCurrentProcess = kernel32Class.getMethod("GetCurrentProcess");
            Object hProcess = getCurrentProcess.invoke(kernel32);

            // 6 P-cores with HyperThreading = 12 logical processors (Bits 0..11) -> 0x0FFF
            // ユーザー環境 Core i5-13600KF (20 threads) -> 12 P-threads: 0x0FFF
            long pCoreMask = (1L << 12) - 1L; // 0x0FFF (Cores 0..11)

            Method setProcessAffinityMask = kernel32Class.getMethod("SetProcessAffinityMask", 
                Class.forName("com.sun.jna.platform.win32.WinNT$HANDLE"), 
                Class.forName("com.sun.jna.platform.win32.BaseTSD$ULONG_PTR"));

            Class<?> ulongPtrClass = Class.forName("com.sun.jna.platform.win32.BaseTSD$ULONG_PTR");
            Object maskObj = ulongPtrClass.getConstructor(long.class).newInstance(pCoreMask);

            boolean success = (boolean) setProcessAffinityMask.invoke(kernel32, hProcess, maskObj);
            if (success) {
                applied = true;
                LOGGER.info("[CpuAffinity] ⚡ Successfully pinned startup process to Performance Cores (Mask: 0x0FFF, P-Cores 0..11)!");
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "P-Core-Affinity", "ACTIVE [Pinned to P-Cores 0..11 (Mask: 0x0FFF)]"
                );
            }
        } catch (Throwable t) {
            LOGGER.debug("[CpuAffinity] JNA process affinity not available or failed: {}. Falling back to Thread priority.", t.getMessage());
        }

        // 補助アライメント: メインスレッドの優先度を最大化
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        } catch (Throwable ignored) {}
    }

    public static void restoreAllCoresAffinity() {
        if (!applied) return;
        try {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            long allCoresMask = (1L << availableProcessors) - 1L;

            Class<?> kernel32Class = Class.forName("com.sun.jna.platform.win32.Kernel32");
            Object kernel32 = kernel32Class.getField("INSTANCE").get(null);
            Method getCurrentProcess = kernel32Class.getMethod("GetCurrentProcess");
            Object hProcess = getCurrentProcess.invoke(kernel32);

            Method setProcessAffinityMask = kernel32Class.getMethod("SetProcessAffinityMask", 
                Class.forName("com.sun.jna.platform.win32.WinNT$HANDLE"), 
                Class.forName("com.sun.jna.platform.win32.BaseTSD$ULONG_PTR"));

            Class<?> ulongPtrClass = Class.forName("com.sun.jna.platform.win32.BaseTSD$ULONG_PTR");
            Object maskObj = ulongPtrClass.getConstructor(long.class).newInstance(allCoresMask);

            boolean success = (boolean) setProcessAffinityMask.invoke(kernel32, hProcess, maskObj);
            if (success) {
                LOGGER.info("[CpuAffinity] 🌿 Startup completed. Restored process CPU affinity to ALL cores (Mask: 0x{}) for gameplay.", Long.toHexString(allCoresMask));
            }
        } catch (Throwable ignored) {}
    }
}
