# OkoTabBridge for NeoForge

TAB 6.2.1 / Minecraft 26.2 / NeoForge 26.2.0.88 / Java 25向け。おこ鯖のTAB設定で使う %bungee_total% と %bungee_<server>% を、Minecraftの通常のstatus応答で補います。TAB本体とLuckPermsによる称号表示をそのまま使います。追加のスコアボードやHUDはありません。

設定は config/oko-tab-bridge.json。networkはVelocity入口、serversは各バックエンドのstatus接続先です。バックグラウンドで15秒ごとに取得し、TABの描画中は保存した値を読みます。初回取得中は「…」、取得失敗時は「—」を表示し、失敗を0人と扱いません。サーバー名、接続先、周期を変えた場合はDQMを再起動してください。

TABは config/tab に置きます。mainのTAB設定を元に、DQMのサーバー名と人数表示を変更し、scoreboard、bossbar、belowname、playerlist objective、scoreboard teamsを無効にします。NeoForgeではPaperのPlaceholderAPIとEssentialsを利用できないため、既存のAFK placeholderを設定から外します。proxy-supportのRedisBungeeもこのadapterの人数取得には使いません。

## Build

./gradlew build（Java25）。公式TAB API6.1.2をcompileOnlyで使用し、APIやTAB本体は配布jarへ同梱しません。TAB6.2.1と一緒にサーバー側へ置きます。

単体試験は実TCP接続でstatusリクエスト、人数、切断・不正応答・タイムアウトを確認します。native TABと既存DQM構成での結合確認も行います。
