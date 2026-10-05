package com.fastlaunch.platform;

import java.nio.file.Path;

/**
 * プラットフォーム抽象化サービスインターフェース。
 * Forge, Fabric, NeoForge 間の差異を統一的に吸収し、
 * コアコードの 100% 共有・ポータビリティを実現する。
 */
public interface IPlatformHelper {

    /**
     * 現在稼働中のプラットフォーム名を返却 ("Forge", "Fabric", "NeoForge" 等)。
     */
    String getPlatformName();

    /**
     * 指定した modId の MOD が導入されているかを判定。
     */
    boolean isModLoaded(String modId);

    /**
     * 開発環境 (IDE等) で実行されているかを判定。
     */
    boolean isDevelopmentEnvironment();

    /**
     * ゲームディレクトリ (Minecraft インスタンスのルート) の Path を返却。
     */
    Path getGameDir();

    /**
     * config ディレクトリの Path を返却。
     */
    Path getConfigDir();

    /**
     * クライアント環境として実行されているかを判定。
     */
    default boolean isClient() {
        return true;
    }
}
