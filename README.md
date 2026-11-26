# QA LifeOS – Hệ điều hành cuộc sống (Android)

Nhà phát triển: Quốc Ân
Phiên bản: 2.0.1

Ứng dụng lập kế hoạch và nhắc việc hằng ngày (Kotlin + Jetpack Compose, minSdk 26).

## Lấy file APK qua GitHub Actions (không cần cài Android Studio)
1. Tạo repo mới trên GitHub, đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào tab **Actions** > workflow **Build APK** (tự chạy sau mỗi lần push, hoặc bấm *Run workflow*).
3. Khi chạy xong, mở lần chạy đó, tải mục **qa-life-os** ở cuối trang (file zip chứa `qa-life-os.apk`).
4. Chép APK vào điện thoại, mở lên và cho phép "cài đặt từ nguồn không xác định".

## Hoặc build bằng Android Studio
Mở thư mục này, đợi Gradle đồng bộ, chọn *Build > Build APK(s)*.

## Lưu ý
- Lần đầu mở app, hãy cho phép quyền **Thông báo** (Android 13+).
- 4 tab: Việc, Thời khóa biểu (lịch cố định hằng tuần), Kế hoạch (kế hoạch từng ngày), Phát sinh (lịch tháng).
- Kế hoạch ngày = Thời khóa biểu + Phát sinh (Phát sinh được ưu tiên). Nhắc nhở báo vào giờ bắt đầu của từng đoạn việc trong Kế hoạch.
- Nếu điện thoại có chế độ tiết kiệm pin mạnh (Xiaomi, Oppo, Samsung...), hãy tắt tối ưu pin cho app để báo đúng giờ.
- Chạy nền: app dùng báo thức chính xác + dịch vụ chạy nền (thông báo thường trực nhỏ). Bấm biểu tượng bánh răng ở góc phải trên để kiểm tra và bật các quyền (thông báo, báo thức chính xác, không tối ưu pin).
- Đến giờ nhắc, việc đặt "Chuông báo" hoặc "Rung" sẽ kêu/rung liên tục như cuộc gọi đến, bật sáng màn hình khóa với nút Tắt / Báo lại 5 phút, cho tới khi bạn tắt (tối đa 10 phút).
- Thứ tự tab: Kế hoạch, Thời khóa biểu, Phát sinh, Danh sách việc. Kế hoạch xem được theo ngày, tuần, tháng; mọi nơi đều ghi cả dương lịch và âm lịch (mùng 1 và ngày cuối tháng âm màu đỏ).
- Nhắc việc đến giờ: hiện trên màn hình khóa, thanh trạng thái và cửa sổ nổi; mỗi lần kêu tối đa 3 phút, nhắc lại sau mỗi 3 phút cho tới khi bấm "Đã làm".
- Thông báo cao cấp (nút Cài đặt): nhắc khi việc đang làm "đã qua / còn lại" a "% / phút", đọc thành tiếng bằng giọng đọc của máy.
