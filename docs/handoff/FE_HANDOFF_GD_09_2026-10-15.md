# Giai đoạn 9 — Bàn giao Backend: Nhập thành phẩm và kiểm tra chất lượng

## §0 — Kết luận trong 3 dòng

- Vấn đề: FE/mock GĐ9 hoàn thành; cần BE triển khai hoặc cung cấp mapping cho phiếu nhập từng phần, giữ sản lượng đầu ra, duyệt atomic vào HOLD, QC từng phần và truy xuất nguồn.
- Ảnh hưởng: thủ kho, QC và quản lý chạy được demo; các phần mở rộng receipt/QC chưa tích hợp API được xác minh, các module khác vẫn dùng theo contract hiện có.
- Mức ưu tiên: P1 — phần mở rộng nhập/QC chưa dùng được bằng API; không chặn FE/mock tiếp tục giai đoạn sau.

**Bản:** v1 · **Ngày dự kiến gửi:** 15/10/2026 · **Mốc FE:** Session314, 15/10/2026 · **Phiên tài liệu:** Session315.
**PO:** duyệt phương án GĐ9 bằng “có”; yêu cầu “tạo tài liệu BE” sau FE delivery. **FE owner / BE owner cá nhân:** chưa chỉ định.
**Trạng thái:** chuẩn bị để PO gửi, chưa gửi ngoài, chưa nghiệm thu BE. Nếu gửi ngày khác đổi tên/ngày trước lần gửi đầu; sửa sau gửi dùng `_v2`, `_v3` cùng task.

Nhãn áp dụng toàn tài liệu:

- **[Đã kiểm chứng FE]:** đọc source/tests/browser mock, không chứng minh endpoint hoặc transaction DB đang deploy.
- **[Đề xuất BE]:** yêu cầu/path/DTO/permission/error cần BE thống nhất; đã có API tương đương thì trả mapping/OpenAPI, không xây trùng hoặc đổi contract cũ ngầm.
- **[Giả định]:** chính sách chưa BE xác nhận; PO duyệt FE/demo không thay xác nhận contract.
- **[Chưa kiểm chứng live]:** không gọi BE trong phiên tài liệu. JSON/UUID bên dưới là ví dụ đề xuất, không phải request/response/record server đã thử.

## §1 — Phạm vi

### 1.1 Toàn bộ 15 màn hình/phần giao diện mới và sửa

| Loại | Màn hình | Dữ liệu/hành vi cần BE |
|---|---|---|
| Sửa | Danh sách Nhập kho thành phẩm | Search mã/WO/Item/lô, lọc WO/kho/trạng thái/ngày, server pagination/totals |
| Mới/nâng cấp | Tạo/sửa phiếu nhập | WO, Item/UOM/kho kế thừa, Good cuối/đã duyệt/claim/còn lập, lượng/ngày/lô/hạn/ghi chú |
| Sửa | Chi tiết phiếu nhập | Tổng quan/Chất lượng/Nguồn sản xuất/Lịch sử, versions và capabilities hiện tại |
| Mới/nâng cấp | Gửi/rút/hủy | DRAFT/PENDING lifecycle, owner/action guard, lý do, audit và trả/giữ quota |
| Mới/nâng cấp | Hàng chờ duyệt | Current source/cap/lot/warehouse preview, approve/reject, requester/approver |
| Mới | Kiểm tra thành phẩm | Phiếu APPROVED còn pending QC, search/filters/page và hàng lịch sử |
| Mới | Ghi QC từng phần | Accepted/rejected/reason/current pending, confirmation và errors theo field |
| Mới | Chi tiết/lịch sử QC | Mỗi decision bất biến, actor/time, tổng đạt/loại/chờ, child/source lots/buckets |
| Mới/nâng cấp | Truy xuất nguồn | Final-output execution contributions; raw-material WO/issue/consumption/warehouse/lot, scope-aware links |
| Sửa | Chi tiết Lệnh sản xuất | Receipt/QC summaries, approved/awaiting/unclaimed/accepted/rejected/pending, CTA |
| Sửa | Ghi nhận sản xuất | Final-operation Good/source và handoff WO sang receipt |
| Sửa | Sản xuất dở dang | Output chưa nhập/claims/HOLD/đạt/loại, không đổi ledger tiêu hao/hoàn vật tư |
| Sửa | Tồn kho, lô và biến động | Production receipt/QC provenance, tracked split hoặc non-tracked buckets, quarantine guards |
| Sửa nguồn/nhãn | MRP và Đơn bán hàng | Future WO supply giảm khi nhập; accepted tồn eligible đúng một lần; QC accepted riêng delivered/fulfilled |
| Sửa | Sidebar/quyền | Read receipt/read QC tách action, label nghiệp vụ VI/EN, Plant scope |

FE routes để mô tả màn hình, **không phải endpoint BE**: `/manufacturing/production-receipts`, `/manufacturing/production-qc`; liên kết sang WO, execution, WIP, inventory lots/movements, planning và sales orders. Detail panel phải, form/dialog giữa; BE không cần mở màn hình để triển khai các quy tắc dưới đây.

**Trong phạm vi:** một receipt một WO/Item/output warehouse và một output lot khi tracking; partial receipt, claims, lifecycle, atomic approval, partial multi-round QC, genealogy tối thiểu, exact6, audit/authorization/replay, related projections và migration legacy.

**Ngoài phạm vi:** Delivery/Invoice/Payment GĐ10; full material/labor/overhead costing và close GĐ11; sửa/đảo/xóa chứng từ đã post; FG customer returns; sửa lại QC decision; tự nhập bù hàng loại; tự tạo production rework từ QC; multi-line/multi-WO receipt; serial tracking/MES; đổi warehouse/Item/lượng WO; tự chốt các quyết định GĐ6 và GĐ8 còn mở; sửa nguyên flow purchase QC của GĐ7.

**Môi trường FE [đã kiểm chứng]:** dev/demo mock, Session314 ngày15/10/2026, UI trên localhost cổng3004, Plant1/Admin, timezone Asia/Ho_Chi_Minh. Không gắn commit giả cho working tree chưa commit. Session315 chỉ lập tài liệu; không gọi BE/OpenAPI hoặc kiểm deploy mới nhất.

### 1.2 Existing API source và extension

**[Đã kiểm chứng FE source ngày15/10/2026, không phải OpenAPI live]:** API client dùng base có context `/api`; controller chuẩn `/v1`. Toàn bộ paths dưới đây ghi đầy đủ `/api`, host lấy từ môi trường BE khi nghiệm thu, không giả host production.

| Existing source API | Wire hiện FE đang gửi/đọc | Giới hạn cần giữ rõ |
|---|---|---|
| GET `/api/v1/production-receipts/candidates` | plantId/page/size; candidate workOrderId/code/status/Item/UOM/actualGoodQuantity/receiptedQuantity/availableToReceipt/tracking/plannedEndAt | Adapter hiện hydrate thêm WO detail từng candidate; chưa có claims/contributions/version authoritative cho GĐ9 |
| GET `/api/v1/production-receipts` | plantId/status/page/size; page ReceiptDto | Chưa gửi search/WO/warehouse/date/QC queue filters mới |
| GET `/api/v1/work-orders/{workOrderId}` | WO detail phục vụ candidate hydration | Không coi quyền đọc receipt mặc nhiên cấp full WO/cost |
| POST `/api/v1/work-orders/{workOrderId}/production-receipts` | destinationWarehouseId, quantity decimal string, optional lotNumber/note; Idempotency-Key | Chưa gửi receivedOn/expiresOn/expected versions; source contributions chưa contract |
| POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/submit` | Không body mới; Idempotency-Key | Không tự thêm versions vào adapter trước mapping |
| POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/approve` | Không body mới; Idempotency-Key | Chưa chứng minh transaction DB GĐ9 bằng source FE |
| POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/reject` | reason; Idempotency-Key | API trạng thái cũ cần tương thích |
| POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-disposition` | result=AVAILABLE hoặc REJECTED, reason; Idempotency-Key | Whole disposition; **không** biểu diễn partial accepted/rejected/pending |

Existing ReceiptDto có receiptId/code/workOrderId/status/tracking/Item/UOM/destinationWarehouseId/code/quantity, optional lotId/lotNumber/outputLotStatus/stockMovementId/postedAt/note/submittedAt/approvedAt/rejectedAt/rejectReason/qcResult/qcReason/qcAt/actor names, traceId/sourceWipTraceIds. `outputLotStatus` hoặc `qcResult` single-value không đủ mô tả mixed partial QC. Source types không chứng minh mọi field luôn có trên server hiện deploy.

`traceId/sourceWipTraceIds` trong receipt là business provenance; không thay thế **X-Trace-Id của HTTP response cùng lượt request** khi báo lỗi.

### 1.3 Luồng và quyền đề xuất

Quyền action đã có mã trong FE; hai quyền READ mới là đề xuất mapping BE. Các tên “đọc phiếu”, “duyệt”, “kiểm tra” là nhãn UI; không dùng mã kỹ thuật làm tên nghiệp vụ.

