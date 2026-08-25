# Screenshot Stitcher 📱✨

[![Android CI](https://github.com/your-username/screenshot-stitcher/actions/workflows/android.yml/badge.svg)](https://github.com/your-username/screenshot-stitcher/actions/workflows/android.yml)
[![Release APK](https://github.com/your-username/screenshot-stitcher/actions/workflows/release.yml/badge.svg)](https://github.com/your-username/screenshot-stitcher/actions/workflows/release.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-purple.svg)](https://developer.android.com/jetpack/compose)

**Screenshot Stitcher** は、複数枚の連続したスクリーンショットや画像を自動で解析し、スクロール時の**重複部分をピクセル単位で高精度に検出・自動カットして 1 枚のシームレスな縦長画像に結合**する Android アプリケーションです。

チャット履歴、長文記事、SNSのタイムライン、Webサイトのキャプチャなどを、簡単かつ綺麗に1枚の画像としてまとめ、端末のギャラリーへの保存やSNSへの共有が可能です。

---

## 🌟 主な機能 (Key Features)

- 🔍 **高精度・高速な重複領域自動検出 (Auto Overlap Detection)**
  - ダウンサンプリング輝度相関解析とフル解像度ラインスキャンを組み合わせた独自アルゴリズムにより、スクロール時の重なり幅をピクセル単位で瞬時に算出。
- 🛠️ **インタラクティブなシーム微調整 (Manual Seam Fine-Tuning)**
  - 自動検出結果を確認しながら、結合部ごとに `±1px` / `±10px` 単位で手動調整可能。結合部のプレビューを見ながら微細なズレを完璧に補正できます。
- ✂️ **ステータスバー・ナビゲーションバーの自動トリミング**
  - スクリーンショットごとに重複して写り込む時計・バッテリーアイコン（ステータスバー）や、下部のジェスチャーバー（ナビゲーションバー）を設定した高さで自動除外。
- 🖼️ **最新 Photo Picker による安全・快適な画像選択**
  - Android 13+ の標準 Photo Picker (`PickMultipleVisualMedia`) に対応。不要なストレージ権限要求なしで、複数画像をスムーズにインポート。
- 🎨 **Material Design 3 (M3) 完全準拠のモダン UI**
  - システムカラーに追従する Dynamic Color、ダークモード/ライトモード完全対応。
  - ドラッグ感覚の並び替え・個別削除・リアルタイム情報表示。
- 🔍 **フル解像度インタラクティブプレビュー**
  - 結合後の長尺画像をピンチイン/ピンチアウトで拡大・縮小、パン移動して細部まで確認可能。
- 💾 **ワンタップ保存 & シームレス共有**
  - Android MediaStore API 経由で Pictures フォルダに安全保存。
  - FileProvider 経由で LINE、X (旧Twitter)、Slack、Gmail 等へ直接共有。
- 🛡️ **大規模画像・テクスチャ上限セーフガード**
  - Android GPU のテクスチャ上限（32,768px）を超える超長尺スクリーンショットでも、自動セーフスケールにより OOM (Out Of Memory) や描画クラッシュを回避。

---

## 📲 使い方 (How to Use)

```
[1. 画像を選択] ──> [2. 重複を自動検出] ──> [3. 必要に応じて微調整] ──> [4. 結合・保存/共有]
```

1. **画像の追加:**
   - メイン画面の「画像を選択」ボタンから、連続するスクリーンショットを撮影順（または複数一括）に選択します。
2. **自動検出 & 順序確認:**
   - アプリが各画像間の重複ピクセル数を自動計算し、結合部バッジに表示します。
   - 上へ/下へボタンで画像の並び順を変更できます。
3. **微調整（オプション）:**
   - 結合部の「調整」ボタンをタップすると、上下の境界線を並べて見ながらピクセル単位でオフセットを調整できます。
   - 画面右上の設定アイコンから、ステータスバーやナビゲーションバーのトリミング高さ、出力フォーマット（PNG / JPEG / WEBP）を変更できます。
4. **結合と保存:**
   - 「〇枚を結合」ボタンをタップすると、瞬時に高解像度結合が実行されます。
   - プレビュー画面で仕上がりを確認し、「ギャラリーに保存」または「共有」をタップします。

---

## 🏗️ 技術スタック & アーキテクチャ (Tech Stack & Architecture)

- **Language:** Kotlin 2.0+
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture Principles
- **Asynchronous & Reactive:** Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle`
- **Image Loading:** Coil 2.x
- **Testing:** Robolectric, Roborazzi (Screenshot Testing), JUnit 4
- **Build System:** Gradle (Kotlin DSL, `build.gradle.kts`)

### プロジェクト構成

```
app/src/main/java/com/example/
├── MainActivity.kt               # メインアクティビティ
├── model/
│   └── StitchModels.kt          # データモデル (ImageItem, SeamConfig, Settings, UiState)
├── engine/
│   └── StitchEngine.kt          # 重複検出アルゴリズム & ビットマップ結合処理
├── viewmodel/
│   └── StitchViewModel.kt       # UI状態管理・非同期処理・MediaStore/共有ロジック
└── ui/
    ├── MainScreen.kt            # メイン画面
    ├── theme/                   # Material 3 テーマ定義 (Theme, Color, Type)
    └── components/
        ├── ImageItemCard.kt     # サムネイル・メタデータ・並替/削除カード
        ├── SeamBadge.kt         # 結合部オーバーラップ表示バッジ
        ├── SeamFineTuneDialog.kt# シーム手動調整ダイアログ
        ├── SettingsBottomSheet.kt# 各種設定シート (トリミング/フォーマット)
        └── ResultView.kt        # 結合結果プレビュー (ズーム/パン対応)
```

---

## 🚀 ビルド & 開発手順 (Build & Development)

### 前提条件
- Android Studio Ladybug (2024.2.1) 以降 推奨
- JDK 17 (Java Development Kit 17)
- Android SDK (API 36 / Build-Tools 35.0.0+)

### リポジトリのクローンとビルド

```bash
# クローン
git clone https://github.com/your-username/screenshot-stitcher.git
cd screenshot-stitcher

# デバッグAPKのビルド
./gradlew assembleDebug

# 単体テストの実行
./gradlew testDebugUnitTest
```

生成された APK は `app/build/outputs/apk/debug/app-debug.apk` に出力されます。

---

## ⚙️ CI/CD (GitHub Actions)

本リポジトリには、GitHub Actions による自動ビルド & リリースパイプラインが組み込まれています。

### 1. デバッグ APK 自動ビルド (`android.yml`)
- **トリガー:** `main`, `master`, `develop` ブランチへの `push` または `pull_request`
- **内容:** コードのコンパイルとテストを実行し、生成された `app-debug.apk` を Actions アーティファクトとして 30 日間保存します。

### 2. リリース APK 自動署名・GitHub Releases 発行 (`release.yml`)
- **トリガー:** `v*` 形式のタグ push（例: `v1.0.0`）、または GitHub Actions タブからの手動実行 (`workflow_dispatch`)
- **内容:** リリースビルドを実行し、GitHub Secrets に登録されたキーストアで自動署名を行い、署名済みリリース APK を GitHub Releases にアタッチします。

#### 必要な GitHub Secrets 設定
リリースワークフローを利用する場合、リポジトリの **Settings → Secrets and variables → Actions** に以下を登録してください：

| Secret 名 | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | リリース用キーストア (`.jks` / `.keystore`) を Base64 エンコードした文字列 |
| `ANDROID_KEYSTORE_PASSWORD` | キーストアのパスワード |
| `ANDROID_KEY_ALIAS` | キーのエイリアス名 |
| `ANDROID_KEY_PASSWORD` | キーのパスワード |

---

## 📄 ライセンス (License)

本プロジェクトは Apache-2.0 ライセンスの下で公開されています。
