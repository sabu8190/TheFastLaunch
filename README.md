<div align="center">
  <img src="icon.png" width="160" height="160" alt="TheFastLaunch Logo" style="border-radius: 24px;" />
  <h1>🚀 TheFastLaunch (v-b1.8.2)</h1>
  <p><b>Minecraft 1.20.1 Forge 向け 起動プロファイリング ＆ パラレル最適化ユーティリティ MOD</b><br>
  <b>Startup Profiling & Parallel Optimization Utility for Minecraft 1.20.1 Forge</b><br>
  <i>Built with Google DeepMind Advanced Agentic AI "Antigravity"</i></p>
  <p>
    <a href="#-日本語-ドキュメント">🇯🇵 日本語</a> |
    <a href="#-english-documentation">🇺🇸 English</a>
  </p>
</div>

---

<a id="-日本語-ドキュメント"></a>
## 🇯🇵 日本語 ドキュメント

### 📖 概要 (Overview)

**TheFastLaunch** は、大規模 ModPack（200+ Mod）環境において発生する長大な起動待機時間のボトルネックを特定・プロファイリングし、Windows OS による **「応答なし（白画面 / Ghost Window）」フリーズの防止** や、Mod の並列処理化・キャッシュ最適化を行うオープンソースの最適化・診断 MOD です。

v-b1.8.2 では、すべてのダミー処理・未実測の数値を完全撤去し、**「100% 実測値に基づく正直なプロファイリング」** と **「マルチサーバーとの完全なレジストリ整合性」** を担保する堅牢なアーキテクチャへと刷新されました。

---

### 🛡️ 誠実性宣言 (Honest Metrics & Zero Dummy Data)

本 Mod は、実態のない宣伝用ログやハードコードされた短縮数値（「〇〇秒短縮！」といった固定表記）を一切排除しています。

* **実測値のみの記録**: モデルベイク、アセット解凍、レジストリ構築などの処理時間は、すべて実行環境のタイマー（ミリ秒 / マイクロ秒単位）で計測された実測差分のみをログに出力します。
* **稼働モジュールのみの可視化**: 実際に動作した最適化モジュールやプロファイラのみをアクティブ機能としてレポートします。未導入 Mod 向けの Mixin や待機状態の機能が誤認を招くログを吐くことはありません。
* **データ完全性の保証**: レジストリ同期の順序やアセットの整合性を最優先とし、高速化のためにゲームの安全性を犠牲にすることはありません。

---

### 🚀 主な機能・アーキテクチャ (Core Features)

#### 1. 🪟 Windows OS「応答なし（白画面）」物理防止 (`DisableProcessWindowsGhosting`)
Windows OS は、メインスレッドが一時的にビジー状態になると、ウィンドウを「応答なし」と判定して半透明の Ghost Window を被せます。TheFastLaunch は Win32 JNA API を通じて `DisableProcessWindowsGhosting` を呼び出し、重い Mod ロード中であっても Windows による不必要な白画面化を防止します。

#### 2. ⚡ JsonThings マルチコア並列パース ＆ 決定論的同期
大量の JSON 定義を読み込む `JsonThings` のパース処理を `ForkJoinPool` で並列化。アイテムやブロックの登録順序（`builders` および `buildersByName`）を決定論的（Deterministic）に完全同期することで、マルチサーバー接続時のレジストリ不一致（Registry Desync）やパケット切断を防ぎます。

#### 3. 🧩 MBD2 / SimpleJson 並列リソース処理
* **MBD2 (Modular Block Data 2)**: 膨大な NBT ファイルや設定データの読み込みをマルチスレッド（`CompletableFuture`）で並列実行し、ディスク I/O 待機時間を短縮します。
* **SimpleJson**: リソース定義のスキャン処理を並列ストリーム化し、高速化を図ります。