| Thao tác | Trạng thái trước → sau | Permission đề xuất mapping | Validation chính | Tác động |
|---|---|---|---|---|
| Đọc receipt/candidate tối thiểu | Không đổi | PERM_PRODUCTION_RECEIPT_READ hoặc MANAGE/APPROVE tương ứng | Current Company/Plant/assignment | Không stock/claim; QC-only chỉ approved projection liên quan |
| Đọc QC | Không đổi | PERM_PRODUCTION_QC_READ hoặc PERM_QUALITY_DISPOSITION | Cùng scope, chỉ approved receipts | Không cấp full master/WO/cost |
| Tạo/sửa nháp | Không có/DRAFT → DRAFT | PERM_PRODUCTION_RECEIPT_MANAGE | Owner nếu sửa; WO IN_PROGRESS/COMPLETED, nguồn known, cap/ref/date/lot | Giữ/đổi claim, không stock |
| Gửi | DRAFT → PENDING_APPROVAL | PERM_PRODUCTION_RECEIPT_MANAGE | Owner, versions/current source/cap/lot | Giữ nguyên claim, không stock |
| Rút | PENDING_APPROVAL → DRAFT | PERM_PRODUCTION_RECEIPT_MANAGE | Owner, reason, versions | Vẫn giữ claim |
| Hủy | DRAFT/PENDING_APPROVAL → CANCELLED | PERM_PRODUCTION_RECEIPT_MANAGE | Owner, reason, versions | Trả claim một lần, không stock |
| Từ chối | PENDING_APPROVAL → REJECTED | PERM_PRODUCTION_RECEIPT_APPROVE | Reason, versions/current scope | Trả claim, không stock |
| Duyệt | PENDING_APPROVAL → APPROVED | PERM_PRODUCTION_RECEIPT_APPROVE | Current refs/source/cap/versions/unique lot | Atomic HOLD/WO/WIP/movement/audit/replay |
| QC | APPROVED → APPROVED | PERM_QUALITY_DISPOSITION | Current source/posted identity/versions, amounts≤pending | Đổi phân bố HOLD/AVAILABLE/REJECTED, immutable decision |

Tất cả thao tác kiểm current authenticated principal, assignment chưa hết hạn/thu hồi và Company sở hữu Plant/WO/warehouse/Item. Actor lấy từ principal, **không nhận actorUserId/name từ body làm authority**. Direct-ID khác scope không lộ existence hoặc dữ liệu; đề xuất404 sau khi đã có quyền module, thiếu quyền module403. Current auth kiểm sau lock và **trước trả replay**, không dựa token/profile FE cũ.

**[Đã kiểm chứng FE / PO duyệt demo]:** cùng người được tạo/duyệt/QC nếu có đủ permissions; không tự áp override self-approve material issue lên receipt. **[Đề xuất BE, Q10]:** giữ chính sách đó hoặc BE chốt separation trước integration. Không dùng role tên Admin/Manager thay permission; chỉ seed demo Admin/Manager được migrate quyền mới trong FE, không tự grant custom roles/Operator.

## §2 — Bằng chứng tái hiện

**Không có lỗi/live request — đây là handoff FE/mock và đề xuất contract, không phải FE_DEFECT.**

| Mảnh bằng chứng BE | Trạng thái của phiên này |
|---|---|
| Request thật method/path/headers/body | Không có — không gọi BE |
| Response thật nguyên văn/HTTP/code | Không có |
| X-Trace-Id response header | Không có; không tự tạo trace để điền |
| Môi trường + giờ BE của cùng run | Không có; latest deployment/OpenAPI chưa xác minh |
| ID bản ghi BE đã dùng trong run | Không có; các UUID §3 chỉ là fixture đề xuất |

Không thể báo DB defect hoặc đóng live acceptance từ thiếu bằng chứng này. Trước live test, BE cung cấp môi trường/version/build và cách xác minh bản deploy; FE ghi đủ năm mảnh cùng request/record/run, che secret, giữ X-Trace-Id.

**[Đã kiểm chứng FE trước đó, Session314]:** 15/10/2026, UI local mock, các lượt QC PR-0001 hiển thị 13:45 theo Asia/Ho_Chi_Minh. WO mock id1/code WO-20261013-0001, planned10PCS/finalGood9PCS. Receipt mock `production-receipt-1`/PR-0001 lượng5, `production-receipt-2`/PR-0002 lượng4; đây không phải UUID hoặc record BE. PR1 parent lot18, child20 đạt3/child21 loại1 rồi child22 đạt1; PR2 đạt4. Quota chờ nhập0, accepted8/rejected1/pending0. Source PR1 contribution EX-0004 lượng5 và MI-0001 theo WO; WIP consumed9PCS+4.5M2, returned1PCS+.602041M2 giữ nguyên. Không có trace HTTP cho local mock. Tổng9 chỉ là phần tồn mới, không phải toàn bộ kho demo có FG tồn trước.

```text
Mong đợi (FE/mock): nhận5+4 từ finalGood9; QC đạt8/loại1, approved vẫn9, không mở quota nhập lại1.
Thực tế (FE/mock Session314): quan sát đúng các số trên, giữ lịch sử sau reload; chưa chứng minh BE đạt.
```

Kiểm chứng FE lưu lại: full103files/555tests passed; focused45 và finalGĐ9 25tests passed; full555 chạy trước hai test bổ sung cuối, không nhận là full557 mới. Build/TypeScript43 pages và i18n passed. Browser VI desktop/EN tablet, reload/Plant isolation/source links passed. Session315 không chạy lại app tests/build hoặc browser vì chỉ tạo tài liệu; kết quả trước không được nâng thành live BE evidence.

## §3 — Đầu ra mong đợi — Cần BE triển khai

Tất cả dòng **[Đề xuất BE]**; API tương đương đã có thì BE cung cấp mapping và proof thay vì xây trùng. “Chặn” ở bảng là **chặn tích hợp/nghiệm thu phần mở rộng GĐ9 bằng API**, không chặn FE/mock/code giai đoạn sau.

| # | Yêu cầu đúng/sai kiểm được | Endpoint/nguồn | Chặn FE? |
|---|---|---|---|
| R01 | List/candidate/detail trả đúng scope, filters/page/totals và đủ minimal fields không cần quyền full WO để đọc receipt | GET receipt/candidates/detail | Có API GĐ9 |
| R02 | Một receipt có đúng một WO/Item/output warehouse, tracked một lot; source khóa theo WO hiện tại | POST/PATCH receipt | Có API GĐ9 |
| R03 | Cấp cap từ Good execution công đoạn cuối, clamp planned, không sum Good mọi operation hoặc dùng rework pending | Candidate/output projection | Có API GĐ9 |
| R04 | DRAFT/PENDING giữ claim; sửa loại claim của chính receipt; withdraw giữ, reject/cancel trả đúng một lần | Create/PATCH/lifecycle | Có API GĐ9 |
| R05 | Contribution unique execution IDs, tổng bằng receipt qty, không vượt Good chưa claim của từng nguồn | Create/PATCH/submit/approve/source | Có API GĐ9 |
| R06 | Chỉ owner sửa/gửi/rút/hủy hợp lệ; reject/withdraw/cancel yêu cầu reason; terminal records bất biến | Lifecycle endpoints | Có API GĐ9 |
| R07 | Approval commit receipt/claim/stock HOLD/movement/WO approved/output WIP/audit/replay cùng transaction hoặc không effect nào | Approve | Có API GĐ9 |
| R08 | Approval recheck current refs/source/expiry/duplicate/versions/cap; không tự đổi warehouse hoặc lot khi conflict | Approve | Có API GĐ9 |
| R09 | QC accepted≥0, rejected≥0, tổng>0≤pending; rejected>0 có reason; mỗi decision bất biến | POST qc-decisions | Có API GĐ9 |
| R10 | Tracked QC tạo child/source quantities, pending giữ parent HOLD; nontracked không có fake lot và cách ly theo receipt | QC/inventory | Có API GĐ9 |
| R11 | QC giữ tổng OnHand/total value/approved receipt, không receipt lần hai hoặc mở quota từ rejected | QC/WO/WIP | Có API GĐ9 |
| R12 | HOLD/REJECTED/expired không eligible cho issue/reserve/transfer/MRP/delivery, generic mutation không bypass production QC | Inventory commands/netting | Có API GĐ9 |
| R13 | Source trả final execution contributions thật và WO-level raw lineage; missing consumption/output evidence giữ unknown | GET source | Có API GĐ9 |
| R14 | WO/WIP summaries đồng nhất receipt approved/claims/accepted/rejected/pending, không debit nguyên liệu đã issue lần hai | WO/WIP | Có API GĐ9 |
| R15 | Future WO supply giảm phần approved; QC accepted chỉ đi vào stock canonical một lần; saved MRP runs bất biến | Planning | Có API GĐ9 |
| R16 | QC accepted tách delivered/shipped/fulfilled; QC không đổi SO thành FULFILLED | SO/allocation projections | Có API GĐ9 |
| R17 | Server xử lý NUMERIC(19,6) decimal string, reject excess scale/overflow, không binary float/round3 | Mọi qty/command | Có API GĐ9 |
| R18 | Read/action/current scope/assignment kiểm cả direct-ID và replay; read-only/QC không lộ cost | Mọi endpoint | Có API GĐ9 |
| R19 | Versions/concurrent commands/claims dùng DB lock hoặc tương đương, không overclaim/double approve/double QC | Mọi command | Có API GĐ9 |
| R20 | Same key+principal/scope/command/body replay once; changed payload conflict; failure rollback không lưu success | Mọi command | Có API GĐ9 |
| R21 | Date/time/expiry, actor/timestamps/audit server-authoritative, nhận date≤PlantToday và validate current eligibility | Create/submit/approve/QC | Có API GĐ9 |
| R22 | Legacy giữ IDs/history, thiếu output evidence thì read-only/unknown, không fake source/cap hoặc reset data | Migration/reads | Có API GĐ9 |
| R23 | Unknown FG valuation không biến thành0/full material cost phiếu đầu; costing pending hiển thị rõ, QC không tạo value lần hai | Inventory/WIP/cost projection | Có API GĐ9 |
| R24 | API cũ và single-value QC không bị đổi nghĩa ngầm; extension có contract/version/capability và migration rõ | Existing + extension API | Có API GĐ9 |

