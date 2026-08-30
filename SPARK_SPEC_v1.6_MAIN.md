機能仕様書：TheFastLaunch v-b1.6 高度メモリ管理・コンフィグ・CREATE_REGISTRIES並列化機能
1. 概要・背景
1.1 課題と背景
Minecraft 1.20.1 Forge / Fabric 環境において、400+ MOD を導入した大規模 MOD パックでは以下の重大なボトルネックが発生していた。


1. CREATE_REGISTRIES フェーズの長大停滞（約115秒のブロッキング）:
Forge / FML の起動ライフサイクルにおける CREATE_REGISTRIES イベントにおいて、400 以上の MOD が同期・直列にレジストリ枠組み（DeferredRegister / RegistryEvent 等）のインスタンス化およびイベントリスナー登録を行うため、メインスレッドが約 115 秒間完全に停止する。
2. 起動専用中間キャッシュのメモリ残留:
リソースパック解決、モデルベイク（ModelBakery、UnbakedModel 参照グラフ）、一時ファイル探索インデックスなど、起動完了後は二度と参照されないオブジェクトが JVM ヒープ上に残留し、常時メモリを数百 MB 〜 数 GB 圧迫し続ける。
3. 静的なGC・メモリ管理の限界:
ユーザーごとの割り当てメモリ量（例: 4GB〜16GB）に関わらず画一的な処理を行っていたため、メモリ逼迫時（OOMリスク時）に適切な自動キャッシュ退避・パージを行う適応型ガバナーが存在しなかった。
1.2 目的とスコープ
TheFastLaunch v-b1.6 では、以下の 4 つの柱により起動時間の劇的短縮とプレイ中のメモリ安定性を実現する。


   * AdaptiveMemoryGovernor: ヒープメモリの動的使用割合（used / max）に基づく適応型キャッシュパージ＆メモリ制御
   * Post-Init Cache Purge: 起動完了後の不要中間キャッシュ（ModelBakery / MultiPack 等）の安全な null 化とクリーンアップ
   * Parallel Registry Dispatcher: CREATE_REGISTRIES フェーズにおける 400+ MOD のレジストリ枠組み・リスナー登録処理のトポロジカル並列ディスパッチ
   * Flexible Configuration System: config/fastlaunch.json による各機能の ON/OFF、メモリ閾値（50%〜95%）、並列ワーカー数の自由な設定
2. システムアーキテクチャ・モジュール詳細設計
2.1 モジュール1: AdaptiveMemoryGovernor (適応型メモリガバナー)
   * 概要: JVM ヒープの割り当てメモリ使用率（used / max）をリアルタイムに評価し、メモリ圧迫度に応じた多段階パージポリシーを実行。
   * 評価ロジック:
   * Runtime.getRuntime().totalMemory() - freeMemory() と maxMemory() から現在使用率（%）を算出。
   * Level 1 (使用率 < 閾値): 軽微な一時バッファのみパージ。
   * Level 2 (使用率 >= 閾値, デフォルト 80%, 設定範囲 50%〜95%): ModelBakery の unbakedCache、中間テクスチャメタデータ、JSON パーサーキャッシュ等を積極パージ。
   * Level 3 (緊急モード >= 92%): 即座に明示的ソフトリファレンスクリアと世代別 GC 促進（System.gc() の安全なスケジューリング）を実行。
   * 安全設計: 弱参照・ソフト参照の適切なハンドリングを行い、ゲームプレイ中に再ロードが必要なリソースはオンデマンドで再生成可能な状態を維持。
2.2 モジュール2: 不要起動キャッシュ自動パージ (Post-Init Cache Purge)
   * 対象オブジェクト:
   * net.minecraft.client.resources.model.ModelBakery.unbakedCache / topLevelModels
   * net.minecraft.server.packs.resources.MultiPackResourceManager.packList の一時探索キャッシュ
   * Forge / FML の一時 Mod クラスメタデータインデックス
   * 実行タイミング: FMLLoadCompleteEvent / タイトル画面遷移直前のアイドルフレーム。
   * クリーンアッププロトコル: 参照の切断（null 化）およびコレクションの .clear() を安全に実行し、GC の到達可能グラフから完全に切り離す。