#### 4. 📦 JAR 分離型アセット事前展開ストリーミング (`ResourceZipPreExtract`)
Minecraft が起動時に Mod JAR（ZIP）からテクスチャやモデルを都度解凍するオーバーヘッドを軽減するため、ローカルの一時フォルダに事前解凍してダイレクト I/O で読み込みます。キャッシュは Mod JAR ごとの専用ディレクトリに完全に分離され、他 Mod とのアセット衝突（テクスチャの上書き・化け）を防ぎます。

#### 5. 🗡️ Tinkers' Construct JEI バリアント動的フィルタ
Tinkers' Construct などのツール系 Mod が生成する天文学的な組み合わせバリアント（数万〜数十万件）によって JEI（Just Enough Items）が極端に重くなる現象を検知し、安全にフィルタリングして起動時のメモリ逼迫を緩和します。

#### 6. 🔍 レシピビューア（JEI / EMI / JEMI）監視 ＆ 競合ガード
* **JEI Forge GUI プロファイリング**: GUI ハンドラ登録の所要時間を追跡。
* **EMI 検索インデックスベイク監視**: EMI レシピおよびローカライズ検索インデックスの構築時間をプロファイリング。
* **JEMI / Mekanism レシピブリッジ監視**: 化学物質やカスタムスロットのスタック処理が正常に行われているかを安全に監視。

#### 7. 🧹 起動時キャッシュ解放 ＆ メモリガバナー
タイトル画面表示（`FMLLoader.isLoadedComplete()`）を検知した段階で、起動時のみに使用された一時バッファや展開キャッシュを安全にパージ。GC 前後のヒープ使用量差分（MB）を実測してログに記録します。

#### 8. 📊 マイクロ秒・ミリ秒精度の完全実測プロファイラー (`FastLaunchSuccessLogger`)
起動プロセス完了時に、実際に稼働した最適化モジュール一覧と、各フェーズで測定された実測処理時間をコンソールおよびログに出力します。

```text
[TheFastLaunch] ==================== FastLaunch Active Diagnostics ====================
[TheFastLaunch] [Active Modules]
[TheFastLaunch]  - Win32 Ghost Window Prevention : ACTIVE (DisableProcessWindowsGhosting enabled)
[TheFastLaunch]  - JsonThings Multi-Core Parser   : ACTIVE (Synchronized Deterministic)
[TheFastLaunch]  - MBD2 Parallel NBT Loader       : ACTIVE (Async Completed)
[TheFastLaunch]  - SimpleJson Parallel Parser     : ACTIVE (Worker Pool)
[TheFastLaunch]  - ModelBakery Parallel Profile   : ACTIVE (Profiled 1420ms)
[TheFastLaunch]  - JEI Forge GUI Handler          : ACTIVE (Registered)
[TheFastLaunch] [Measured Phase Timings]
[TheFastLaunch]  - ModelBakery Bake               : 1420 ms
[TheFastLaunch]  - JsonThings Parsing             : 850 ms
[TheFastLaunch]  - MBD2 NBT Load                  : 320 ms
[TheFastLaunch]  - Startup Cache Purge            : Reclaimed 128 MB heap
[TheFastLaunch] =======================================================================
```
*(※ 上記の数値は実行環境における実測例です。環境や導入 Mod 構成によって値は変動します)*

---

<a id="-english-documentation"></a>
## 🇺🇸 English Documentation

### 📖 Overview

**TheFastLaunch** is an open-source startup profiling and optimization mod for Minecraft 1.20.1 Forge. Designed for heavy modpacks (200+ mods), it identifies launch bottlenecks, prevents Windows "Not Responding" ghost windows, parallelizes CPU-bound parsing routines, and optimizes asset streaming.

In **v-b1.8.2**, all unmeasured claims, placeholder routines, and dummy logs have been entirely replaced with **100% measured, microsecond-level profiling** and **deterministic registry synchronization**, ensuring complete stability and zero multiplayer desyncs.

---

### 🛡️ Integrity & Transparency Statement