### 3.1 Quy tắc số lượng, trạng thái và nguồn

Ký hiệu cùng WO/output UOM: `P=plannedQuantity`; `G=actualFinalGoodQuantity` từ final-operation executions gồm retry Good hợp lệ; `C=min(P,G)`; `A=sum APPROVED receipt qty`; `D=sum DRAFT/PENDING qty`; `U=max(0,C−A−D)`; `W=max(0,C−A)`.

- **[Đã kiểm chứng FE]:** projection cap/awaiting của mock dùng C clamp planned; bình thường G≤P nên W=G−A. **[Đề xuất BE]:** trả riêng G và C, không gọi clamped C là actual G; bất thường G>P hoặc A>C phải có integrity warning/block, không che bằng max0 rồi cho tiếp. Q3 chốt nguồn/cap, Q4 chốt projection này.
- Nháp q>0 và q≤U; edit dùng U loại chính draft cũ. `sum(contributions.qty)=q`, mỗi execution một dòng, quantity>0; trên mỗi source, tổng contribution của DRAFT/PENDING/APPROVED≤Good source. Chọn nguồn ổn định theo execution time+ID, lưu snapshot; BE sinh contributions, không tin FE khai nguồn không có evidence.
- Hủy/từ chối trả claim và contributions, không xóa history. Pending rút về nháp vẫn claim. Không tự expiry draft/TTL trong mock; nếu BE cần TTL phải chốt Q7 và phản ánh UI, không lặng lẽ mất claim.
- Approval chuyển q từ D sang A, W giảm q; U không tăng. Không cho nhận planned chưa có Good, không cần WO COMPLETED nếu IN_PROGRESS có final Good known. Cancelled/closed/unreleased WO không nhận mới theo policy chốt.
- `acceptedTotal=sum decisions.accepted`; `rejectedTotal=sum decisions.rejected`; APPROVED `pending=q−acceptedTotal−rejectedTotal`, luôn≥0. Phiếu chưa APPROVED pendingQC=0, không lấy q để tạo hàng chờ QC.
- Receipt status và quality state khác nhau: APPROVED không đổi sau QC; QC đề xuất NOT_STARTED (chưa kiểm), PARTIAL (còn chờ sau decision), ACCEPTED (toàn đạt), REJECTED (toàn loại), MIXED (kiểm hết có cả đạt và loại). Nếu còn pending và có rejected thì PARTIAL + các totals, không mất cảnh báo phần đã loại. Draft qualityState không có. Single outputLotStatus không được nói toàn receipt AVAILABLE khi còn HOLD/REJECTED.
- Lô cha HOLD quantity0 vẫn là hồ sơ nguồn sau kiểm hết, không có pending stock; child quantity là **tồn hiện tại**, khác lượng đã QC trong immutable decision nếu về sau đã xuất/chuyển.
- Genealogy gồm hai tầng: contribution FG → final execution thực; raw lineage receipt→WO→posted issue/confirmed consumption→source warehouse/lot. Không có per-unit raw allocation thì `materialAttribution=WORK_ORDER_LEVEL`, không lấy toàn consumed của WO gán riêng cho từng partial receipt. Legacy thiếu thì UNKNOWN và reason.

### 3.2 Dữ liệu đề xuất và nullability

**Wire rules:** entity IDs là opaque UUID strings, không dùng mock numeric IDs/codes làm PK. Quantities JSON string decimal plain, scale≤6, NUMERIC(19,6); version integer≥1; dates YYYY-MM-DD; timestamp RFC3339 UTC string, Plant timezone IANA để trình bày/ngày nghiệp vụ. Không làm tròn excess scale tự động. Tất cả field dưới đây đề xuất **required trừ nơi ghi optional**. Nullable có thể omitted theo NON_NULL envelope/serializer; absent phải giữ unknown, không default0. Empty arrays biết là không có, khác omitted do không được đọc. Cost không được phép thì omitted, không null để suy giá trị0.

| Field | Kiểu | Nullable | Nguồn giá trị/công thức đề xuất | Dùng ở đâu UI |
|---|---|---|---|---|
| receiptId/code/status/version | UUID/string/enum/int | Không | Canonical receipt, server code và revision | List/detail/actions |
| companyId/plantId | UUID | Không | Tenant/Plant từ record + scope | Isolation |
| workOrderId/workOrderCode/workOrderVersion | UUID/string/int | Không | Canonical WO, không fake id theo code | Source, optimistic command |
| itemId/itemSku/itemName/uom/outputTrackingMethod | UUID/string/string/string/enum | Không | Receipt snapshot từ WO/Item; NON_TRACKED/LOT_TRACKED | Header/form |
| destinationWarehouseId/Code/Name | UUID/string/string | Không | WO output warehouse snapshot, current active check riêng | Locked source |
| quantity | decimal string | Không | q>0 | Form/detail |
| receivedOn/plantTimeZone | date/string | Không | User business date≤server PlantToday, timezone server | Form/history |
| lotNumber/expiresOn | string/date, optional | Có | Tracked proposed lot; expiry optional nếu policy chưa mandatory; NON_TRACKED absent | Form |
| lotId/stockMovementId/postedAt | UUID/UUID/instant, optional trước post | Có | Posted parent lot nếu tracked, movement và server time | Source/posted identity |
| note | string, optional | Có | User text≤2000 | Detail |
| createdByUserId/createdByUsername/createdAt | UUID/string/instant | Không | Principal/server, snapshot tên cho history | Owner/audit |
| submittedAt/approvedAt/rejectedAt/cancelledAt | instant, optional | Có | Server action history | History |
| approvedByUserId/rejectedByUserId/qcByUserId | UUID, optional | Có | Actor mỗi action; detailed decision riêng | History |
| rejectReason/cancelReason/withdrawReason | string, optional | Có | Required theo command, trim5–2000 | History/errors |
| outputEvidenceKnown/legacyReadOnly | boolean | Không | Evidence/migration authoritative | Disabled action/warning |
| outputUnknownReason | string, optional | Có | Vì sao source thiếu/không đối soát | Unknown warning |
| actualFinalGoodQuantity/receiptableGoodCap | decimal string | Có khi evidence unknown | G và C, **không đổi tên lẫn nhau** | Candidate/cap |
| approvedReceiptQuantity/claimedQuantity | decimal string | Không khi ledger known; thiếu migration phải flagged | A/D; không count rejected/cancelled | WO/WIP/candidate |
| awaitingReceiptQuantity/unclaimedReceiptQuantity | decimal string | Có khi unknown | W/U | Form/WIP |
| acceptedQuantity/rejectedQuantity/pendingQcQuantity | decimal string | Không khi QC ledger known | Totals, nonapproved pending0 | List/QC/WIP |
| qualityState | enum, optional chưa approved | Có | NOT_STARTED/PARTIAL/ACCEPTED/REJECTED/MIXED | Badge khác receipt status |
| contributions | array<{executionId,executionCode,operationId,quantity}> | Có khi legacy unknown; otherwise array | Final-output claims, UUID/string/UUID/decimal | Source detail |
| qcDecisions | array<QcDecision>, detail có page riêng nếu nhiều | Không khi ledger known | Immutable decisions, không overwrite | Quality history |
| qcDecisionId/acceptedQuantity/rejectedQuantity/reason/actorId/actorName/decidedAt | UUID/decimal/decimal/string/UUID/string/instant | reason optional nếu reject0, còn lại không | Server event và quantities | QC row |
| outputLots | array<OutputLot>, tracked only | Không khi known; NON_TRACKED [] | Posted parent + children | Quality detail |
| lotId/lotNumber/parentLotId/status/currentQuantity | UUID/string/UUID/enum/decimal | parentLotId optional cho root | Stock canonical; currentQuantity không phải QC decision amount | Lot table/link |
| receiptId/qcDecisionId trên inventory movement | UUID | receiptId không; qcDecisionId optional RECEIVE | Provenance server, phân biệt production/purchase | Movement/source |
| stockQuantityDelta/dispositionQuantity/qualityFrom/qualityTo | decimal/decimal/enum/enum | disposition fields optional RECEIVE | QC quantity delta0 nhưng dịch quality thực có amount | Stock history |
| materialAttribution/issueLines/executionLinks | enum/array/array | UNKNOWN cho legacy | WORK_ORDER_LEVEL; issueLineId/Item/UOM/kho/lô/issued/consumed; consumed null khi unknown | Genealogy |
| qcAcceptedQuantity trên SO allocation/line | decimal string | Có cho legacy chưa reconcile | Production QC accepted theo allocation, khác delivered | SO/source coverage |
| allowedActions/blockers | string[]/array<{code,field?,message}> | Không | Current server authorization/state; không thay kiểm command | Buttons/warnings |
| valuationStatus/receiptValue/currency | enum/decimal/string | receiptValue/currency optional, unknown và unauthorized phân biệt | PENDING_COSTING/KNOWN/UNKNOWN_LEGACY; authoritative costing sau | Có quyền cost mới đọc |

