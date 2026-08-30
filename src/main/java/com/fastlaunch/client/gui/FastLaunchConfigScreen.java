package com.fastlaunch.client.gui;

import com.fastlaunch.config.FastLaunchConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Minecraft の Mods 画面から開く TheFastLaunch ゲーム内設定 GUI 画面。
 * バニラ / Forge 標準 Widget のみを用いた超軽量・依存関係ゼロの設計。
 */
public class FastLaunchConfigScreen extends Screen {
    private final Screen parentScreen;

    private double purgeThreshold;
    private double criticalThreshold;
    private boolean startupCachePurge;
    private boolean createRegistriesParallel;
    private int workerThreads;

    public FastLaunchConfigScreen(Screen parentScreen) {
        super(Component.literal("TheFastLaunch Configuration"));
        this.parentScreen = parentScreen;

        // 現在の設定値をロード
        this.purgeThreshold = FastLaunchConfig.MEMORY_PURGE_THRESHOLD_PERCENT;
        this.criticalThreshold = FastLaunchConfig.CRITICAL_PURGE_THRESHOLD_PERCENT;
        this.startupCachePurge = FastLaunchConfig.ENABLE_STARTUP_CACHE_PURGE;
        this.createRegistriesParallel = FastLaunchConfig.ENABLE_CREATE_REGISTRIES_PARALLEL;
        this.workerThreads = FastLaunchConfig.PARALLEL_WORKER_THREADS;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 40;
        int buttonWidth = 310;
        int buttonHeight = 20;
        int spacing = 24;

        // 1. メモリパージ基準割合スライダー (50% ~ 95%)
        this.addRenderableWidget(new AbstractSliderButton(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight, 
                Component.literal("🧹 メモリパージ基準: " + (int) purgeThreshold + "%"), (purgeThreshold - 50.0) / 45.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("🧹 メモリパージ基準: " + (int) purgeThreshold + "% (通常時GC抑制)"));
            }

            @Override
            protected void applyValue() {
                purgeThreshold = Math.round(50.0 + this.value * 45.0);
            }
        }).setTooltip(Tooltip.create(Component.literal("メモリ使用率がこの値を超えた時のみ不要キャッシュを解放し1GB+回収します (デフォルト: 80%)")));

        // 2. 緊急パージ基準割合スライダー (85% ~ 99%)
        this.addRenderableWidget(new AbstractSliderButton(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight, 
                Component.literal("🚨 緊急パージ基準: " + (int) criticalThreshold + "%"), (criticalThreshold - 85.0) / 14.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("🚨 緊急パージ基準: " + (int) criticalThreshold + "% (OOM緊急ガード)"));
            }

            @Override
            protected void applyValue() {
                criticalThreshold = Math.round(85.0 + this.value * 14.0);
            }
        }).setTooltip(Tooltip.create(Component.literal("クラッシュ寸前のメモリ逼迫時にソフト参照を緊急パージします (デフォルト: 92%)")));

        // 3. 起動後キャッシュ安全解放トグルボタン
        Button cacheButton = Button.builder(Component.literal("🗑️ 起動中間キャッシュ解放: " + (startupCachePurge ? "ON (推奨)" : "OFF")), btn -> {
            startupCachePurge = !startupCachePurge;
            btn.setMessage(Component.literal("🗑️ 起動中間キャッシュ解放: " + (startupCachePurge ? "ON (推奨)" : "OFF")));
        }).bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
          .tooltip(Tooltip.create(Component.literal("タイトル画面到達後にModelBakery等の不要な起動バッファを破棄します")))
          .build();
        this.addRenderableWidget(cacheButton);

        // 4. CREATE_REGISTRIES 並列化トグルボタン
        Button parallelButton = Button.builder(Component.literal("🚀 レジストリ並列構築: " + (createRegistriesParallel ? "ON (推奨)" : "OFF")), btn -> {
            createRegistriesParallel = !createRegistriesParallel;
            btn.setMessage(Component.literal("🚀 レジストリ並列構築: " + (createRegistriesParallel ? "ON (推奨)" : "OFF")));
        }).bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight)
          .tooltip(Tooltip.create(Component.literal("400+ MODのレジストリ枠組み構築を全コアで並列化し115秒停滞を解消します")))
          .build();
        this.addRenderableWidget(parallelButton);

        // 5. 並列ワーカースレッド数スライダー (1 ~ CPU最大コア数)
        int maxCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        this.addRenderableWidget(new AbstractSliderButton(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight, 
                Component.literal("⚡ 並列ワーカースレッド数: " + workerThreads + " Cores"), (double) (workerThreads - 1) / Math.max(1, maxCores - 1)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("⚡ 並列ワーカースレッド数: " + workerThreads + " Cores (利用可能: " + maxCores + ")"));
            }

            @Override
            protected void applyValue() {
                workerThreads = Math.max(1, (int) Math.round(1.0 + this.value * (maxCores - 1)));
            }
        }).setTooltip(Tooltip.create(Component.literal("アセット読み込みや検索インデックス作成に割り当てるCPUスレッド数")));

        // 下部ボタン群 (初期値に戻す / 保存して適用 / キャンセル)
        int bottomY = this.height - 32;
        int actionBtnWidth = 100;

        // リセット
        this.addRenderableWidget(Button.builder(Component.literal("初期値に戻す"), btn -> {
            this.purgeThreshold = 80.0;
            this.criticalThreshold = 92.0;
            this.startupCachePurge = true;
            this.createRegistriesParallel = true;
            this.workerThreads = maxCores;
            this.rebuildWidgets();
        }).bounds(centerX - 155, bottomY, actionBtnWidth, buttonHeight).build());

        // 保存して適用
        this.addRenderableWidget(Button.builder(Component.literal("保存して適用"), btn -> {
            FastLaunchConfig.MEMORY_PURGE_THRESHOLD_PERCENT = this.purgeThreshold;
            FastLaunchConfig.CRITICAL_PURGE_THRESHOLD_PERCENT = this.criticalThreshold;
            FastLaunchConfig.ENABLE_STARTUP_CACHE_PURGE = this.startupCachePurge;
            FastLaunchConfig.ENABLE_CREATE_REGISTRIES_PARALLEL = this.createRegistriesParallel;
            FastLaunchConfig.PARALLEL_WORKER_THREADS = this.workerThreads;
            FastLaunchConfig.save();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }).bounds(centerX - 50, bottomY, actionBtnWidth, buttonHeight).build());

        // キャンセル
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }).bounds(centerX + 55, bottomY, actionBtnWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "§7TheFastLaunch 高速化＆適応型メモリガバナー設定", this.width / 2, 27, 0xAAAAAA);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
}
