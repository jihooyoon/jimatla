# Bàn giao phiên làm việc — jimatla

Cập nhật lần cuối: 2026-10-26. Người dùng nói tiếng Việt, xưng "tao/mày", thích trả lời ngắn gọn.

## Mục tiêu
Viết launcher Android TV tối giản (jimatla) thay `com.google.android.tvlauncher` (đầy quảng cáo, tốn ~150 MB RAM) trên TV của người dùng, và giảm tối đa việc TV dùng internet khi không ở trong app nào.

## Trạng thái (26/10)
Bản tối thiểu đã chạy trên TV làm màn Home, cuộn mượt (bản release). Đã commit + push lên `main`.
**Đã dọn TV (26/10, theo yêu cầu):** bật lại `tvlauncher`, đặt lại làm Home, gỡ hẳn `ji.minitvlauncher`. TV đang ở trạng thái như trước khi thử launcher. Lần sau cài lại: cài APK → `pm disable-user --user 0 com.google.android.tvlauncher` (cần hỏi người dùng) → bấm Home thử.
Cách làm tiếp: code + build trên máy tính, KHÔNG dùng máy ảo (người dùng không muốn). Xem giao diện bằng ảnh chụp Roborazzi (JVM) nếu cần; phần cần TV thật (độ mượt, RAM, nguồn vào, thông báo, vị trí, giọng nói, banner thật) gom lại để thử khi người dùng bật TV.

### Bước tiếp theo — làm theo thiết kế (thứ tự đã đề xuất, người dùng chưa phản đối)
Trước khi code: đọc kỹ `docs/dev/design/Main.dc.html` + 3 ghi chú trong `docs/dev/design/canvas.json`. Mỗi bước xong cài lên TV, đo RAM (`dumpsys meminfo ji.minitvlauncher`) và độ mượt.
1. Nền móng: lưu cài đặt (ưa thích, thứ tự, ngôn ngữ — DataStore), font Manrope, màu/kiểu chữ theo thiết kế.
2. Màn chính: đồng hồ to + ngày (co lại khi cuộn), hàng Ưa thích, hàng theo `appCategory`, kiểu dự phòng cho app không có banner (đề xuất icon trên nền màu danh mục).
3. Menu nhấn giữ OK: Mở / Thêm-Xóa ưa thích / Sắp xếp / Thông tin ứng dụng.
4. Hàng nút trên: Bluetooth, Mạng, Cài đặt (mở màn hệ thống); Nguồn vào (thử thực tế); Thông báo (cấp quyền qua adb); Hình nền (tự viết trình chọn ảnh — nặng nhất).
5. Thời tiết: vị trí hệ thống + Open-Meteo.
6. Tìm kiếm app + nút giọng nói.
7. Chọn ngôn ngữ trong app + bản dịch tiếng Anh đầy đủ.
8. Dùng ổn vài ngày → gỡ `tvlauncher` và `tvrecommendations`.

### Dựng môi trường trên máy mới
- JDK 25 (`java-25-openjdk-devel` trên Fedora), Android cmdline-tools vào `~/Android/Sdk/cmdline-tools/latest`, `ANDROID_HOME=~/Android/Sdk`, thêm `cmdline-tools/latest/bin` vào PATH.
- `yes | sdkmanager --licenses` rồi `sdkmanager "platforms;android-37.0" "build-tools;36.0.0"`. Gradle tự tải qua `./gradlew`.
- Tạo `local.properties` nếu cần (`sdk.dir=...`) — file này không commit.
- adb: dùng bản của hệ thống (dnf `android-tools`). AGP tự cài `platform-tools` vào SDK — đừng thêm nó vào PATH (hai bản adb khác phiên bản sẽ tắt server của nhau).
- Build + cài: `./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk && adb shell cmd package compile -m speed -f ji.minitvlauncher`. Bản release hiện ký bằng khóa debug của máy. Nếu TV đang có bản ký bởi máy khác thì không cài đè được → gỡ trước hoặc chép `~/.android/debug.keystore` từ máy cũ sang.