Danh sách chỉ cần totals/summaries; detail/source/QC history trả phần nặng theo endpoint, tránh nhúng mọi ledger/cost vào list. Unauthorized cost fields không xuất hiện ở source hoặc replay. QC-only đọc approved receipts liên quan và minimal WO/issue names/quantity, không nhận full master/WO detail bằng quyền mượn.

### 3.3 Endpoint mở rộng đề xuất

Tất cả path dưới đây **[Đề xuất BE, chưa deploy được xác minh]**, trừ existing paths đã ghi §1.2. Giữ tên wire `lotNumber`, `note`, `destinationWarehouseId` của API cũ; mock `lotCode/notes/warehouseId` chỉ là tên nội bộ. Mọi mutation có Idempotency-Key, authenticated Company/Plant scope, expectedWorkOrderVersion; existing receipt thêm expectedVersion. Không gửi actor hoặc trusted contributions từ FE. Nếu BE dùng If-Match/revision riêng thay body thì mapping phải xác định Q12, không trộn hai cơ chế bất nhất.

Success command đề xuất trả command-result projection ở §3.4 J2/J6, có resulting versions và các effect/totals cần thiết; FE refetch detail/list/WO/WIP/inventory/related MRP-SO theo tác động. Không bắt server trả full detail/source ledger hoặc money trong replay result. Success create201, các command/PATCH200; nếu BE giữ response ReceiptDto đầy đủ thì thống nhất mapping và cost redaction trước tích hợp.

| Endpoint hiện có/đề xuất | Query/body | Success result | Page/search | Quyền/scope | Idempotency |
|---|---|---|---|---|---|
| Mở rộng GET `/api/v1/production-receipts/candidates` | plantId, workOrderId?, search?, page=0,size=20 | Minimal candidate với refs, versions, output evidence/G/C/A/D/W/U/blockers | Có server search/page | READ/MANAGE/APPROVE; scoped, không full WO/cost | Không write |
| Mở rộng GET `/api/v1/production-receipts` | plantId,status?,workOrderId?,destinationWarehouseId?,search?,receivedFrom?,receivedTo?,qualityState?,pendingQc?,hasQcDecision?,page,size | Page summaries và totals đúng filters | page zero-based, size1–100; ngày inclusive receivedOn; sort createdAt desc,id stable | Receipt read hoặc action; QC-only approved projection | Không write |
| Mới GET `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}` | receipt thuộc đúng WO; Plant scope | Full minimal detail + counters/versions/allowedActions/quality | Không | Receipt/QC read tương ứng | Không write |
| Mở rộng POST `/api/v1/work-orders/{workOrderId}/production-receipts` | expectedWorkOrderVersion, quantity, destinationWarehouseId, lotNumber?, receivedOn, expiresOn?, note? | Receipt version1 + resulting workOrderVersion; contributions server sinh | Không | MANAGE, source scope | Có |
| Mới PATCH `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}` | expectedVersion,expectedWorkOrderVersion, quantity, lotNumber?,receivedOn,expiresOn?,note?; source WO/Item/kho immutable | Updated DRAFT + revisions/source claim | Không | MANAGE+owner | Có |
| Mở rộng POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/submit` | expectedVersion,expectedWorkOrderVersion | Receipt PENDING + revisions | Không | MANAGE+owner | Có |
| Mới POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/withdraw` | expectedVersion,expectedWorkOrderVersion,reason | Receipt DRAFT, claim retained | Không | MANAGE+owner | Có |
| Mới POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/cancel` | expectedVersion,expectedWorkOrderVersion,reason | Receipt CANCELLED, claim released | Không | MANAGE+owner | Có |
| Mở rộng POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/approve` | expectedVersion,expectedWorkOrderVersion | APPROVED+posted identity/HOLD/counters/revisions | Không | APPROVE | Có |
| Mở rộng POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/reject` | expectedVersion,expectedWorkOrderVersion,reason | REJECTED+claim released/revisions | Không | APPROVE | Có |
| Mới POST `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-decisions` | expectedVersion,expectedWorkOrderVersion,acceptedQuantity,rejectedQuantity,reason? | Receipt totals/revisions + immutable qcDecision + stock effects IDs | Không | QUALITY_DISPOSITION | Có |
| Mới GET `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/qc-decisions` | page,size | Page immutable decisions, totals riêng không theo page | Có, decidedAt/id stable | QC/receipt read phù hợp | Không write |
| Mới GET `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/source` | plant scope, optional page/size cho issueLines | Contributions, materialAttribution, minimal issue/consumption/warehouse/lot links, outputLots | Issue lineage page nếu lớn; contributions summary complete | Receipt/QC read, cost omitted | Không write |
| Mới GET `/api/v1/work-orders/{workOrderId}/production-receipts/{receiptId}/events` | page,size | Page action/actor/reason/at/revisions | Có | Receipt read, QC-only quality history tối thiểu | Không write |

QC workspace dùng list `status=APPROVED&pendingQc=true` cho hàng chờ và `status=APPROVED&hasQcDecision=true` cho lịch sử theo Q21; lịch sử gồm mọi receipt đã có quyết định kể cả còn pending, không chỉ fully inspected. Không dùng pendingQc=false thay thế lịch sử vì sẽ mất receipt kiểm dở. Search trim/case-insensitive trên receipt code, WO code, Item SKU/name và lotNumber; optional filters kết hợp AND, hasQcDecision false nghĩa chưa có quyết định. totalElements tính đúng scope/filters, không phải toàn Plant bỏ filter.

GET WO/WIP/planning/SO/inventory hiện có bổ sung authoritative summaries/provenance theo mapping BE, **không bắt BE tạo endpoint kho/money thứ hai**. Capabilities xác nhận new receipt read/edit/claims/partialQC/source/version support; nếu chỉ old whole-QC thì giữ UI legacy, extension unavailable.

### 3.4 Ví dụ wire đề xuất — cùng fixture, không phải API thật

Fixture UUID chỉ minh họa: Company `10000000-0000-4000-8000-000000000001`; Plant1 `10000000-0000-4000-8000-000000000002`; WO `10000000-0000-4000-8000-000000000010`; FG `10000000-0000-4000-8000-000000000020`; output warehouse `10000000-0000-4000-8000-000000000030`; final execution `10000000-0000-4000-8000-000000000041` Good9; receipt1/2 `10000000-0000-4000-8000-000000000051`/`10000000-0000-4000-8000-000000000052`; parent1/2 `10000000-0000-4000-8000-000000000061`/`10000000-0000-4000-8000-000000000062`. WO planned10, finalGood9, output UOM PCS; active Item/UOM/kho, LOT_TRACKED. Không có opening FG stock/reservation trong **fixture BE đề xuất**, khác kho demo đã có opening stock.

Headers minh họa cho command, secret che; `X-Plant-Id` theo current scoped client, BE cross-check body/path. X-Trace-Id phải do server trả, không điền giá trị giả vào evidence:

```text
Authorization: Bearer <REDACTED>
X-Plant-Id: 10000000-0000-4000-8000-000000000002
Content-Type: application/json
Idempotency-Key: gd09-fixture-pr1-create
```

**J1 — body POST `/api/v1/work-orders/10000000-0000-4000-8000-000000000010/production-receipts`**, WOversion20, receipt chưa có. Response HTTP201 đề xuất, SAVE tăng WO lên21/receiptversion1:

```json
{
  "expectedWorkOrderVersion": 20,
  "destinationWarehouseId": "10000000-0000-4000-8000-000000000030",
  "quantity": "5.000000",
  "lotNumber": "GD9-FG-01",
  "receivedOn": "2026-10-15",
  "note": "Nhap dot 1"
}
```

**J2 — success create tối thiểu**, các field detail required khác theo §3.2 có thể nằm trong DTO đầy đủ, dưới đây là **command result projection** riêng để tránh giả response list/detail đầy đủ. Mỗi command trả projection này cộng phần effect tương ứng; FE refetch detail. Fields required của command result: receiptId/status/version/workOrderId/workOrderVersion, effects nếu có. `result` required cho mọi success ở đây:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "receiptId": "10000000-0000-4000-8000-000000000051",
    "status": "DRAFT",
    "version": 1,
    "workOrderId": "10000000-0000-4000-8000-000000000010",
    "workOrderVersion": 21,
    "claimedQuantity": "5.000000",
    "unclaimedReceiptQuantity": "4.000000",
    "contributions": [
      {"executionId": "10000000-0000-4000-8000-000000000041", "quantity": "5.000000"}
    ]
  }
}
```

