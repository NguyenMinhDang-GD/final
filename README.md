# Cô Ba D2C F&B Platform (Android Kotlin Jetpack Compose + Node.js Express Server)

Đồ án cuối kỳ môn **Phát triển Ứng dụng Di động**: Nền tảng Đặt món & Điều phối Giao hàng Tự động xây dựng theo mô hình **Client–Server**:
- **Mobile App (`app/`):** Android Native (Kotlin + Jetpack Compose Material 3 + MVVM + OkHttp + WebSocket + OpenStreetMap `osmdroid`).
- **Backend Server (`backend/`):** Node.js + Express.js + RESTful API + WebSocket (`ws`) + JWT Auth (`bcryptjs` + `jsonwebtoken`) + Cơ sở dữ liệu quan hệ SQLite (`node:sqlite` 11 bảng + giao dịch ACID).

---

## 1. Khởi chạy Backend Server (Node.js)
Mở Terminal tại thư mục `e:\baitap\android\final\backend`:

```powershell
cd e:\baitap\android\final\backend
npm.cmd install
npm.cmd start
```

Server sẽ lắng nghe tại **`http://0.0.0.0:3000`** và tự động tạo file cơ sở dữ liệu `backend/data/coba_fnb.sqlite` kèm sẵn dữ liệu mẫu quán **"Bún Riêu & Bún Chả Cá Cô Ba"**:
- Tài khoản Khách hàng mẫu: `khach@coba.vn` / `123456` (có sẵn Ví `500.000đ`).
- Tài khoản Chủ quán / Bếp mẫu: `quan@coba.vn` / `123456`.

---

## 2. Khởi chạy Ứng dụng Android
1. Mở thư mục gốc `e:\baitap\android\final` bằng **Android Studio** và bấm **Run** lên máy ảo `Pixel_8`.
2. Mặc định trong `local.properties`:
   - `NODE_API_BASE_URL=http://10.0.2.2:3000` (`10.0.2.2` là địa chỉ chuẩn để máy ảo Android gọi vào `localhost:3000` của máy tính).
   - Nếu cài lên điện thoại thật dùng chung Wi-Fi, đổi `10.0.2.2` thành IP LAN của laptop (ví dụ `http://192.168.1.15:3000`).

---

## 3. Danh mục REST API & WebSocket của Node.js Server

| Phương thức | Endpoint | Mô tả nghiệp vụ |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Kiểm tra trạng thái hoạt động của server |
| `GET` | `/api/bootstrap` | Tải trọn gói thông tin quán, lịch mở cửa, thực đơn & voucher |
| `POST` | `/api/auth/register` | Đăng ký tài khoản (`bcrypt` hash + cấp JWT + Ví 500k) |
| `POST` | `/api/auth/login` | Đăng nhập bằng Email & Mật khẩu, trả về JWT |
| `POST` | `/api/auth/demo-login` | Chuyển nhanh vai trò Demo (`CUSTOMER` / `MERCHANT`) |
| `PATCH` | `/api/store/status` | Bật/tắt mở cửa hoặc hẹn giờ Quán bận (`15p`, `30p`, `1h`, `1 ngày`) |
| `PUT / POST` | `/api/store/schedules/*` | Cấu hình lịch thường ngày (Thứ 2–CN) & lịch nghỉ lễ |
| `POST / PATCH / DELETE` | `/api/catalog/*` | Quản lý Danh mục, Món ăn, Option Groups, Options & Vouchers |
| `POST` | `/api/orders` | Giao dịch ACID đặt đơn: khóa lượt Voucher, bảo vệ phí ship, trừ tiền Ví & bắn WebSocket `ORDER_CREATED` |
| `POST` | `/api/orders/:id/confirm` | Bếp xác nhận nhận đơn (`PREPARING`) |
| `POST` | `/api/orders/:id/reject` | Giao dịch ACID từ chối đơn & hoàn tiền Ví tự động 100% (`REFUNDED`) |
| `POST` | `/api/orders/:id/dispatch-shipper` | Gọi Lalamove v3 Sandbox API (`HMAC-SHA256`) / Mock Shipper |
| `PATCH` | `/api/orders/:id/milestone` | Cập nhật tiến độ tài xế (`DRIVER_AT_STORE` .. `COMPLETED`) |
| `POST` | `/api/orders/:id/review` | Khách hàng chấm điểm `1–5 sao` & viết nhận xét |
| `WS` | `ws://<host>:3000/ws` | Kênh WebSocket đẩy sự kiện Realtime cho Bếp & Khách hàng |

---

## 4. Lệnh chạy Kiểm thử Tự động (Backend + Android)

```powershell
# 1. Kiểm thử tích hợp Node.js API, WebSocket & Giao dịch hoàn tiền
cd e:\baitap\android\final\backend
node --test tests/api.test.js

# 2. Kiểm thử Unit Test Android & Đóng gói APK
cd e:\baitap\android\final
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```
