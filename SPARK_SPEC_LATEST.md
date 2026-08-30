機能仕様書：テスト_MemoryOptimizationTest.md
1. 概要・背景
1.1 目的
本ドキュメントは、Google Drive / Gmail API 連携パイプラインの導通確認および自動仕様書起票システムの動作検証を目的とする。併せて、TheFastLaunch におけるメモリ最適化モジュールの動的キャッシュ解放ロジックおよびテスト構成を定義する。
1.2 スコープ
* メモリ使用率（used/max）に基づく動的キャッシュ解放ロジックの定義
* テスト用設定ファイル構造（config/fastlaunch.json）の仕様
* 単体テスト・結合テストおよび正常性判定テストケースの策定
2. メモリ最適化モジュール仕様
2.1 動的キャッシュ解放ロジック (Adaptive Cache Eviction)
* 監視指標: JVM ヒープメモリ使用率 UsageRatio = (totalMemory - freeMemory) / maxMemory
* 解放トリガー:
   * UsageRatio < 0.70: 正常状態（通常キャッシュ保持）
   * 0.70 <= UsageRatio < 0.85: 警告状態（起動専用中間バッファおよび ModelBakery 未ベイクモデル参照の解放）
   * UsageRatio >= 0.85: 逼迫状態（ソフト参照の全解放および不要アセットキャッシュの強制パージ）
* 安全機構: プレイ中に再参照される可能性のあるリソースは破棄対象外とし、不整合や NullPointerException を防止。
2.2 テスト用設定ファイル仕様 (config/fastlaunch.json)
{


  "$schema": "https://fastlaunch.mod/schemas/config.test.v1.json",


  "testMode": true,


  "memoryGovernor": {


    "enabled": true,


    "warningThresholdPercent": 70.0,


    "criticalThresholdPercent": 85.0,


    "mockMemoryUsageRatio": 0.82


  },


  "cachePurge": {


    "purgeModelBakery": true,


    "purgeResourceManagerBuffers": true


  },


  "logging": {


    "logLevel": "DEBUG",


    "outputTestSummary": true


  }


}
3. 正常性判定テストケース
テストID
	テスト項目
	入力・事前条件
	期待される動作・結果
	判定基準
	TC-MEM-001
	メモリ使用率算出テスト
	ヒープ 8GB 割当 / 実使用 6.56GB
	使用率 82.0% が正確に算出されること
	誤差 0.1% 未満
	TC-MEM-002
	警告レベル解放テスト
	使用率 75% 模擬入力
	起動専用中間バッファが解放され、ヒープが削減されること
	メモリ削減確認＆例外ゼロ
	TC-MEM-003
	逼迫レベル解放テスト
	使用率 88% 模擬入力
	ソフト参照および一時キャッシュの強制パージが実行されること
	OOM 回避＆ログ出力確認
	TC-PIPE-001
	パイプライン往復テスト
	Gmail 経由の仕様書作成要求
	指定ファイル名で Google Drive の適正フォルダに自動作成されること
	配置完了＆通知正常
	4. 検証結果サマリー
* パイプライン導通: 正常
* ファイル配置先: 仕様書一覧 > Game > minecraft_optimization_mod
* 命名規則準拠: 機能仕様書：テスト_MemoryOptimizationTest.md