**J3 — submit**, POST `/api/v1/work-orders/10000000-0000-4000-8000-000000000010/production-receipts/10000000-0000-4000-8000-000000000051/submit`, key `gd09-fixture-pr1-submit`. Success200 receiptversion2/WO22, status PENDING_APPROVAL, claim5, no stock:

```json
{"expectedVersion": 1, "expectedWorkOrderVersion": 21}
```

**J4 — approve**, POST `/api/v1/work-orders/10000000-0000-4000-8000-000000000010/production-receipts/10000000-0000-4000-8000-000000000051/approve`, key `gd09-fixture-pr1-approve`. Success200 receiptversion3/WO23, APPROVED, lot61 HOLD5, approved5/awaiting4:

```json
{"expectedVersion": 2, "expectedWorkOrderVersion": 22}
```

Receipt2 sau đó create4 từ WO23→24/version1, submit24→25/version2, approve25→26/version3; lot GD9-FG-02/HOLD4, contribution cùng execution41 phần4 còn lại. Sau hai approval: approved9/claims0/U0/W0/HOLD9. Không dùng versions của PR1 cho PR2.

**J5 — QC PR1 lần1**, POST `/api/v1/work-orders/10000000-0000-4000-8000-000000000010/production-receipts/10000000-0000-4000-8000-000000000051/qc-decisions`, key `gd09-fixture-pr1-qc1`:

```json
{
  "expectedVersion": 3,
  "expectedWorkOrderVersion": 26,
  "acceptedQuantity": "3.000000",
  "rejectedQuantity": "1.000000",
  "reason": "Mot san pham khong dat kich thuoc"
}
```

**J6 — command result QC**, HTTP200. Child IDs và codes do server sinh; UUID dưới đây chỉ fixture. `currentQuantity` đúng lúc vừa QC; decision quantities bất biến. QC stock quantity delta0, dịch chất lượng tổng4, pending1:

```json
{
  "code": "SUCCESS",
  "message": "Success",
  "result": {
    "receiptId": "10000000-0000-4000-8000-000000000051",
    "status": "APPROVED",
    "version": 4,
    "workOrderId": "10000000-0000-4000-8000-000000000010",
    "workOrderVersion": 27,
    "qualityState": "PARTIAL",
    "acceptedQuantity": "3.000000",
    "rejectedQuantity": "1.000000",
    "pendingQcQuantity": "1.000000",
    "qcDecision": {
      "qcDecisionId": "10000000-0000-4000-8000-000000000071",
      "acceptedQuantity": "3.000000",
      "rejectedQuantity": "1.000000",
      "reason": "Mot san pham khong dat kich thuoc",
      "actorId": "10000000-0000-4000-8000-000000000090",
      "actorName": "Demo QC",
      "decidedAt": "2026-10-15T06:45:00Z"
    },
    "outputLots": [
      {"lotId":"10000000-0000-4000-8000-000000000061","lotNumber":"GD9-FG-01","status":"HOLD","currentQuantity":"1.000000"},
      {"lotId":"10000000-0000-4000-8000-000000000063","lotNumber":"GD9-FG-01-QC-01-A","parentLotId":"10000000-0000-4000-8000-000000000061","status":"AVAILABLE","currentQuantity":"3.000000"},
      {"lotId":"10000000-0000-4000-8000-000000000064","lotNumber":"GD9-FG-01-QC-01-R","parentLotId":"10000000-0000-4000-8000-000000000061","status":"REJECTED","currentQuantity":"1.000000"}
    ],
    "stockQuantityDelta": "0.000000"
  }
}
```

**J7 — PR1 QC lần2**, cùng path J5, key `gd09-fixture-pr1-qc2`. Success version5/WO28, accepted4/rejected1/pending0, quality MIXED. PR2 sau đó QC4 accepted với receiptversion3/WO28→receiptversion4/WO29, toàn đạt. Reason omitted hợp lệ khi rejected0:

```json
{"expectedVersion":4,"expectedWorkOrderVersion":27,"acceptedQuantity":"1.000000","rejectedQuantity":"0.000000"}
```

**J8 — server pagination envelope minh họa**, GET `/api/v1/production-receipts?plantId=10000000-0000-4000-8000-000000000002&status=PENDING_APPROVAL&page=0&size=20` sau fixture hoàn tất không còn pending approval:

```json
{"code":"SUCCESS","message":"Success","result":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true}}
```

**J9 — QC vượt pending**, HTTP409 đề xuất, không `result` thành công. Field là wire name; không trả entity ngoài scope trong lỗi. X-Trace-Id header thật sẽ được lưu khi live, không fabricate trong ví dụ:

```json
{"code":"PRODUCTION_QC_QUANTITY_EXCEEDED","message":"Inspection quantity exceeds the remaining quantity","errors":[{"field":"acceptedQuantity","message":"Refresh the remaining inspection quantity before confirming"}]}
```

**J10 — unknown legacy source projection**, GET source trong fixture riêng không gán legacy vào WO F1 đã biết. Không invent execution41 cho record cũ:

```json
{"code":"SUCCESS","message":"Success","result":{"receiptId":"10000000-0000-4000-8000-000000000099","legacyReadOnly":true,"outputEvidenceKnown":false,"outputUnknownReason":"Final operation contribution ledger is unavailable","materialAttribution":"UNKNOWN","allowedActions":[]}}
```

### 3.5 Tác động kho/WIP/value và tích hợp

| Event | OnHand | Reserved | Available eligible | HOLD/REJECTED | WIP/value | Chứng từ |
|---|---|---|---|---|---|---|
| SAVE/SUBMIT/WITHDRAW | 0 delta | 0 delta | 0 delta | 0 delta | Physical W không đổi, claim giữ output | Receipt/claim/event/revisions |
| CANCEL/REJECT pending | 0 | 0 | 0 | 0 | W không đổi; claim/contribution release một lần | Terminal history |
| APPROVE q | +q FG | 0 | 0 | HOLD+q | Output W−q; raw consumed/returned unchanged; FG value pending | RECEIVE + receipt + WO/claim/audit/replay |
| QC accept a/reject b | 0 delta | 0 delta | +a nếu eligible/không expiry, source allocation áp riêng | HOLD−a−b, REJECTED+b | Output W/A không đổi; total value không tăng | Decision + child/bucket + disposition movement |
| Replayed command | 0 delta mới | 0 | 0 | 0 | Không ghi lại history/counters/value | Stored result cùng key, current auth |
| Failure cuối transaction | 0 effect committed | 0 | 0 | 0 | Rollback WO/receipt/claim/value/result | Error, không orphan lot/movement |

“0” trong bảng là **delta**, không phải số tồn hoặc giá trị chưa biết bằng0. QC movement quantity delta0 cần dispositionQuantity/qualityFrom/To riêng hoặc event equivalent; không viết RECEIVE a lần nữa. Giữ source receipt/qcDecision IDs kể cả NON_TRACKED accepted gộp normal pool, nếu mất provenance chi tiết phải có immutable movement ledger để trace.

**F1 — số lượng fixture chuẩn:**

| Mốc | A approved | D claims | U chưa claim | W chờ nhập | FG OnHand mới | HOLD | AVAILABLE | REJECTED |
|---|---|---|---|---|---|---|---|---|
| FinalGood9, chưa receipt | 0 | 0 | 9 | 9 | 0 | 0 | 0 | 0 |
| Draft1=5 | 0 | 5 | 4 | 9 | 0 | 0 | 0 | 0 |
| Approve1=5 | 5 | 0 | 4 | 4 | 5 | 5 | 0 | 0 |
| Draft2=4 | 5 | 4 | 0 | 4 | 5 | 5 | 0 | 0 |
| Approve2=4 | 9 | 0 | 0 | 0 | 9 | 9 | 0 | 0 |
| QC1 accept3/reject1 | 9 | 0 | 0 | 0 | 9 | 5 | 3 | 1 |
| QC1 accept thêm1 | 9 | 0 | 0 | 0 | 9 | 4 | 4 | 1 |
| QC2 accept4 | 9 | 0 | 0 | 0 | 9 | 0 | 8 | 1 |

Cuối F1: physical9=8eligible+1rejected, approved9, U/W0. Final-operation Good9 khác production Scrap1 từ GĐ8, QC rejected1 là metric khác; không suy finalGood8 hoặc cho receipt lại1.

**F2 — value unknown:** ở mock mới, FG RECEIVE9 có value unknown/null, không0VND; raw-material consumed/value ledger giữ nguyên. Tổng FG value sau QC vẫn unknown, không nói đã đối soát đầy đủ WIP/cost. BE cần quyết định Q26: cho physical receipt trước cost pending hay đòi authoritative valuation trước post. Nếu BE có policy giá authoritative V hợp lệ, tổng FG value trước/sau QC phải bằngV; từng child/pending allocation cần residual policy, không lấy V/q bằng float hoặc cảV cho mỗi child. Full costing không thuộc task này, contract phải diễn tả pending/known và không che unknown.

**F3 — claims/precision fixture riêng:** Good cuối lần gốc5 và retry3 rồi retry1 cho tổng9; receipt5 dùng source5, receipt4 dùng3+1, không cấp lại source5. Với output `0.000002`, receipt `0.000002`, QC accepted`0.000001`/rejected`0.000001` thì pending0; không round3 và không fake cost0.