TheFastLaunch strictly adheres to an honest logging policy:
* **Real-time Measurement Only**: Timings for model baking, asset extraction, JSON parsing, and registry dispatch are strictly captured using system timers. No hardcoded or fabricated time-saving claims are permitted.
* **Active Module Reporting**: The diagnostic summary at the title screen only displays modules that actually executed in your current session.
* **Deterministic Stability**: Parallel routines are engineered with strict deterministic sorting to maintain absolute registry ordering and multiplayer parity.

---

### 🚀 Core Architecture & Features

#### 1. 🪟 Windows OS Ghost Window Prevention (`DisableProcessWindowsGhosting`)
Windows OS automatically overlays a translucent "Not Responding" ghost window when the main thread does not poll the message queue within 5 seconds. TheFastLaunch invokes Win32 JNA `DisableProcessWindowsGhosting` to physically prevent Windows from freezing the game window during intense mod loading.

#### 2. ⚡ JsonThings Multi-Core Parser with Deterministic Ordering
Parallelizes JsonThings JSON parsing across the `ForkJoinPool`. Item and block builders (`builders` and `buildersByName`) are synchronized and deterministically ordered, preventing race conditions, ID shifts, and multiplayer connection drops (such as Tinkers' add-on item synchronization).

#### 3. 🧩 MBD2 & SimpleJson Parallel Resource Loading
* **Modular Block Data 2 (MBD2)**: Asynchronously reads heavy NBT configuration files via `CompletableFuture`, minimizing disk I/O wait times.
* **SimpleJson**: Parallelizes JSON resource scanning across available worker threads.

#### 4. 📦 Isolated Asset Pre-Extraction Streaming (`ResourceZipPreExtract`)
Pre-extracts textures and model assets from mod JARs into isolated local cache directories per JAR (`.fastlaunch_extracted_assets/<jar_name>/`). This eliminates on-the-fly ZIP decompression overhead while strictly preventing asset collisions across different mods.

#### 5. 🗡️ Tinkers' Construct Dynamic JEI Variant Filtering
Monitors and safely filters combinatorial tool/weapon variants in Tinkers' Construct and its add-ons to prevent excessive memory consumption and JEI indexing freezes.

#### 6. 🔍 Recipe Viewer Diagnostics & Compatibility Guards (JEI / EMI / JEMI)
* **JEI Forge GUI Profiling**: Tracks handler registration duration.
* **EMI Search & Recipe Indexing**: Measures bake times for EMI search indices and localized databases.
* **JEMI / Mekanism Bridge**: Safely verifies custom chemical and stack handling without crashes.

#### 7. 🧹 Startup Memory Purging & Heap Tracking
Releases temporary startup buffers and pre-extraction caches once the title screen is reached (`FMLLoader.isLoadedComplete()`), recording the actual heap memory reclaimed in megabytes.

#### 8. 📊 Real-Time Diagnostic Logger (`FastLaunchSuccessLogger`)
Upon reaching the main menu, outputs a clean, honest breakdown of active modules and their measured execution times.

---

## 🖥️ 動作環境 / Requirements

* **Minecraft**: 1.20.1
* **Mod Loader**: Minecraft Forge 47.4.0+ (Forge 47.4.21 recommended)
* **Java**: Java 17 (64-bit)
* **OS**: Windows 10 / 11 (64-bit) *(Win32 ghost window prevention is Windows-specific; other optimizations run cross-platform)*
* **Side**: Client-only (Server does not require this mod; fully compatible with multiplayer servers)

---

## 🛠️ ソースコードからのビルド / Building from Source

```bash
git clone https://github.com/sabu8190/TheFastLaunch.git
cd TheFastLaunch
./gradlew build
```

The compiled JAR will be generated at `build/libs/TheFastLaunch-b1.8.2-1.20.1.jar`.

---

## 📜 ライセンス / License

This project is licensed under the **MIT License**. You are completely free to use, modify, distribute, and include it in modpacks.

---

## 🤖 開発クレジット / Credits

* **Author & Lead Developer**: [saburou8190](https://github.com/sabu8190)
* **AI Pair Programming Assistant**: **Google DeepMind Antigravity (Advanced Agentic Coding)**