2.3 モジュール3: Parallel Registry Dispatcher (CREATE_REGISTRIES 並列化)
   * 課題分析: 各 MOD の DeferredRegister 呼び出しおよびレジストリ生成は、基本的に互いに独立しているが、一部 MOD 間で依存関係が存在する。
   * 並列ディスパッチ設計:
   * Phase A (依存グラフ構築): ModOrder / ModList から依存関係（Dependencies）を解析し、無依存の独立グループと依存グループにクラスタリング。
   * Phase B (並列ディスパッチ): 独立グループのレジストリ枠組み構築を ForkJoinPool または専用 WorkerPool（CPU コア数に応じて自動設定）で並列実行。
   * Phase C (同期バリア & 依存解決): 依存関係のあるレジストリをトポロジカル順序で高速直列解決。
   * スレッドセーフティ: Forge の RegistryManager に対するアクセスを細粒度ロックおよび ConcurrentHashMap 化により競合を防止。
2.4 モジュール4: 設定ファイル仕様 (config/fastlaunch.json)
   * ファイルパス: config/fastlaunch.json
   * 主要パラメータ:
   * enableMemoryGovernor: boolean (デフォルト true)
   * memoryGovernorThreshold: float (50.0 〜 95.0, デフォルト 80.0)
   * enablePostInitCachePurge: boolean (デフォルト true)
   * purgeModelBakeryIntermediate: boolean (デフォルト true)
   * enableParallelCreateRegistries: boolean (デフォルト true)
   * registryDispatchThreads: int (デフォルト 0 = 自動: CPU コア数 - 1)
   * enableDetailedLogging: boolean (デフォルト true)
3. 設定ファイル JSON スキーマ
{


  "$schema": "https://fastlaunch.mod/schemas/config.v1.6.json",


  "version": "1.6.0",


  "memoryGovernor": {


    "enabled": true,


    "thresholdPercent": 80.0,


    "emergencyThresholdPercent": 92.0,


    "logLevel": "INFO"


  },


  "cachePurge": {


    "enabled": true,


    "purgeModelBakery": true,


    "purgeResourceManagerTempBuffers": true,


    "purgeForgeStartupMetadata": true


  },


  "createRegistries": {


    "enableParallelDispatch": true,


    "maxWorkerThreads": 0,


    "threadNamePrefix": "FastLaunch-RegistryWorker-"


  },


  "logging": {


    "visualSummary": true,


    "logSavedMemory": true


  }


}
4. 性能目標と検証基準
評価指標
	改善前（Baseline: 400+ MOD）
	目標値（TheFastLaunch v-b1.6）
	達成判定基準
	CREATE_REGISTRIES 処理時間
	約 115 秒
	15 秒以下（約 85% 削減）
	起動ログのフェーズ時間計測
	起動完了後ヒープ使用量
	約 6.8 GB
	4.8 GB 〜 5.2 GB（約 25% 削減）
	VisualVM / プロファイラ実測
	メモリ不足（OOM）発生率
	割り当て 6GB 環境で高頻度発生
	ゼロ（適応型パージによる安定化）
	100 回連続起動・参加テスト
	レジストリ登録の完全性
	-
	100% 整合性維持（欠損・順序不正ゼロ）
	全ブロック/アイテム/レシピの整合検証
	5. ロールバックおよびフォールバック計画
   * 万が一特定の難読化・特殊 MOD で並列レジストリ登録に競合が発生した場合、config/fastlaunch.json の createRegistries.enableParallelDispatch: false に切り替えることで、即座にバニラ直列モードへ安全にフォールバック可能。