**MRP/SO:** trên fixture tổng FG nhu cầu10, opening eligible0, trước receipt committed future WO supply9 nên thiếu1. Approve9 giảm future supply0 và HOLD9 excluded nên thiếu10. QC accepted8 tạo eligible8, future0, thiếu2. Không thêm lại approved9 vào future supply và không tính HOLD9 cùng available8. Đây là isolated no-earmark fixture đã được test mock; không dùng để tự chốt effective SO coverage/earmark policy còn mở GĐ6. Source-linked production QC cập nhật metric accepted theo allocation cap, không tăng delivered/invoiced/paid hoặc đổi SO FULFILLED. Legacy fulfilled đang mang nghĩa production accepted phải có migration/mapping riêng, không relabel thành giao khách; historical run/allocation giữ trace.

### 3.6 Atomicity, concurrency, date và migration

- **[Đề xuất BE]:** transaction DB khóa source WO/Good pool/claim và stock liên quan theo thứ tự ổn định; không copy localStorage lock của FE làm bảo đảm multi-user. Concurrent create/approve/QC chỉ commit khi current versions/caps/source đúng. Mọi stock/receipt/WO/claim/QC/audit/idempotency result rollback cùng failure. DB unique constraint lot theo policy Q8/Q9, không chỉ UI precheck.
- Source kế thừa WO tại create, giữ snapshot Item/UOM/tracking/kho; nếu current source thay trước approve hoặc QC, báo conflict, không đổi source âm thầm. User phải refresh/xử lý lại, không tự post sang kho khác.
- Tracked lot number trim, nonempty≤100; case-insensitive uniqueness như FE mock nhưng phạm vi tenant/Plant cần Q8. Mock posted lots kiểm theo Item trong inventory state, pending lot claim kiểm current Plant: đây **không phải proof** uniqueness multi-Company/Plant DB. Child number server sinh độc lập, giữ parent/receipt/qc IDs, handle collision atomic. NON_TRACKED lotNumber/expiresOn absent, no fake lotId.
- Date validation server: receivedOn≤PlantToday; expiry nếu có phải>receivedOn và>PlantToday khi save/submit/approve. Current accept không được expiry, rejection-only expired có reason được phép theo Q24. Timezone fallback/expiry boundary cần Q23; không lấy browser clock làm nguồn cuối.
- Reason trim5–2000 cho reject/withdraw/cancel và QC rejected>0; note≤2000. QC accepted-only reason optional; mock chưa enforce upper bound riêng cho optional accepted-only reason, BE đề xuất≤2000 và FE đồng bộ sau contract, không coi đã kiểm chứng mọi path.
- ExpectedVersion và expectedWorkOrderVersion hiện mock tăng khi receipt SAVE/lifecycle/QC làm ledger WO thay đổi; command result trả resulting revisions. Mapping toàn WO revision hay receipt-ledger revision riêng là Q12. Same-key replay đã committed phải xử lý **trước version mismatch cũ**, nhưng sau current auth/scope; trả nguyên result không tạo effect lần2. Sau success FE refetch current detail trước thao tác tiếp.
- Idempotency proposal scope tenant+Plant+principal+command/resource+key, fingerprint canonical semantic payload bao gồm expected versions; key reused changed payload409. Failed transaction không lưu success. Timeout network retry same key/payload; user edit hoặc refresh state rồi tạo intent mới dùng key mới. Retention đề xuất≥24h/BE chốt Q14; hết hạn không tự mù retry command đã post.
- Legacy migration giữ record IDs/code/time/trace/sourceWipTraceIds và stock relations. Không có evidence contributions thì read-only/unknown, không tạo execution giả hoặc trừ approved hai lần. Authoritative old posted receipt quantity vẫn phải được đối soát vào aggregate/cap trước cho nhận mới. GĐ9 extension chỉ mở sau ledger reconcile/capability, không chạy hai receipt stores cho cùng WO.
- Chưa có reversal/correction API: APPROVED/QC decisions không PATCH/DELETE; sai đã post báo workflow correction riêng, không hướng dẫn sửa DB hoặc negative receipt trong task này. QC reject không tự tạo writeoff/remove stock; REJECTED vẫn OnHand cách ly chờ nghiệp vụ khác.

### 3.7 Nghiệm thu và rollout

Tất cả A01–A40 là **live pending/chưa chạy**. BE tự tạo fixture tương đương, trả real IDs; FE lưu request/response/status/code/X-Trace-Id/environment+version+time/IDs **cùng run**. Cột evidence “E” nghĩa bộ năm mảnh đó cộng đối chiếu DB/stock/event theo ca, không phải evidence đã có.

| ID | Đầu vào/record | Thao tác | Kết quả kiểm được | Bằng chứng | Trạng thái |
|---|---|---|---|---|---|
| A01 | Candidate F1 G9/P10 | List/read | G9,C9,A0,D0,U9,W9, minimal scoped refs | E | Chưa chạy live |
| A02 | F1 WO/Item/kho | Create5 | DRAFT claim5/U4, no stock, server contribution5 | E | Chưa chạy live |
| A03 | Draft5 | Create thêm5 | Conflict cap, D vẫn5/no orphan | E | Chưa chạy live |
| A04 | Draft5 | Edit thành4 rồi5 | Own claim excluded, D4 rồi5, no stock | E | Chưa chạy live |
| A05 | Draft5 | Submit→withdraw(reason) | PENDING→DRAFT, D5 giữ nguyên, event actor | E | Chưa chạy live |
| A06 | Draft/PENDING5 | Cancel/retry | D release một lần; U9; no stock, history giữ | E | Chưa chạy live |
| A07 | Pending5 | Reject(reason) | REJECTED, claim release/no stock, không resubmit | E | Chưa chạy live |
| A08 | F1 pending5/4 | Approve hai lần khác receipt | HOLD9, A9/W0, đúng tableF1/revisions | E | Chưa chạy live |
| A09 | F1 posted receipt | Duplicate approval samekey | Original result, no second stock/event/counter | E | Chưa chạy live |
| A10 | F1 PR1 HOLD5 | QC3accepted/1rejected | Parent1, child3/1, immutabledecision, totals3/1/1 | E | Chưa chạy live |
| A11 | PR1 pending1 | QC1accepted | MIXED4/1/0, parent0, child mới1 | E | Chưa chạy live |
| A12 | PR2 HOLD4 | QC4accepted | F1 physical9/eligible8/rejected1, pending0 | E | Chưa chạy live |
| A13 | F1 afterQC reject1 | Create thêm1 | U0, không nhận lại cùng Good; no auto rework | E | Chưa chạy live |
| A14 | NON_TRACKED fixture | Approve5+4/partialQC | No lotId fake, HOLD/REJECTED buckets isolated | E | Chưa chạy live |
| A15 | Tracked missing/invalid lot | Save/submit | Field lotNumber error, input giữ/no claim sai | E | Chưa chạy live |
| A16 | Lot duplicate trim/case | Save và race trước approve | Unique policy đúng, approve rollback full | E | Chưa chạy live |
| A17 | Child code collision | QC split | Không orphan child/decision/counter/movement | E | Chưa chạy live |
| A18 | Current WO warehouse đổi | Submit/approve/QC | Source conflict, không chuyển kho ngầm | E | Chưa chạy live |
| A19 | Inactive Item/UOM/kho | Approve/accept | Reference conflict, no partial effects | E | Chưa chạy live |
| A20 | Qty0/negative/scale7/overflow | Save/QC | Validation field/code, no rounding/float | E | Chưa chạy live |
| A21 | Exact6 F3 | Approve/QC | .000002=.000001+.000001, pending0 | E | Chưa chạy live |
| A22 | QC qty0 hoặc tổng>pending | QC | Conflict/validation đúng code, preserve input | E | Chưa chạy live |
| A23 | Reject>0/no reason | QC/reject/cancel | Field reason required, no write | E | Chưa chạy live |
| A24 | Same WO source, hai creates | Concurrent claim | Không tổng claims vượt9 | E + concurrency | Chưa chạy live |
| A25 | Hai approvals stale version | Concurrent approve | Một commit hoặc serialization đúng, no double stock | E + concurrency | Chưa chạy live |
| A26 | Hai QC chung pending | Concurrent QC | Không negative pending/duplicate accepted | E + concurrency | Chưa chạy live |
| A27 | Failure sau staged stock | Inject rollback trên test env | Receipt pending/claim cũ, no lot/movement/WO/result commit | E + DB kiểm | Chưa chạy live |
| A28 | Network timeout sau commit | Retry samekey | Original success, một bộ effects | E | Chưa chạy live |
| A29 | Samekey changedpayload | Retry khác quantity |409 IDEMPOTENCY_CONFLICT, no write | E | Chưa chạy live |
| A30 | Read-only/noQC/noCost profile | Direct action/read source | Action403, read tối thiểu/no money, no full-WO leakage | E | Chưa chạy live |
| A31 | Plant2/Company khác | Direct-ID/list/source/history | No record leakage, scope mismatch consistent | E | Chưa chạy live |
| A32 | Assignment revoked khi đợi lock | Mutate và replay | Current auth denied trước commit/replay | E + concurrency | Chưa chạy live |
| A33 | Expired output sau approval | Accept và reasoned reject | Accept blocked/no partial QC; reject-only theo policy chốt | E | Chưa chạy live |
| A34 | Date future/expiry boundary | Save/submit/approve | PlantToday/timezone server, đúng field và boundary | E | Chưa chạy live |
| A35 | GĐ8 final/retry sources5+3+1 | Receipt5+4/source read | Contribution mỗi nguồn≤Good, không dùng lại output | E | Chưa chạy live |
| A36 | Missing/tampered contribution | Submit/approve | Unknown/source conflict, không fake genealogy | E + DB kiểm | Chưa chạy live |
| A37 | Legacy nooutput evidence | Migration/read/newcreate | Preserve history/IDs, read-only/unknown, cap không nhận trùng | E + migration | Chưa chạy live |
| A38 | F1 MRP isolated | Before/approve/QC | Supply9→0, shortage1→10→2, snapshots giữ | E + planning output | Chưa chạy live |
| A39 | SO allocation + sourceWO | QCaccepted8 | QCmetric8 theo cap, delivered0/status không FULFILLED do QC | E + SO output | Chưa chạy live |
| A40 | Pending cost + stock guards | Approve/QC/generic status/issue | Unknown≠0, totalvalue không tăng doQC, HOLD/REJECTED bypass blocked, rawledger unchanged | E + ledger | Chưa chạy live |

