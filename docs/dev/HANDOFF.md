# Bàn giao phiên làm việc — jimatla

Cập nhật lần cuối: 2026-10-26. Người dùng nói tiếng Việt, xưng "tao/mày", thích trả lời ngắn gọn.

## Mục tiêu
Viết launcher Android TV tối giản (jimatla) thay `com.google.android.tvlauncher` (đầy quảng cáo, tốn ~150 MB RAM) trên TV của người dùng, và giảm tối đa việc TV dùng internet khi không ở trong app nào.

## ⚠ SỰ CỐ TV (báo ngày sau 26/10)
TV khởi động → launcher cũ hiện thoáng qua → vào recovery: "Can't load Android system. Your data may be corrupt", đòi factory reset. Không vào được adb (No route to host). Đây là cơ chế Rescue Party của Android: một tiến trình lõi bị crash lặp lại lúc khởi động. Chưa biết tiến trình nào vì không đọc được log.
- Thay đổi trên TV (đều qua adb, không đụng phân vùng system): phiên trước gỡ cho user 0 các gói trong "Đã làm (26/10)" + gỡ backdrop + appops TV360/Spotify; phiên 26/10 cài/gỡ `ji.minitvlauncher`, `set-home-activity`, disable rồi enable lại `tvlauncher`. SAI SÓT: sau các thao tác đó KHÔNG khởi động lại TV để kiểm tra.
- Nghi vấn (chưa xác minh): `tvlauncher` hoặc tiến trình đi kèm crash do thiếu gói đã gỡ, ví dụ `com.realtek.vsmleanbacklauncher.vsmpartnercustomizer` (tuỳ biến của hãng cho launcher) hoặc `com.google.android.tungsten.setupwraith`.
- Factory reset sẽ khôi phục mọi gói hệ thống đã gỡ (chúng vẫn nằm trong /system, chỉ bị gỡ cho user 0); mất app tự cài, đăng nhập, cài đặt.
- BÀI HỌC: sau mỗi lần gỡ/tắt gói hệ thống phải `adb reboot` và kiểm tra TV lên bình thường rồi mới làm tiếp.
- **ĐÃ GIẢI QUYẾT (26/10):** log xác nhận: `com.realtek.keyintercept` (persistent) crash vì không có provider `tvlauncher.widget`, provider này thuộc `com.realtek.vsmleanbacklauncher.vsmpartnercustomizer/.ConfigurationProvider`. Bắt adb trong lúc khởi động (script lặp `adb connect` 0,5 s), `install-existing` cả 13 gói → TV khởi động lại bình thường. Sau đó (người dùng đồng ý) gỡ lại 12 gói, GIỮ `vsmpartnercustomizer`, `adb reboot` có script canh: 3 phút sau khi lên 0 crash, `keyintercept` ổn định, Home = tvlauncher.
- `vsmpartnercustomizer` → đưa vào danh sách KHÔNG ĐỤNG. Launcher mới cũng phải để gói này cài sẵn.
- Rescue Party đã chạy mức RESET_SETTINGS_UNTRUSTED_DEFAULTS (đặt lại cài đặt do app không phải hệ thống ghi) — có thể vài tuỳ chỉnh đã về mặc định. Appops chặn chạy nền TV360/Spotify vẫn còn (đã kiểm tra).
- Cách cứu hộ nếu tái diễn: script trong lịch sử phiên — vòng lặp `adb connect` + `adb get-state`, bắt được thì đọc `logcat -b crash -d` rồi `cmd package install-existing`.

## Trạng thái (26/10, cuối ngày)
Đã code xong 7 bước theo thiết kế, mỗi bước test trên máy tính (Robolectric + ảnh chụp Roborazzi) rồi commit + push lên `main`. **CHƯA thử bất kỳ bước nào trên TV thật** — TV đang tắt, launcher cũ đang là màn Home, `ji.minitvlauncher` đã gỡ khỏi TV.

