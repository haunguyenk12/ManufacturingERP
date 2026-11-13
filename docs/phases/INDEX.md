# Chỉ mục lịch sử phase (`CLAUDE.md §0.x`)

> Chuyển từ `CLAUDE.md` ngày 2026-11-06 để không nạp ~500 KB lịch sử vào mỗi phiên. Nội dung **nguyên văn**.
> Tài liệu khác ghi "xem `§0.45`" ⇒ mở `docs/phases/0.45.md`. Tra cứu: `Grep` trong thư mục này, rồi `Read` có `offset`/`limit`.
> Phase mới: tạo `docs/phases/<mã>.md` (vd `G12.md`) + thêm một dòng vào bảng dưới. **Không** viết narrative vào `CLAUDE.md`.

| § | Tiêu đề | KB |
|---|---|---:|
| [0.1](0.1.md) | 0.1 Phase Hiện Tại | 96.7 |
| [0.2](0.2.md) | 0.2 Module Đã Hoàn Thành | 6.9 |
| [0.3](0.3.md) | 0.3 P1 – Approval Workflow & Business Gates (ĐÃ HOÀN THÀNH) | 2.0 |
| [0.4](0.4.md) | 0.4 Nợ Kỹ Thuật Đã Biết (không phải bug, đừng "tiện tay sửa") | 15.3 |
| [0.6](0.6.md) | 0.6 F2 – Production Receipt Lifecycle & QC Disposition (ĐÃ HOÀN THÀNH 2026-07-26) | 1.5 |
| [0.7](0.7.md) | 0.7 F3 – Sales Order + Planning Demand (ĐÃ HOÀN THÀNH 2026-07-26) | 2.0 |
| [0.8](0.8.md) | 0.8 F4 – Routing Tối Thiểu + Snapshot Lên Work Order (ĐÃ HOÀN THÀNH 2026-07-27) | 1.7 |
| [0.9](0.9.md) | 0.9 F5-A – Production Execution + Work Order Reshape (ĐÃ HOÀN THÀNH 2026-07-27) | 2.4 |
| [0.10](0.10.md) | 0.10 F5-B – Reshape Planning (ĐÃ HOÀN THÀNH 2026-07-28) | 1.9 |
| [0.11](0.11.md) | 0.11 F6 – Fulfillment Allocation (ĐÃ HOÀN THÀNH 2026-07-28) | 2.4 |
| [0.12](0.12.md) | 0.12 D4 – MRP Đúng Số: Open PO Vào Netting + Run Header (ĐÃ HOÀN THÀNH 2026-07-30) | 2.0 |
| [0.13](0.13.md) | 0.13 D5 – QC Disposition Cho Output Không Lot-Tracked (ĐÃ HOÀN THÀNH 2026-07-30) | 3.1 |
| [0.14](0.14.md) | 0.14 D6 – Scope Lại `Idempotency-Key` Của `stock_movements` (ĐÃ HOÀN THÀNH 2026-07-30) | 2.9 |
| [0.15](0.15.md) | 0.15 D7 – Test Tầng HTTP Nhóm A + Rà 409/422 (ĐÃ HOÀN THÀNH 2026-07-30) | 6.0 |
| [0.16](0.16.md) | 0.16 D7b – Test Tầng HTTP Nhóm B+C (ĐÃ HOÀN THÀNH 2026-07-31) | 4.2 |
| [0.17](0.17.md) | 0.17 D11 – Đóng Hai Nợ Đúng-Sai Cuối Cùng (#25, #26) (ĐÃ HOÀN THÀNH 2026-07-31) | 5.1 |
| [0.18](0.18.md) | 0.18 F7 – Đối Chiếu Spec FE Toàn Diện + Đóng 5 Gap (ĐÃ HOÀN THÀNH 2026-07-31) | 4.4 |
| [0.19](0.19.md) | 0.19 F8 – Đóng Gap Field-Level + 4 Endpoint Spec FE (ĐÃ HOÀN THÀNH 2026-07-31) | 6.6 |
| [0.20](0.20.md) | 0.20 F9 – Hai Field Cuối + Sửa Tuyên Bố Nói Quá Của `F8` (ĐÃ HOÀN THÀNH 2026-07-31) | 3.6 |
| [0.21](0.21.md) | 0.21 F10 – Ba Nợ Field-Level Cuối Của Spec FE (`G`, `F`, `H`) (ĐÃ HOÀN THÀNH 2026-08-01) | 4.8 |
| [0.22](0.22.md) | 0.22 D8a – Refresh Token Reuse Detection (RTR) (ĐÃ HOÀN THÀNH 2026-08-03) | 4.6 |
| [0.23](0.23.md) | 0.23 D8b – Absolute Session Timeout (ĐÃ HOÀN THÀNH 2026-08-03) | 6.0 |
| [0.24](0.24.md) | 0.24 Bugfix Từ Log Production: `lower(bytea)` + 500 Do Thiếu Query Param (2026-08-04) | 4.4 |
| [0.25](0.25.md) | 0.25 C2-5 – CORS + Dữ Liệu Test Multi-Plant + Sửa Lệch RBAC Seed↔Tài Liệu (2026-08-04) | 7.7 |
| [0.26](0.26.md) | 0.26 C2-3 – UOM Master (ĐÃ HOÀN THÀNH 2026-08-04) | 5.0 |
| [0.27](0.27.md) | 0.27 C2-4 – SO PATCH · Role/Scope Lifecycle · Over-BOM Contract · Time Variance (ĐÃ HOÀN THÀNH 2026-08-05) | 6.2 |
| [0.28](0.28.md) | 0.28 C2-6 – Work Center Entity + CRUD + Routing FK (ĐÃ HOÀN THÀNH 2026-08-05) | 5.0 |
| [0.29](0.29.md) | 0.29 C2-7 – Shift + Work Calendar Entity + CRUD + Work Center FK (ĐÃ HOÀN THÀNH 2026-08-05) | 6.1 |
| [0.30](0.30.md) | 0.30 C2-8 – CRP Tĩnh + Capacity Board + Schedule Adjustment (ĐÃ HOÀN THÀNH 2026-08-05) | 10.6 |
| [0.31](0.31.md) | 0.31 P3 – Costing Engine (ĐÃ HOÀN THÀNH 2026-08-05) | 7.6 |
| [0.32](0.32.md) | 0.32 Concurrent Refresh-Token Race — Mở Rộng `D8` (ĐÃ HOÀN THÀNH 2026-08-05) | 6.3 |
| [0.33](0.33.md) | 0.33 P5 – Serial Number Tracking (ĐÃ HOÀN THÀNH 2026-08-06) | 4.0 |
| [0.34](0.34.md) | 0.34 P6 – WO Close/Reconcile (ĐÃ HOÀN THÀNH 2026-08-06) | 5.6 |
| [0.35](0.35.md) | 0.35 D8c – Forgot-password / Account Recovery (ĐÃ HOÀN THÀNH 2026-08-06) | 6.2 |
| [0.36](0.36.md) | 0.36 C2-1 – Audit Logs Read API (ĐÃ HOÀN THÀNH 2026-08-06) | 5.8 |
| [0.37](0.37.md) | 0.37 C2-2 – Inventory Lot Lifecycle API (ĐÃ HOÀN THÀNH 2026-08-06) | 6.1 |
| [0.38](0.38.md) | 0.38 Bugfix P0 Auth: `/auth/refresh` Phụ Thuộc Ngầm Vào Access Token + `TOKEN_MALFORMED` Sai Nghĩa (2026-08-06) | 7.5 |
| [0.39](0.39.md) | 0.39 FE Contract Fix: Sales Order `version` + Activate API Cho Plant/Warehouse/Item (2026-08-08) | 5.5 |
| [0.40](0.40.md) | 0.40 Bugfix P0: Sales Order Full-Replacement `PATCH` Chết Với `lines[]` + `version` Không Bump Khi Thay Dòng (2026-08-10) | 6.3 |
| [0.41](0.41.md) | 0.41 Fix Contract Admin RBAC: Đọc Membership Role/Scope + Unmapped Path Trả 500 (2026-08-12) | 6.7 |
| [0.42](0.42.md) | 0.42 Trả Nợ #27: Wire Format Ngày/Giờ Về ISO (2026-08-12) | 8.7 |
| [0.43](0.43.md) | 0.43 FE Handoff: Inventory Dashboard API — Nhãn Hiển Thị + Hợp Đồng Đếm/Thứ Tự (2026-08-14) | 9.1 |
| [0.44](0.44.md) | 0.44 Trả Lời `live-data-audit.md`: Available Theo Lot Status + Idempotency Cho Planning Run (2026-08-14) | 8.5 |
| [0.45](0.45.md) | 0.45 Bộ Dữ Liệu Demo `VIETBIKE` + Sửa Lỗi P0 `MultipleBagFetchException` (2026-08-14) | 10.7 |
| [0.46](0.46.md) | 0.46 Bộ Dữ Liệu Demo SẠCH Cho Capstone 2 — Namespace `D26-` (2026-08-22) | 14.5 |
| [0.47](0.47.md) | 0.47 Track `EH-*` – Refactor Hạ Tầng Xử Lý Lỗi (2026-08-23) | 8.3 |
| [0.48](0.48.md) | 0.48 Contract Giá Trị Của Audit Log — `changes[].oldValue`/`newValue` Về `string` (2026-08-25) | 6.7 |
| [0.49](0.49.md) | 0.49 Track `AR-*` – Refactor Hạ Tầng Audit (2026-09-02) | 7.5 |
| [0.50](0.50.md) | 0.50 `G2` – Danh Mục Nền Cho FE Giai Đoạn 2 (2026-10-14) | 7.8 |
| [0.51](0.51.md) | 0.51 `G3` – Nhập Excel Dữ Liệu Nền, Mẫu Cố Định, Atomic (2026-10-14) | 4.9 |
| [0.52](0.52.md) | 0.52 `G4` – Tồn Kho, Lô Và Chuyển Kho (2026-10-15) | 7.3 |
| [0.53](0.53.md) | 0.53 `G5` – BOM, Quy Trình, Ca/Lịch/Trung Tâm, Công Suất (2026-10-16) | 6.7 |
| [0.54](0.54.md) | 0.54 `G6a` – Đơn Bán Hàng Nối Khách + Giữ Tồn Cho Đơn (2026-10-17) | 4.5 |
| [0.55](0.55.md) | 0.55 `G6b` – Run MRP Thương Mại + Chuyển Đổi Một Phần (2026-10-18) | 4.5 |
| [0.56](0.56.md) | 0.56 `G6c` – Nhập Đơn Bán Hàng Excel (2026-10-18) | 3.4 |
| [0.57](0.57.md) | 0.57 `G7a` – Vòng Đời Đơn Mua Hàng (2026-10-18) | 5.9 |
| [0.58](0.58.md) | 0.58 `G7b` – Phiếu Nhận Nháp, Nhận Mua Vào Vùng Chờ Kiểm (2026-10-18) | 6.0 |
| [0.59](0.59.md) | 0.59 `G7c` – QC Mua Hàng Theo Số Lượng, Lô Con (2026-10-19) | 5.6 |
| [0.60](0.60.md) | 0.60 `G8a` – Nháp Lệnh SX: Kho Từng Dòng, Version Bắt Buộc, Lịch Đã Chốt, Trả Quota Khi Huỷ (2026-10-19) | 6.2 |
| [0.61](0.61.md) | 0.61 `G8b` – Cấp Vật Tư: Phiếu Bổ Sung, Kiểm Lại Lúc Duyệt, Huỷ Của Người Tạo (2026-10-19) | 6.0 |
| [0.62](0.62.md) | 0.62 `G8c` – Công Đoạn: Đầu Vào/Chuyển Giao, Xử Lý Lại, Vật Tư Thực Dùng, Hoàn Thành Có Hụt (2026-10-20) | 5.9 |
| [0.63](0.63.md) | 0.63 `G8d` – Dở Dang (WIP) + Hoàn Vật Tư Về Đúng Nguồn (2026-10-20) | 3.9 |
| [0.64](0.64.md) | 0.64 `G9a` – Phiếu Nhập Thành Phẩm: Vòng Đời, Version, Hạn Mức Nhập, Đọc (2026-10-21) | 6.4 |
| [0.65](0.65.md) | 0.65 `G9b` – Phiếu Nhập: Phân Bổ Theo Lượt Báo Sản Lượng + Truy Xuất Nguồn (2026-10-23) | 4.8 |
| [0.66](0.66.md) | 0.66 `G9c` – Phiếu Nhập: QC Theo Lượng Nhiều Lượt, Lô Con (2026-10-23) | 5.1 |
| [0.67](0.67.md) | 0.67 `G10a` – Nền Thương Mại Của Đơn Bán: Giá, Khách Bắt Buộc, Baseline, Quyền (2026-10-25) | 3.5 |
| [0.68](0.68.md) | 0.68 `G10b` – Phiếu Giao Hàng (2026-10-25) | 4.2 |
| [0.69](0.69.md) | 0.69 `G10c` – Hoá Đơn Bán Hàng (2026-10-26) | 3.1 |
| [0.70](0.70.md) | 0.70 `G10d` – Thanh Toán Khách Hàng, Tiến Độ Đơn, Liên Kết Ngược (2026-10-27) | 4.4 |
| [0.71](0.71.md) | 0.71 `G11` – Giá Thành Sản Xuất, Chênh Lệch Và Đóng Lệnh (2026-10-27) | 4.9 |
| [0.72](0.72.md) | 0.72 Track `CF-*` – Capstone 3 Follow-up (từ 2026-10-27) | 17.8 |
| [AP-1](AP-1.md) | AP-1 Luồng tài khoản và mật khẩu: đăng nhập lần đầu bằng mã email, đổi mật khẩu (2026-11-06) | 4.6 |
| [NEXT_PHASE_PLAN-G2-G11](NEXT_PHASE_PLAN-G2-G11.md) | Roadmap `G2`–`G11` nguyên văn (file `NEXT_PHASE_PLAN.md` cũ ở gốc repo, chuyển 2026-11-12). Tham chiếu "`NEXT_PHASE_PLAN.md §9`…`§18`" trỏ vào đây | 284 |
| [RA-1](RA-1.md) | RA-1 Rà soát toàn dự án và dọn dẹp: sửa self-access user, quyền `PERM_ACCESS_MANAGE`, rút gọn rules, sắp xếp tài liệu (2026-11-13) | 5.6 |

`§0.5` (ba cột số lượng của `work_orders`) vẫn nằm trong `CLAUDE.md`.