Rollout đề xuất: (1) BE chốt §5/mapping/permissions/revisions/enums/error codes; (2) deploy GĐ8 canonical final Good/consumption và inventory quarantine/legacy reconcile; (3) receipt claim/lifecycle/atomic approve; (4) partialQC/stock/source/read projections; (5) MRP/SO metrics/capabilities; (6) OpenAPI+deployversion+fixture IDs; (7) FE adapter update và live A01–A40, không fallback mock. Filter/page/queue và source/history permission phải có trước demo multi-role. Existing whole-QC endpoint phải bị chặn/điều phối cho receipt dùng partial ledger, tránh ghi đè quyết định mới bằng API cũ.

**Trạng thái:** tài liệu sẵn để PO gửi; BE code/mapping/xác nhận pending, tích hợp/live acceptance pending. Không đóng BE task vì FE mock passed. Change log v1: bàn giao15screen areas/R01–R24/contract đề xuất/Q1–Q32/A01–A40; không sửa ứng dụng ở phiên tài liệu.

## §4 — Hợp đồng lỗi mong đợi

**[Đề xuất BE]:** success/error dùng envelope `{code,message,result?,errors?}`; code/message required; success read/command cần result; lỗi không result thành công; errors optional array `{field,message}`. NON_NULL có thể omit không áp dụng, không ép mọi response có `errors:[]`/`result:null`. HTTP status và code cùng có nghĩa, FE rẽ nhánh theo code. Error message thân thiện không chứa stack/cost/record ngoài scope. Response header X-Trace-Id thật luôn giữ để tra log, Authorization/token che.

| Tình huống | HTTP đề xuất | code mong đợi | Message/field FE | Input giữ | Retry |
|---|---|---|---|---|---|
| Invalid quantity/scale/overflow/date/text |400| VALIDATION_ERROR | Field wire quantity/acceptedQuantity/receivedOn/... | Có | Sửa input, intent/key mới |
| Tracking thiếu lot |400| LOT_REQUIRED | lotNumber bắt buộc | Có | Sửa field |
| NON_TRACKED gửi lot/expiry |400| LOT_NOT_ALLOWED | Bỏ lotNumber/expiresOn | Có | Refresh master/sửa |
| Lot unique conflict |409| LOT_CODE_ALREADY_EXISTS | lotNumber đã dùng, không tự đổi lot | Có | Chọn mã hợp lệ |
| Reuse lot nguồn khác |409| LOT_REUSE_NOT_ALLOWED | Không reuse parent posted khác | Có | Không retry mù |
| Lot không đúng Item |409| LOT_ITEM_MISMATCH | Kiểm nguồn Item/lô | Có | Refresh source |
| Lot kho khác |409| LOT_WAREHOUSE_CONFLICT | Kiểm nguồn kho/lô | Có | Refresh source |
| Approved thiếu/mismatch HOLD source |409| OUTPUT_LOT_NOT_POSTED | Chặn QC, báo integrity để tra trace | Có | BE đối soát trước |
| Lifecycle không hợp lệ |409| RECEIPT_STATE_CONFLICT | Phiếu đã đổi trạng thái | Có | Refetch |
| Qty vượt C−A−claims |409| PRODUCTION_OUTPUT_EXCEEDED | quantity; tải lại lượng còn lập | Có | Refetch và intent mới |
| G vượt planned khi policy chặn |409| PLANNED_QUANTITY_EXCEEDED | WO/output warning | Có | Manager xử lý source |
| Output legacy chưa có evidence |409| PRODUCTION_OUTPUT_UNKNOWN | Chỉ đọc, không suy lượng | Có | Chờ reconcile |
| Contribution/current source thay đổi |409| PRODUCTION_RECEIPT_SOURCE_CHANGED | Không tự đổi WO/Item/kho/nguồn | Có | Refetch, xử lý draft |
| Inactive/missing current master |409| REFERENCE_INACTIVE | Tên field/source blocker, không leak | Có | Fix master/refetch |
| Version mismatch |409| VERSION_CONFLICT | Phiếu/WO đã thay đổi | Có | Refetch, newkey cho intent mới |
| QC tổng vượt pending |409| PRODUCTION_QC_QUANTITY_EXCEEDED | acceptedQuantity/rejectedQuantity | Có | Refetch pending |
| QC lô/bucket inconsistent |409| PRODUCTION_QC_STATE_CONFLICT | Chặn decision, tra trace | Có | Không retry mù |
| Expired acceptance |409| LOT_EXPIRED | Không nhận đạt phần hết hạn | Có | Reject-only nếu policy cho |
| Samekey khác fingerprint |409| IDEMPOTENCY_CONFLICT | Không gửi lại key với payload mới | Có | Key mới chỉ cho intent mới |
| Thiếu quyền/current assignment |403| PERMISSION_DENIED | Không có quyền thao tác | Có; khóa action | Không auto retry |
| ID không có/khác scope |404| NOT_FOUND | Không tìm thấy trong phạm vi | Không tiết lộ record | Không auto retry |
| Current Plant/body/header lệch |403| PLANT_SCOPE_MISMATCH | Chọn lại Plant phù hợp | Có | Refetch context |
| Storage/transient fail |503| SERVICE_UNAVAILABLE | Chưa xác định kết quả; lưu trace/key | Có | Samekey/payload sau auth check |

Các lot-integrity codes và RECEIPT_STATE_CONFLICT/PLANNED_QUANTITY_EXCEEDED đã có xử lý trong source FE cũ; **không chứng minh chúng đang được server trả trong phiên này**. Mã PRODUCTION_* và mapping status mới cần Q28. Mock dùng short codes như OUTPUT_EXCEEDED/STALE/SOURCE_CHANGED, không gửi nguyên short code thành server contract tự động. 401/session/refresh tiếp tục contract Auth riêng; tài liệu này không thay nghĩa các mã hết phiên/token reuse.

## §5 — Cần BE xác nhận

**Các mục dưới đây KHÔNG cần code, chỉ cần trả lời.** Mỗi Q một quyết định. Mọi mục **KHÔNG CHẶN FE/mock code tiếp**; ghi riêng mức chặn integration GĐ9. **Owner:** BE lead + FE, policy PO tham gia khi cần; **quyết định/ngày:** chưa chốt cho Q1–Q32. PO đã duyệt flow demo không có nghĩa các DTO/path/policy BE dưới đây đã được chốt.