### Việc tiếp theo: thử trên TV (người dùng bật TV)
1. `adb connect 192.168.<x>.179:5555`, `./gradlew assembleRelease`, cài APK, `adb shell cmd package compile -m speed -f ji.minitvlauncher`.
2. Hỏi người dùng rồi mới `pm disable-user --user 0 com.google.android.tvlauncher` để launcher mới làm Home (xem "Thao tác riêng trên TV").
3. Cấp quyền đọc thông báo: `adb shell cmd notification allow_listener ji.minitvlauncher/ji.minitvlauncher.system.NotificationReader`.
4. Nhờ người dùng thử (TAO KHÔNG bấm phím giả lập): cuộn mượt không; nhấn giữ OK mở menu; thêm/xóa/sắp xếp ưa thích; nút Bluetooth/Mạng/Cài đặt mở đúng màn; Nguồn vào liệt kê HDMI và chuyển được; chuông + bảng thông báo; hình nền (xử lý lần đầu mất bao lâu, chọn ảnh từ USB, chia sẻ từ X-plore); hộp thoại quyền vị trí + dòng thời tiết; tìm kiếm (bàn phím ảo có hiện không) + giọng nói.
5. Đo RAM: `adb shell dumpsys meminfo ji.minitvlauncher` (so với tvlauncher ~150 MB).
6. Danh mục app thật: nhiều app TV không khai báo `appCategory` → có thể dồn vào "Khác"; xem thực tế rồi tính.

### Bước tiếp theo — làm theo thiết kế (thứ tự đã đề xuất, người dùng chưa phản đối)
Trước khi code: đọc kỹ `docs/dev/design/Main.dc.html` + 3 ghi chú trong `docs/dev/design/canvas.json`. Mỗi bước xong cài lên TV, đo RAM (`dumpsys meminfo ji.minitvlauncher`) và độ mượt.
1. ~~Nền móng~~ — XONG 26/10 (chưa commit, chưa thử trên TV): `ui/theme/Theme.kt` (đơn vị `Number.px`/`.tx` = px thiết kế ÷ 2, bảng màu `Palette`, font Manrope biến thể `res/font/manrope.ttf`, license ở `assets/licenses/manrope-OFL.txt`), `data/Settings.kt` (DataStore: danh sách ưa thích theo thứ tự).
2. ~~Màn chính~~ — XONG 26/10 (chưa commit, chưa thử trên TV): `ui/HomeScreen.kt` + `HomeRows.kt`. Đồng hồ to → co lại khi cuộn (2 bản chữ nét 300/600 scale + mờ chéo qua `graphicsLayer`, không layout lại chữ mỗi khung hình); ngày; hàng Ưa thích (rỗng → khung viền đứt + gợi ý) + hàng theo `appCategory`; hàng đang chọn luôn cuộn lên vị trí hàng đầu (tắt bring-into-view dọc bằng `LocalBringIntoViewSpec`, tự `animateScrollTo`); ô 320×180 phóng 1.1 + viền sáng; nhãn dài mờ cuối / chạy chữ khi chọn; bấm Home khi đang ở Home → về đầu. Mép trên/dưới vùng cuộn dùng dải màu nền (không mask offscreen). App không có banner: nền màu danh mục + icon.
   - Test: `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` → ảnh 1920×1080 ở `app/build/outputs/roborazzi/` (Robolectric SDK 35, xem `app/src/test/resources/robolectric.properties`).
   - Cần xem trên TV: độ mượt khi cuộn/chạy chữ, font biến thể trên Android 9, app thật phần lớn không khai báo `appCategory` → dồn vào "Khác".
   - Đã thử và bỏ: khi Ưa thích rỗng giữ cuộn ở đầu → ô đang chọn bị đẩy xuống sát đáy, tệ hơn.
3. ~~Menu nhấn giữ OK~~ — XONG 26/10 (đã commit, chưa thử trên TV): `ui/AppMenu.kt` (Popup focusable neo vào ô, dưới ô hoặc lên trên nếu hết chỗ; Back đóng), `ui/Icons.kt` (icon nét lấy từ SVG thiết kế). Mở bằng nhấn giữ OK (`Card.onLongClick` của tv-material) hoặc phím Menu. Thêm/Xóa ưa thích → focus theo app sang hàng mới + thông báo nhỏ 1,8 s; Sắp xếp → ◀ ▶ đổi chỗ, OK/Back xong, ô khác mờ 40 %; Thông tin ứng dụng → `ACTION_APPLICATION_DETAILS_SETTINGS`.
   - Cần xem trên TV: nhấn giữ OK có mở menu không (test chỉ thử được phím Menu), màn thông tin ứng dụng có mở được trên TV này không.
   - Test Robolectric: popup gắn cửa sổ qua main looper → `settle()` trong test phải `shadowOf(mainLooper).idle()`.
