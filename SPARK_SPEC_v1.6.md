Antigravity メール送信連携機能 仕様書
1. 概要
1.1 目的
Google Antigravity（IDE / CLI / スクリプト環境）から、自律型AIエージェント（Gemini Spark）に対して非同期にタスク（仕様書作成、要件分析等）を依頼するためのメール送信機能の仕様を定義する。
1.2 背景
Antigravity環境での開発作業中に、要件定義の整理やドキュメント化タスクを分離し、Gmailトリガー経由でGemini Sparkへディスパッチすることで、開発作業とドキュメント作成の並行処理を実現する。
2. システム構成・連携フロー
2.1 全体アーキテクチャ
1. Antigravity 開発環境: CLI / Pythonスクリプトからタスク要件を抽出
2. Gmail 送信モジュール: sabuaka8190@gmail.com からGmail経由でメール送信
3. Gmail 受信トリガー: 送信元・件名ルールにより Gemini Spark がメールを検知
4. Gemini Spark: 要件を解析し、Google Docs形式の仕様書を自動作成
5. Google Drive: 完成した仕様書を指定フォルダに自動配置・共有リンクを発行
2.2 アクターと設定情報
* 送信元（クライアント）: Antigravity 環境上の Python スクリプト / CLI ツール
* 送信アカウント: sabuaka8190@gmail.com
* 受信エージェント: Gemini Spark（メール監視スケジュール設定済み）
* 成果物保存先: Google Drive（Google Docs 形式）
3. 機能要件
3.1 メール作成・フォーマット機能
* 件名命名規則:
   * プレフィックス: [仕様書作成] または [Spec-Request]
   * 形式: [仕様書作成] <機能名・対象モジュール名>
* 本文構成 (Markdown / プレーンテキスト):
   * 目的・背景: 作成したい機能の概要
   * 主要要件: 実装すべき機能、入力/出力要件、制約事項
   * 参考情報: コードスニペット、エンドポイント、データ構造、関連ファイル名
   * 出力形式指定: 保存先フォルダ、フォーマット（Google Docs / Markdown）
3.2 送信インターフェース
* CLI モード: コマンドラインから要件ファイルを指定して送信
   * 例: python send_task.py --title "認証機能" --file requirements.md
* Python API モード: Antigravity 内のスクリプトやエージェントからモジュールとして呼び出し
* 自動ディスパッチ: Antigravity のエージェントがコミットや設計メモを検知して自動送信
3.3 エラーハンドリング・リトライ
* 送信失敗（認証エラー、ネットワーク切断、レートリミット等）時のログ記録
* 指数バックオフによる自動リトライ（最大3回）
* 送信結果（Message ID、送信日時）のローカル記録
4. 非機能要件
4.1 セキュリティ・認証管理
* 認証情報はソースコード内に直接記述せず、環境変数（.env）または Secret Manager で管理する。
* 認証方式:
   * 推奨: Google OAuth 2.0 (Gmail API: https://www.googleapis.com/auth/gmail.send)
   * 簡易構成: Gmail アプリパスワード + TLS (SMTP: smtp.gmail.com:587)
4.2 性能・運用要件
* 送信レイテンシ: 1秒以内
* ログ出力: 送信時刻、宛先、件名、成否ステータスを出力
5. 実装サンプル（Python）
5.1 SMTP (アプリパスワード) を用いた軽量実装例
import os


import smtplib


from email.mime.multipart import MIMEMultipart


from email.mime.text import MIMEText


def send_spec_request(subject: str, body: str, to_email: str = None):


    smtp_host = "smtp.gmail.com"


    smtp_port = 587


    sender_email = os.environ.get("GMAIL_SENDER", "sabuaka8190@gmail.com")


    app_password = os.environ.get("GMAIL_APP_PASSWORD")


    recipient = to_email or sender_email


    if not app_password:


        raise ValueError("環境変数 GMAIL_APP_PASSWORD が設定されていません。")


    msg = MIMEMultipart()


    msg["From"] = sender_email


    msg["To"] = recipient


    msg["Subject"] = f"[仕様書作成] {subject}"


    msg.attach(MIMEText(body, "plain", "utf-8"))


    with smtplib.SMTP(smtp_host, smtp_port) as server:


        server.starttls()


        server.login(sender_email, app_password)


        server.send_message(msg)


    


    print(f"メール送信完了: {msg['Subject']}")
6. テスト・運用手順
1. 事前準備:
   * Google アカウントの2段階認証を有効化し、アプリパスワードを発行。
   * Antigravity ワークスペースの環境変数に GMAIL_APP_PASSWORD を設定。
2. 送信テスト:
   * テスト用スクリプトを実行し、[仕様書作成] テスト機能 の件名で送信。
3. 連携確認:
   * Gemini Spark がメールを受信し、Google Drive に仕様書ドキュメントが生成されることを確認。