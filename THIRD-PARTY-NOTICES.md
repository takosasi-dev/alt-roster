# 使っているライブラリ

このアプリが同梱する(APK に入る)ライブラリと、テストにだけ使うライブラリ。版は `gradle/libs.versions.toml` に書いてある物。

## APK に入る物

| 名前 | 版 | ライセンス |
|---|---|---|
| Kotlin 標準ライブラリ(org.jetbrains.kotlin:kotlin-stdlib) | 2.0.21 | Apache-2.0 |
| kotlinx.coroutines(kotlinx-coroutines-android) | 1.9.0 | Apache-2.0 |
| AndroidX Core KTX(androidx.core:core-ktx) | 1.13.1 | Apache-2.0 |
| AndroidX Lifecycle(lifecycle-runtime-ktx / lifecycle-runtime-compose / lifecycle-viewmodel-compose) | 2.8.6 | Apache-2.0 |
| AndroidX Activity Compose(androidx.activity:activity-compose) | 1.9.2 | Apache-2.0 |
| Jetpack Compose(ui / ui-graphics / ui-tooling-preview / material-icons-extended。BOM 2024.10.00) | BOM 2024.10.00 | Apache-2.0 |
| Compose Material 3(androidx.compose.material3:material3。Expressive の部品のため BOM より新しい版。これが引き込む Compose の ui・foundation・runtime も BOM より新しくなる) | 1.5.0-alpha12 | Apache-2.0 |
| Navigation Compose(androidx.navigation:navigation-compose) | 2.8.2 | Apache-2.0 |
| Room(room-runtime / room-ktx) | 2.6.1 | Apache-2.0 |
| DataStore Preferences(androidx.datastore:datastore-preferences) | 1.1.1 | Apache-2.0 |
| Jetpack Glance(glance-appwidget / glance-material3) | 1.1.0 | Apache-2.0 |
| Zip4j(net.lingala.zip4j:zip4j) | 2.11.5 | Apache-2.0 |
| desugar_jdk_libs(com.android.tools:desugar_jdk_libs。Android 7.x で java.time などを使うための、OpenJDK 由来の写し) | 2.1.5 | GPL-2.0 with Classpath Exception |

これらが引き込む AndroidX・Kotlin の関連のライブラリも Apache-2.0。

## ビルドとテストにだけ使う物(APK に入らない)

| 名前 | 版 | ライセンス |
|---|---|---|
| Android Gradle Plugin | 8.6.0 | Apache-2.0 |
| Kotlin Gradle Plugin / Compose Compiler Plugin | 2.0.21 | Apache-2.0 |
| KSP(com.google.devtools.ksp) | 2.0.21-1.0.28 | Apache-2.0 |
| Room コンパイラ(room-compiler) | 2.6.1 | Apache-2.0 |
| Compose UI Tooling(debug ビルドだけ) | BOM 2024.10.00 | Apache-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| kotlin-test-junit | 2.0.21 | Apache-2.0 |
| kotlinx-coroutines-test | 1.9.0 | Apache-2.0 |
| Compose UI Test(androidx.compose.ui:ui-test-junit4。画面の画像を撮るテスト) | BOM 2024.10.00 | Apache-2.0 |
| Robolectric(org.robolectric:robolectric) | 4.14.1 | MIT |
| Roborazzi(io.github.takahirom.roborazzi:roborazzi / roborazzi-compose) | 1.32.2 | Apache-2.0 |

Apache License 2.0 の全文: <https://www.apache.org/licenses/LICENSE-2.0>

## 画像・音・フォント

実在のゲーム・企業・商品の画像、アイコン、ロゴ、音、フォントは入れていない。
アプリのアイコンと通知のアイコンは、このリポジトリのために描いたベクター(XML)。
画面の中のアイコンは Material Icons(Apache-2.0)。