4a. ~~Hàng nút trên~~ — XONG 26/10 (đã commit, chưa thử trên TV): `ui/TopBar.kt` (nút tròn 64, chú thích dưới nút đang chọn, chấm số), `ui/SidePanel.kt` (bảng 640 bên phải, Popup focusable; bảng Nguồn vào, bảng Thông báo), `system/Network.kt` (biểu tượng + chú thích theo mạng mặc định; SSID chỉ có khi được quyền vị trí), `system/Notifications.kt` (`NotificationReader` = NotificationListenerService), `system/TvInputs.kt`. Bluetooth/Mạng/Cài đặt mở `Settings.ACTION_*` (có dự phòng về `ACTION_SETTINGS`). Nút Nguồn vào chỉ hiện khi có `TvInputManager` input; chuông chỉ hiện khi có quyền đọc thông báo hoặc có bảng thông báo hệ thống (`com.android.tv.action.OPEN_NOTIFICATIONS_PANEL`, Android TV 9 có lẽ không có).
   - Cần thử trên TV: cấp quyền `adb shell cmd notification allow_listener ji.minitvlauncher/ji.minitvlauncher.system.NotificationReader`; danh sách nguồn vào của Realtek và chuyển nguồn qua `TvContract.buildChannelUriForPassthroughInput` (Live TV `com.android.tv`); màn Bluetooth/Wi‑Fi của TV Settings có mở đúng không.
   - Bổ sung (26/10): chú thích Bluetooth hiện tên phụ kiện đang kết nối, trừ remote (`system/Bluetooth.kt`: proxy A2DP/HEADSET/HID_HOST, remote = lớp Peripheral minor 0x0C); 2+ thiết bị → "N thiết bị đã kết nối". Android 12+ cần quyền BLUETOOTH_CONNECT — launcher CHƯA xin quyền này (TV này Android 9 không cần), nên trên TV đời mới chỉ hiện "Bluetooth và phụ kiện".
   - Bổ sung (26/10): bảng Nguồn vào hiện trạng thái tín hiệu thật (`TvInputManager.getInputState`: đã kết nối / thiết bị đang chờ / không có tín hiệu) và đánh dấu nguồn **chọn gần nhất qua launcher** (lưu DataStore). Android không có API công khai cho biết TV đang ở nguồn nào; chuyển nguồn bằng nút trên remote thì dấu tích không đổi theo.
