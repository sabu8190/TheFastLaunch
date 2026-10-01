# 🚀 TheFastLaunch v-b1.9.1 Changelog

---

## 🇺🇸 English (For GitHub Release Notes)

# 🚀 TheFastLaunch v-b1.9.1 (Hotfix: Forgery / Fabrication Compatibility)

**TheFastLaunch v-b1.9.1** is a critical hotfix that addresses Mixin injection failures when running alongside **Forgery** (or Fabrication-family mods).

---

### 🌟 Key Highlights in v-b1.9.1

#### 🛡️ 1. Dynamic Forgery / Fabrication Detection & Safe Preload Bypass
* **Fixes Critical Injection Failure on `net.minecraft.world.entity.Entity` (Issue #4)**:
  * Resolved an issue where background early class preloading of vanilla `Player` / `Entity` classes forced early classloading before `Forgery`'s `MixinEntity` could inject its `@ModifyArg` targets.
  * Added dynamic classpath and mod directory scanning (`isForgeryPresent()`). When Forgery / Fabrication is detected, early background preloading for entity classes is safely skipped.
  * Dynamically disables any potentially interfering Mixins when Forgery is present via `FastLaunchMixinPlugin.shouldApplyMixin()`.

#### ⚡ 2. Vanilla Entity Safety in Class Preloader
* **Isolated `LIVING_ENTITY_CLASSES`**:
  * Removed vanilla entity classes (`net.minecraft.world.entity.*`) from the early preload queue, ensuring third-party core mods have complete priority to transform base entity classes without premature initialization.

---

### 🛠️ Compatibility & Requirements
* **Minecraft Version**: `1.20.1`
* **Loader**: `Forge` (47.1.0+)
* **Java Version**: `Java 17`
* **Environment**: `Client` (Pure client-side mod, zero server setup required)

---

## 🇯🇵 日本語（GitHub リリースノート）

# 🚀 TheFastLaunch v-b1.9.1（緊急ホットフィックス：Forgery / Fabrication 互換性対応）

**TheFastLaunch v-b1.9.1** では、**Forgery**（Fabrication系MOD）導入環境において発生していた Mixin 競合クラッシュ（Issue #4）を解決する緊急互換性ホットフィックスを実施しました！

### 🌟 主な新機能・修正点

1. 🛡️ **Forgery / Fabrication の動的自動検知と安全なプリロードバイパス**:
   * Forgery の `MixinEntity` が `Vec3` を引数に取るメソッドへ `@ModifyArg` を適用する前に、バニラの `Entity` / `Player` クラスが早期クラスロードされてしまうことで発生していた `Critical injection failure`（起動クラッシュ）を解消。
   * クラスローダーおよび `mods/` フォルダを動的に走査し、Forgery / Fabrication が導入されている場合のみ、競合の可能性がある早期プリロード処理および該当 Mixin を安全に自動バイパスします。
2. ⚡ **クラスプリローダーの安全化**:
   * `LIVING_ENTITY_CLASSES` からバニラの基本エンティティ（`net.minecraft.world.entity.*`）を安全に除外し、他のコアMODやトランスフォーマーが優先的に安全に処理できるよう改善しました。