### Đã làm (lịch sử)
- Môi trường: JDK 25.0.4; SDK `platforms;android-37.0` + `build-tools;36.0.0`, chọn theo AGP **9.4.1** (tối đa API 37; build-tools mặc định 36.0.0; cần Gradle ≥ 9.6.0).
- Project: Gradle wrapper 9.8.2, AGP 9.4.1 (Kotlin có sẵn trong AGP + plugin `kotlin.plugin.compose` 2.4.21), Compose BOM 2026.09.00, tv-material 1.1.0, minSdk 28 / target+compile 37. Code: `app/src/main/java/ji/minitvlauncher/` (`MainActivity.kt` lưới 5 cột + mở app + chặn Back; `AppRepository.kt` lấy app LEANBACK trước rồi LAUNCHER, banner hoặc icon, LruCache 16 MB cho ảnh). Chuỗi: `values/` (tiếng Anh, mặc định) + `values-vi/`.
- Đặt làm Home: `set-home-activity` KHÔNG đủ vì HOME filter của tvlauncher có priority=2 (app bên thứ ba tối đa 0) → Android bỏ qua preferred. Đã `pm disable-user --user 0 com.google.android.tvlauncher` (người dùng đồng ý) → HOME về `ji.minitvlauncher/.MainActivity`; dự phòng `com.android.tv.settings/.system.FallbackHome` (priority -1000). Trả lại: `adb shell pm enable com.google.android.tvlauncher`.
- Bản debug cuộn giật (Compose debug không tối ưu) → luôn cài bản release + compile speed. Nếu sau này giật: baseline profile, xem animation scale của tv-material Card, đo `dumpsys gfxinfo ji.minitvlauncher`.

## Quyết định (26/10)
- UI: **Kotlin + Jetpack Compose** — đã chốt.
- Mục tiêu dài hạn: đưa lên Play Store thành launcher dùng chung cho mọi TV, không chỉ cho TV này.
- Package name: **`ji.minitvlauncher`** — đã chốt (hợp lệ cho Play Store, không đổi được sau khi đăng). Tên hiển thị: "Mini TV Launcher".
- Thời tiết: Open-Meteo (người dùng không định thu tiền → gói miễn phí OK); màn nhập vị trí tay làm sau, lấy **vị trí hiện tại từ hệ thống**. Đã kiểm tra: TV có network location (GMS `network`/`fused`, `location_providers_allowed=gps,network`, đã có last known location) → dùng `LocationManager` + `ACCESS_COARSE_LOCATION`, không cần thư viện Play Services. Vẫn cần màn nhập vị trí tay để dự phòng cho TV khác (người dùng sẽ thêm vào artifact thiết kế bên Cowork). Lưu ý: gói miễn phí của Open-Meteo chỉ cho dùng phi thương mại.
- Font: **Manrope** (biến thể một file `Manrope[wght].ttf` từ google/fonts, ~165 KB, độ đậm 200–800, có tiếng Việt) — đã chốt sau khi cân nhắc dùng Roboto mặc định.
- Ngôn ngữ: chọn ngôn ngữ ngay trong app; làm tiếng Việt + tiếng Anh trước (tao dịch), thêm tiếng khác sau. Android 9 → dùng `AppCompatDelegate.setApplicationLocales`.

## Thiết kế
- Gốc: artifact claude.ai "TV Launcher tối giản" https://claude.ai/artifact/AhWv2xBkrUz94pM954GvUr (loại Design canvas; đọc bằng Artifact tool action `read` + `path` `project/Main.dc.html`, `project/canvas.json`).
- Bản sao cục bộ: `docs/dev/design/Main.dc.html` (prototype HTML, toàn bộ logic trong class `Component`), `docs/dev/design/canvas.json` (chứa 3 ghi chú quan trọng: spec xử lý hình nền, rule nút giọng nói khi ở màn Tìm kiếm — ưu tiên thấp, ghi chú các bảng hệ thống/thông báo).
- Ảnh nền mẫu nằm trong asset store của artifact (`/_blob/<id>` trong Main.dc.html) — tải bằng Artifact `read` với `path` = asset id khi cần.
- Tóm tắt: màn 1920×1080; đồng hồ to + ngày + thời tiết, co lại khi cuộn; hàng Ưa thích + các hàng theo `android:appCategory`; nhấn giữ OK → menu (Mở / Thêm-Xóa ưa thích / Sắp xếp / Thông tin ứng dụng); màn Tìm kiếm app + nút giọng nói (`RecognizerIntent`); nút trên cùng: Nguồn vào, Bluetooth, Mạng, Thông báo (badge), Hình nền, Cài đặt. Font Manrope (OFL).

