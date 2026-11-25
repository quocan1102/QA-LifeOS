# Quản Trị Bản Thân (Android)

Ứng dụng lập kế hoạch và nhắc việc hằng ngày (Kotlin + Jetpack Compose, minSdk 26).

## Lấy file APK qua GitHub Actions (không cần cài Android Studio)
1. Tạo repo mới trên GitHub, đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào tab **Actions** > workflow **Build APK** (tự chạy sau mỗi lần push, hoặc bấm *Run workflow*).
3. Khi chạy xong, mở lần chạy đó, tải mục **QuanTri-debug-apk** ở cuối trang (file zip chứa `app-debug.apk`).
4. Chép APK vào điện thoại, mở lên và cho phép "cài đặt từ nguồn không xác định".

## Hoặc build bằng Android Studio
Mở thư mục này, đợi Gradle đồng bộ, chọn *Build > Build APK(s)*.

## Lưu ý
- Lần đầu mở app, hãy cho phép quyền **Thông báo** (Android 13+).
- Nhắc nhở sẽ báo vào giờ bắt đầu của các khung giờ trong Thời khóa biểu, lặp lại hằng tuần.
- Nếu điện thoại có chế độ tiết kiệm pin mạnh (Xiaomi, Oppo, Samsung...), hãy tắt tối ưu pin cho app để báo đúng giờ.