4b. ~~Hình nền~~ — XONG 26/10 (đã commit, chưa thử trên TV): `wallpaper/WallpaperPipeline.kt` (đúng spec trong `canvas.json`: khung trái 290/cao 760, màu nền = hue trung bình vector có trọng số S×V → HSL(h, 38 %, 7,5 %), ảnh xám → #0E0F11, mặt nạ elip góc trên phải 0,42→1 smoothstep, lớp tối mép trên 45 %→0 trong 170 px, nhiễu tam giác; `VERSION` đổi → tự xử lý lại). KHÁC spec: chỉ lưu phần khung 1630×760 (PNG) thay vì cả 1920×1080, vì ngoài khung toàn màu nền → tiết kiệm ~3 MB RAM. `wallpaper/Wallpapers.kt` (3 ảnh mẫu trong `assets/wallpapers/`, 1 ô ảnh tùy chỉnh, màu lưu trong DataStore, vẽ màu ngay rồi ảnh hiện dần 200 ms, mờ theo cuộn). Bảng chọn `ui/WallpaperPanel.kt`. Chọn ảnh: `ACTION_GET_CONTENT` nếu TV có, không thì trình chọn riêng `ui/ImagePicker.kt` (MediaStore, cần quyền đọc ảnh — TV sẽ hiện hộp thoại xin quyền) + nhận ảnh qua Chia sẻ (`SetWallpaperActivity`, "Đặt làm hình nền TV", ví dụ từ X-plore). Mặc định: Hoàng hôn.
   - Cần thử trên TV: thời gian xử lý ảnh lần đầu, MediaStore có thấy ảnh trong USB không, chia sẻ từ X-plore, RAM sau khi có hình nền.
5. ~~Thời tiết~~ — XONG 26/10 (đã commit, chưa thử trên TV): `weather/Weather.kt` (Open-Meteo, toạ độ làm tròn 0,1°, °F chỉ cho US…, mã WMO → 8 nhóm), `weather/WeatherSource.kt` (vị trí `getLastKnownLocation` network/passive/fused, tên nơi qua `Geocoder`, lưu kết quả cuối trong DataStore, tải lại tối đa 1 lần/giờ và CHỈ khi màn chính đang hiện). Xin quyền vị trí gần đúng đúng 1 lần (không hỏi lại); bị từ chối → ẩn dòng thời tiết. Dữ liệu cũ hơn 2 giờ → thêm "Cập nhật X trước". Icon thời tiết: CloudSun lấy từ thiết kế, còn lại tự vẽ cùng kiểu.
   - Cần thử trên TV: hộp thoại xin quyền vị trí, Geocoder trên TV này có trả tên "Hà Nội" không.
6. ~~Tìm kiếm + giọng nói~~ — XONG 26/10 (đã commit, chưa thử trên TV): `ui/Search.kt`. Nút "Tìm kiếm" góc trên trái, trượt sang cạnh đồng hồ nhỏ khi cuộn; màn tìm kiếm toàn màn hình (Popup focusable): nút mic + ô nhập + nút đóng + lưới kết quả 5 cột; tìm không dấu (`searchKey` trong `HomeRows.kt`, đ→d). Phím mũi tên rời ô nhập khi con trỏ đã ở đầu/cuối. Mic = `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (katniss), ẩn nếu TV không có. Gõ xong bấm "Tìm" trên bàn phím mà chỉ còn 1 kết quả → mở luôn.
   - Cần thử trên TV: bàn phím ảo có hiện khi ô nhập được focus không (ô nhập nằm trong Popup), giọng nói tiếng Việt qua katniss.
   - Rule giọng nói (thiết kế, ưu tiên thấp) — ĐÃ LÀM: màn Tìm kiếm thấy phím Search/VoiceAssist/Assist rồi activity bị pause trong vòng 3 s → tự đóng, để tắt Assistant là về thẳng màn chính. Pause vì lý do khác (thông báo, hộp thoại, chuyển nguồn, nút mic của chính launcher) KHÔNG đóng. Nếu hệ thống nuốt phím trước khi tới app (thường với Assist/VoiceAssist) thì đơn giản là không có tác dụng. Cần thử trên TV xem remote gửi mã phím nào.
   - Bài học: trong Popup phải lấy `LocalFocusManager` BÊN TRONG nội dung Popup, lấy bên ngoài là focus manager của cửa sổ chính.
7. ~~Ngôn ngữ~~ — XONG 26/10: theo ngôn ngữ hệ thống, chưa có bản dịch → tiếng Anh (cơ chế tài nguyên Android, `values/` = tiếng Anh đầy đủ, `values-vi/`). Không có chọn ngôn ngữ trong app. Lint sạch lỗi (`MissingTranslation`: các chuỗi không cần dịch đánh `translatable="false"`). Có test tiếng Pháp → hiện tiếng Anh, ngày theo tiếng Pháp. Thêm nhãn TalkBack cho ô app và nút tròn. Thêm tiếng mới: tạo `values-xx/strings.xml`.

### Đo tài nguyên (26/10, `dumpsys procstats --hours 24` + `dumpsys meminfo`, TV vừa khởi động lại)
- Chạy thường trực, nặng nhất: gms ~106 MB, tvlauncher ~92 MB (TB 24 h 106 MB), katniss (Assistant) interactor+search ~120 MB, Play Store 3 tiến trình ~97 MB (vending, :background, :instant_app_installer), bàn phím ~35 MB, mediashell (Chromecast) ~27 MB, tvrecommendations ~23 MB.
- App bên thứ ba: iQIYI ~266 MB nhưng chỉ khi đang mở (trạng thái Top) → bình thường. Spotify ~20 MB ở trạng thái "Imp Fg" 71 % thời gian (dịch vụ tiền cảnh, appops chặn nền KHÔNG chặn được loại này; có thể do đang phát nhạc thật). TV360 sau khi chặn: chỉ còn cache 0,4 % → gần như 0. Không có số liệu TV360 trước khi chặn nên không kết luận được nó tệ hơn app khác.
- `ji.minitvlauncher` (bản thử 25/10): TB ~43 MB, đỉnh 72 MB — so với tvlauncher ~106 MB.

### Thao tác riêng trên TV (không thuộc kế hoạch code)
- Bỏ `tvlauncher` và `tvrecommendations`: chỉ làm khi người dùng yêu cầu và xác nhận rõ ràng ngay lúc đó; đề nghị trước đó không tính.
- Cách làm: ưu tiên **disable** (`adb shell pm disable-user --user 0 <pkg>`), KHÔNG gỡ hẳn. App bị disable không chạy, không chiếm RAM, chỉ chiếm bộ nhớ lưu trữ; bật lại bằng `pm enable <pkg>`.

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
- Ngôn ngữ (đổi 26/10): KHÔNG có chọn ngôn ngữ trong app. Lấy theo ngôn ngữ hệ thống; ngôn ngữ chưa có bản dịch → tiếng Anh. Hiện có tiếng Anh (mặc định) + tiếng Việt, tao dịch; thêm tiếng khác sau bằng `values-xx/`.

## Thiết kế
- Gốc: artifact claude.ai "TV Launcher tối giản" https://claude.ai/artifact/AhWv2xBkrUz94pM954GvUr (loại Design canvas; đọc bằng Artifact tool action `read` + `path` `project/Main.dc.html`, `project/canvas.json`).
- Bản sao cục bộ: `docs/dev/design/Main.dc.html` (prototype HTML, toàn bộ logic trong class `Component`), `docs/dev/design/canvas.json` (chứa 3 ghi chú quan trọng: spec xử lý hình nền, rule nút giọng nói khi ở màn Tìm kiếm — ưu tiên thấp, ghi chú các bảng hệ thống/thông báo).
- Ảnh nền mẫu nằm trong asset store của artifact (`/_blob/<id>` trong Main.dc.html) — tải bằng Artifact `read` với `path` = asset id khi cần.
- Changelog thiết kế (ghi chú "changelog" trong `canvas.json`, 26/10) — ĐÃ ÁP DỤNG: (1) icon Cài đặt = bánh răng Lucide `settings`; (2) căn quang học đồng hồ: dịch trái đúng bằng left side bearing đo bằng `Paint.getTextBounds` (từng chuỗi giờ, nét 300 cho cỡ lớn / 600 cho cỡ nhỏ) + bearing dòng ngày × (1 − e), nút Tìm kiếm khi thu gọn = sau mép nét đồng hồ nhỏ + bề rộng + 32 (`ui/Clock.kt`); (3) ô đang chọn 320×180 → 346×194 (~1,08): dọc nở đều 7 px không đẩy nhãn, ngang mép trái đứng yên, nở sang phải đẩy các ô sau; dải ô bỏ lề dưới 18, khoảng cách hàng 72; mũi tên sắp xếp y=62 (`ui/HomeScreen.kt`). Khi thiết kế có changelog mới: đọc lại artifact (Artifact read `project/canvas.json`, `project/Main.dc.html`) và diff với bản trong `docs/dev/design/`.
- Đồng hồ: Compose đặt dòng 128 px thấp hơn CSS `line-height: 1` ~24 px → nâng 0,1875 em để chân chữ ở y=345 như thiết kế.
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
- KHÔNG đụng: `com.realtek.*` (kể cả `com.realtek.vsmleanbacklauncher.vsmpartnercustomizer` — gỡ nó làm TV không khởi động được), `com.android.tv`, `com.android.tv.settings`, `com.vsm.factorymenu`, gms/gsf/vending, webview, systemui, RtkTvSetupWizard/RtkTvSetupCustomizer.
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
