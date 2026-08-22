#!/usr/bin/env bash
# 999-manifest — write demo-ids.json and print the closing summary.

stage_banner "15. Kết xuất demo-ids.json"

note "meta.finishedAt" "$(ts_now)"

dump_manifest "$MANIFEST"
log "Đã ghi $MANIFEST"

cat >&2 <<EOF

================================================================================
BỘ DỮ LIỆU DEMO ĐÃ SẴN SÀNG — $CO_NAME ($CO_CODE)
================================================================================

Đăng nhập
  admin / $ADMIN_PASSWORD          toàn quyền, dùng cho master data toàn cục
  quanly.hanoi / $DEMO_PASSWORD    MANAGER, chỉ $P1_CODE
  congnhan.hanoi / $DEMO_PASSWORD  OPERATOR, chỉ $P1_CODE
  kehoach.vietbike / $DEMO_PASSWORD vai trò tuỳ chỉnh $ROLE_PLANNER, toàn công ty
  quanly.saigon / $DEMO_PASSWORD   MANAGER, chỉ $P2_CODE  <- dùng để chứng minh cô lập
  kiemtoan.tamthoi / $DEMO_PASSWORD phân công hết hạn sau 7 ngày

  Lưu ý: tài khoản gán phạm vi PLANT/COMPANY vẫn bị 403 ở GET /uoms và GET /companies.
  Đó là đặc điểm sẵn có của guard (chỉ đọc assignment phạm vi GLOBAL), không phải lỗi seed.
  Các màn hình master data toàn cục hãy đăng nhập admin.

Lệnh sản xuất — đủ 8 trạng thái tại $P1_CODE
  WO-2601 DRAFT      WO-2602 PLANNED     WO-2603 BLOCKED    WO-2604 RELEASED
  WO-2605 IN_PROGRESS WO-2606 COMPLETED  WO-2607 CLOSED     WO-2608 CANCELLED
  WO-2609 RELEASED (đụng lịch với WO-2604 trên tổ TO-HAN => bảng năng lực báo quá tải)
  WO-2610/2611/2612 sinh từ MRP        WO-2620 sản phẩm theo số sê-ri
  WO-2650 tại $P2_CODE

Đơn bán hàng — đủ 6 trạng thái
  SO-1001 DRAFT   SO-1002 CONFIRMED   SO-1003 CANCELLED
  SO-1004 PARTIALLY_FULFILLED (6/10)  SO-1005 FULFILLED   SO-1006 IN_PRODUCTION
  SO-1007 CONFIRMED, đề xuất bị BLOCKED vì XE-TRE-EM chưa có quy trình công nghệ

Mua hàng
  PR-2001 DRAFT  PR-2002 APPROVED  PR-2003 CONVERTED  PR-2004 REJECTED  PR-2005 CANCELLED
  PO-3001 DRAFT  PO-3002 SENT      PO-3003 PARTIALLY_RECEIVED  PO-3004 RECEIVED  PO-3005 CANCELLED
  GR-4001 POSTED  GR-4002 CANCELLED  GR-4003 POSTED

MRP
  ${ID[planningRuns.A.code]}  phạm vi kho thành phẩm — đề xuất có đủ READY / WARNING / BLOCKED
  ${ID[planningRuns.B.code]}  toàn nhà máy, quét theo horizon

Lô hàng
  $LOT_HOLD_CODE      HOLD (giữ thủ công)       $LOT_REJECT_CODE  REJECTED (giữ thủ công)
  LO-BANH275-GIU  HOLD (đã duyệt, chưa QC)  LO-BANH275-DAT  AVAILABLE   LO-BANH275-LOI  REJECTED
  SN-XEDIEN-0001 / 0002  số sê-ri, QC đạt / QC loại

Toàn bộ mã và UUID: $MANIFEST
Hướng dẫn tra cứu khi thuyết trình: docs/demo-dataset-guide.md
================================================================================

EOF

# stdout carries only the manifest, so the script can be piped:
#   ./scripts/demo/seed-demo.sh | python -m json.tool
cat "$MANIFEST"