## Những điều đã kiểm tra trên TV ảnh hưởng tới app
- **Không có DocumentsUI** → "Chọn ảnh từ bộ nhớ" phải tự viết trình chọn ảnh (USB/bộ nhớ trong) hoặc nhận ảnh qua share intent (từ X-plore).
- Thông báo: cần cấp quyền bằng `adb shell cmd notification allow_listener <pkg>/<service>`.
- Giọng nói: recognizer là katniss (`KatnissRecognitionService`) → giữ katniss.
- Nguồn vào: `TvInputManager` + chuyển nguồn qua `com.android.tv` — cần thử thực tế.
- App không có banner 16:9 → cần kiểu dự phòng (đề xuất: icon trên nền màu danh mục), thiết kế chưa vẽ.
- Màn hình: 1920×1080, density 320 (→ 960×540 dp).

## TV
- Vsmart VinaTV 4K TR2001, chip Realtek, Android TV 9, RAM 2 GB. adb: `adb connect 192.168.<x>.179:5555` (IP mạng nội bộ chỉ ghi số cuối; hỏi người dùng hoặc xem router cho phần còn lại).
- Vsmart đã phá sản: không OTA, không firmware để cứu → chỉ dùng thao tác đảo ngược được (`pm uninstall -k --user 0`, `disable-user`, appops). Cài lại: `adb shell cmd package install-existing <pkg>`.
- KHÔNG đụng: `com.realtek.*`, `com.android.tv`, `com.android.tv.settings`, `com.vsm.factorymenu`, gms/gsf/vending, webview, systemui, RtkTvSetupWizard/RtkTvSetupCustomizer.
- `com.google.android.leanbacklauncher` trên TV này là **stub** (không có HOME activity) → không dùng làm launcher dự phòng được.

### Đã làm (26/10)
- Screensaver → `com.vinds.app/.DayDream` (của Vsmart, offline). Đã gỡ `com.google.android.backdrop`.
- Đã gỡ (user 0): `com.vsm.notify`, `com.vinsmart.khbhsmarttv`, `com.vsmart.apisnhelper`, `com.vinsmart.retailhelper`, `com.vinsmart.retaildemoapp_v3`, `android.autoinstalls.config.vsmart`, `com.realtek.vsmleanbacklauncher.vsmpartnercustomizer`, `com.google.android.syncadapters.calendar`, `com.google.android.syncadapters.contacts`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`, `com.google.android.tungsten.setupwraith`.
- TV360 (`com.viettel.tv360.tv`) và Spotify (`com.spotify.tv.android`): appops `RUN_IN_BACKGROUND` + `RUN_ANY_IN_BACKGROUND` = ignore.
- Người dùng muốn giữ: `com.vinsmart.mediabrowser`, `com.vinds.app`, talkback, katniss (cần giọng nói).
- Sao lưu trước khi gỡ: `docs/dev/tv-backup/`.

### Còn treo
- "Usage & diagnostics": người dùng đã tắt (26/10).
- Tự cập nhật Play Store: không tắt được bằng lệnh (TV build `user`, không root; cài đặt nằm trong dữ liệu riêng của Play Store) → người dùng tự tắt trên remote.
- Assistant (katniss) tốn ~124 MB: có thể thử gỡ bản cập nhật (về bản 3.13 trong /system: `adb shell pm uninstall com.google.android.katniss`, không `--user 0`) — chưa làm, chờ người dùng.
- Gỡ `tvlauncher`, `tvrecommendations` sau khi launcher mới chạy ổn.
- Chưa quyết: Chromecast (`mediashell`, ~39 MB), `tv.remote.service` (~14 MB).

## Lưu ý
- **TUYỆT ĐỐI không điều khiển giao diện TV bằng `input keyevent`/`tap`.** Ngày 26/10 bấm DPAD mò đã lạc vào màn xác nhận Factory Reset. Người dùng yêu cầu: cần thao tác trên giao diện thì hướng dẫn để họ tự bấm.
- Toolchain: JDK `java-25-openjdk-devel` (LTS; không dùng 27 vì Gradle/Kotlin chưa chắc hỗ trợ). Android cmdline-tools ở `~/Android/Sdk/cmdline-tools/latest`. Biến môi trường ở cuối `~/.zshrc` và `~/.bashrc.d/android.sh`. Dùng `adb` của dnf (`/usr/bin/adb`); không cài SDK `platform-tools` vì hai bản adb khác phiên bản sẽ tắt server của nhau.
- GitHub: repo công khai `jihooyoon/jimatla` — đừng ghi IP đầy đủ, email, tên tài khoản vào file trong repo. Chỉ commit/push khi người dùng bảo. Danh tính git: giống commit đầu (đặt trong `.git/config` của repo, `git config user.name/user.email`).
- Ghi chú phát triển nằm trong `docs/dev/` (commit vào repo để làm tiếp trên máy khác); `CLAUDE.md` ở gốc repo bảo Claude đọc file này đầu phiên.