1. **Q1:** BE chấp nhận một receipt một WO/Item/output warehouse/lot tracking? — FE nghiêng về có, tránh ambiguous multi-line posting — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
2. **Q2:** Có cho partial receipt khi WO IN_PROGRESS có final Good known? — FE nghiêng về có, không bắt chờ WO COMPLETED — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
3. **Q3:** Cap source có chỉ lấy actual Good công đoạn cuối gồm retry Good? — FE nghiêng về có, không sum operations/planned/rework pending — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
4. **Q4:** BE trả riêng actualFinalGoodQuantity G và receiptableGoodCap min(P,G)? — FE nghiêng về có, tránh clamped cap bị hiểu là actual — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
5. **Q5:** DRAFT và PENDING có giữ output claim theo công thức §3.1? — FE nghiêng về có, chặn hai drafts dùng cùng Good — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
6. **Q6:** Contribution có do BE tự cấp theo time+ID ổn định và lưu source? — FE nghiêng về có, không tin client tạo source attribution — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
7. **Q7:** Claim nháp có giữ đến explicit edit/cancel/reject thay vì tự TTL? — FE nghiêng về có như demo, không biến mất quota bất ngờ — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
8. **Q8:** Uniqueness posted lot có theo Company+Item+trim(case-insensitive lotNumber)? — FE nghiêng về có, cùng tenant không reuse giữa Plant; mock không proof cross-Company DB — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
9. **Q9:** Pending/draft lot number có claim uniqueness cùng phạm vi posted lot? — FE nghiêng về có để báo sớm, approve vẫn unique dưới lock — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
10. **Q10:** Cho phép creator approve/QC own receipt khi có explicit permissions? — FE nghiêng về có theo PO/demo, không áp material-issue override ngầm — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
11. **Q11:** BE map hai read permissions mới như §1.3? — FE nghiêng về có để read-only không cần action hoặc full WO — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
12. **Q12:** ExpectedWorkOrderVersion có dùng một canonical WO revision tăng cả receipt/QC commands? — FE nghiêng về có như demo; nếu tách ledger revision cần DTO mapping duy nhất — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
13. **Q13:** Scope idempotency có tenant+Plant+principal+command/resource+key? — FE nghiêng về có, replay current auth trước trả result — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
14. **Q14:** Retention idempotency result có ít nhất24h? — FE nghiêng về có để retry timeout, cần TTL cụ thể server — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
15. **Q15:** Partial QC tracked có luôn tạo child kể cả all-accepted/all-rejected? — FE nghiêng về có, giữ identity/decision trace thay whole-lot overwrite — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
16. **Q16:** NON_TRACKED có dùng receipt-specific quarantine buckets không lotId giả? — FE nghiêng về có, accepted vào normal pool vẫn có movement source — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
17. **Q17:** QC rejected có giữ approved cap không reopen? — FE nghiêng về có, reject không chứng minh source Good mới — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
18. **Q18:** QC rejected có giữ OnHand cách ly thay vì tự writeoff? — FE nghiêng về có, disposal là workflow riêng — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
19. **Q19:** Quyết định QC có bất biến, không PATCH/DELETE trong GĐ9? — FE nghiêng về có, correction cần contract riêng — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
20. **Q20:** BE chấp nhận qualityState enum độc lập receipt status như §3.1? — FE nghiêng về có, PARTIAL/MIXED không ép single outputLotStatus — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
21. **Q21:** List hỗ trợ hasQcDecision filter để history gồm receipt kiểm dở? — FE nghiêng về có, pendingQc=false không đủ — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
22. **Q22:** Minimal receipt/QC source projection có độc lập full WO/cost read? — FE nghiêng về có, chỉ names/quantities/lineage theo scope — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
23. **Q23:** Expiry date có hiệu lực tới đầu ngày expiresOn theo Plant timezone? — FE nghiêng về có như demo; phải chốt boundary trước tích hợp — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
24. **Q24:** Expired HOLD có được reject-only có lý do? — FE nghiêng về có; accepted expired bị chặn — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
25. **Q25:** Reason trim5–2000, note≤2000 có là server policy thống nhất? — FE nghiêng về có, optional accepted-only reason vẫn bounded — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
26. **Q26:** Cho physical receipt/QC khi FG costing pending, value unknown thay0? — FE nghiêng về có để FE/BE làm theo giai đoạn; kế toán/cost close chặn ở GĐ11 — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
27. **Q27:** Genealogy tối thiểu có WORK_ORDER_LEVEL material attribution thay exact per-FG-unit? — FE nghiêng về có khi chưa có allocation authoritative — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
28. **Q28:** BE giữ lot-integrity codes cũ và map các PRODUCTION_* error đề xuất §4? — FE nghiêng về có để FE xử lý theo code, không HTTP-only — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
29. **Q29:** API extension có version/capability phân biệt partial ledger với whole-QC cũ? — FE nghiêng về có để endpoint cũ không overwrite decisions mới — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
30. **Q30:** Legacy thiếu source có read-only/unknown cho tới reconcile? — FE nghiêng về có, không reset hoặc backfill execution giả — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
31. **Q31:** SO có metric productionQcAccepted riêng delivered/fulfilled cũ? — FE nghiêng về có, QC không tự đánh dấu giao khách — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.
32. **Q32:** MRP có giảm scheduled WO supply bằng approved receipt và đếm eligible accepted qua inventory đúng một lần? — FE nghiêng về có theo fixture; earmark/effective coverage GĐ6 còn riêng — KHÔNG CHẶN FE code tiếp; CHẶN API GĐ9.

**Gate trước vẫn độc lập:** GĐ8 Q7 output warehouse MAKE DRAFT còn editable trong source GĐ8; GĐ9 chỉ khóa source receipt theo current WO, không chứng minh GĐ8 đã khóa. GĐ8 Q37 fractional returned-value residual, GĐ6 Q10/Q16/Q26/Q27/Q28 về metric/warehouse/rounding/coverage giữ task cũ; không trùng ngữ nghĩa Q số tương ứng của GĐ9. Không tự đóng các câu đó bằng phê duyệt UI GĐ9.

## §6 — FE đã tự xử lý

**[Đã kiểm chứng FE/mock]:**

- Canonical receipt/claims/contributions/QC trong persistence chung WO/inventory, legacy history retained; mutation exact6/current versions/lock/rollback/replay, không duplicate store. Đây là phòng vệ demo, không thay server DB multi-user isolation.
- Input/date/lot/quantity/cap checks; confirmations; owner/action guards; errors giữ input; same-key retry cho cùng intent; busy lock chống double submit; Plant/scope change clear context và invalidate shared data.
- Receipt/QC lists/filters/pagination20, right detail giữ context, centered action dialogs, source links theo permission. Đã sửa pagination mismatch bằng test25rows. VI/EN business labels; QC rejected khác receipt request rejected; timestamps theo locale/Plant zone.
- Minimal source quantity/name/history projections, cost action flag không lộ source money; stock guards ngăn generic change status/adjust bypass production QC; tracked children và nontracked source buckets đúng source type.
- WO/Execution/WIP receipt links/summaries; raw consumption/returns không trừ lại; MRP nhận approved counter từ canonical source; SO qcAccepted mới không auto FULFILLED. Source/raw trace được ghi rõ WO-level.
- Existing API receipt adapter giữ nguyên; uncontracted partial-QC/claims/source chưa gửi lên BE, API extension unavailable, không fallback mock hoặc dùng whole-QC giả partial.

**Cần đối chiếu khi BE trả contract:** rename mock fields→wire, UUID/revision semantics/capability/error codes, safe command result/refetch, same-key replay authorization, lot scope/uniqueness claims/child naming, expiry cutoff, optional reason bound, legacy reconciling, source permissions/page/totals, QC decision amounts vs current child quantity, availability/reservations/earmark, pending costing và cost redaction. Không biến thiếu giá/trace/consumed thành0/array rỗng giả.

FE hiện không chứng minh cross-Company lot uniqueness hoặc authoritative server lease/TTL, full-cost conservation sau partial receipt, residual costing GĐ8, delivery progress GĐ10. Full555/focused45/final25 và browser QA thuộc FE/mock trước; không có live acceptance mới. Chỉ gỡ guard khi có contract và server evidence tương đương. Không thay dependencies/API flags/app code ở phiên tài liệu này.

## §7 — Tham chiếu

Các file dưới đã chuẩn bị trước, **chưa xác nhận PO đã gửi/BE đã nhận**. Chỉ tên file và mục đích; toàn bộ yêu cầu GĐ9 để triển khai đã tự chứa trong tài liệu này, không cần mở repo/máy/màn hình FE.

- `BE_HANDOFF_GD_01_2026-10-09.md` — Auth/assignment/current scope, đối chiếu bản PO thực gửi.
- `FE_HANDOFF_GD_02_2026-10-12.md` — Item/UOM/Plant/Warehouse/active lifecycle và tracking.
- `FE_HANDOFF_GD_04_2026-10-12.md` — Inventory eligibility/quantity/value/lot/reservation foundations.
- `FE_HANDOFF_GD_06_2026-10-14.md` — Canonical WO/MAKE/SO allocation/MRP supply và các gate metric/warehouse/rounding.
- `FE_HANDOFF_GD_07_2026-10-14.md` — Purchase partial QC/quarantine, giữ provenance production riêng.
- `FE_HANDOFF_GD_08_2026-10-14.md` — Final Good/input/retry/consumption/WIP/return và Q7/Q37 chưa chốt.
- `FE_HANDOFF_GD_09_MESSAGE_2026-10-15.md` — Tin nhắn4dòng PO gửi kèm bản này.

### Checklist phát hành v1

- Đủ §0–§7, kết luận3dòng/P1 đúng phần mở rộng API, Ngoài phạm vi,15screen areas và ownership/mốc/ngày/version rõ.
- Không defect/live evidence: năm mảnh không có và latest deploy chưa kiểm nêu rõ; không fabricated request/response/trace/record. UUID và timestamps JSON chỉ fixture đề xuất, mock IDs riêng.
- R01–R24 kiểm được; field5cột/UUID/decimal/nullability/date/revisions; existing API source tách proposed extension, full `/api` path, body/result/page/permission/idempotency rõ.
- F1 5+4=9, QC8+1=9, W/U0, approved9 giữ nguyên; MRP shortage1→10→2 trong isolated fixture, không phủ earmark policy; value unknown không0.
- JSON/code block copy được; code/HTTP/errors/input/retry; §5 riêng không cần code/32 câu hỏi đóng/preference/blocking/owner-date pending. GĐ6/GĐ8 gates độc lập.
- A01–A40 live pending cần evidence cùngrun; mock/test/build không thành DB/server acceptance. §6 phòng vệ/giới hạn, §7 chỉ filename không repo/local path.
- Lưu local v1 và message4dòng; chưa gửi ngoài/commit/deploy/appchange; sau gửi sửa dùng_v2 cùng task; chỉ đóng BE task sau FE live acceptance.
