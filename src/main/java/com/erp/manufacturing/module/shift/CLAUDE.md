# module/shift — Business Rule Invariants

> Thêm ở `C2-7` (2026-08-05, track `C2-*` — `FRONTEND_ALIGNMENT_ROADMAP.md §8`). Chỉ nạp khi agent
> làm việc trong `module/shift/**` hoặc sửa `WorkCenter` liên quan tới `workCalendarId`. Nối tiếp
> `C2-6` (Work Center) trong cluster `P4` (Work Center/Shift/Calendar/Capacity).

Shift + Work Calendar master data (`BACKEND_CAPSTONE2_API_GAPS.md §3.5`). Bảng `shifts`,
`shift_breaks`, `work_calendars`, `work_calendar_weekly_shifts`, `work_calendar_exceptions`,
migration `V46` (schema, cùng migration đó cũng thêm `work_centers.work_calendar_id`) + `V47`
(seed permission).

| # | Bất biến | Test bảo vệ |
|---|---|---|
| B_sh1 | `Shift` là **một** interval liên tục (`startTime`/`endTime`, kiểu `TIME`) + `breaks[]` — **không** phải danh sách nhiều "work interval" con. `endTime < startTime` là ca **qua đêm hợp lệ**, không phải input sai | `ShiftServiceTest.create_normalizesCodeToUpperCase` (same-day), `.create_breakCrossingMidnightOfOvernightShift_isAccepted` (overnight vẫn tạo được) |
| B_sh2 | Mọi `ShiftBreak` phải nằm **trong** cửa sổ `[startTime, endTime)` của shift cha, tính **circular** (có qua đêm) qua `ShiftTimeWindow.containsInterval` — validate ở `ShiftService`, **trước khi** save (rule C9). 422, không phải 409: input sai, không phải state machine. Re-validate lại **toàn bộ** breaks hiện có mỗi khi `update` đổi `startTime`/`endTime`, kể cả khi request không gửi lại `breaks[]` | `ShiftServiceTest.create_breakOutsideShiftWindow_throws`, `.create_breakOutsideOvernightShiftWindow_throws`, `.update_shrinkingWindowWithoutResendingBreaks_reValidatesExistingBreaks` |
| B_cal1 | Mọi `WorkCalendarWeeklyShift` phải trỏ tới `Shift` **cùng plant** với `WorkCalendar` cha — validate ở `WorkCalendarService` (resolve toàn bộ `shiftId` trước, kiểm plant, **trước khi** build entity nào, rule C9). **Không** qua lookup service riêng (khác Work Center↔Routing) vì `Shift`/`WorkCalendar` cùng module | `WorkCalendarServiceTest.create_shiftOfDifferentPlant_throws` |
| B_cal2 | `WorkCalendarException` chỉ có **một chiều** `NON_WORKING` — không có cột "type". `UNIQUE (work_calendar_id, exception_date)`; trùng ngày **trong cùng một request** bị chặn ở service **trước khi** save (DB constraint chỉ bắt được nếu đã tồn tại từ trước, không bắt được 2 dòng trùng trong cùng batch insert cho tới flush) | `WorkCalendarServiceTest.create_duplicateExceptionDates_throws` |
| B_wc4 | `WorkCenter.workCalendar` phải cùng plant với `WorkCenter` — xem `module/workcenter/CLAUDE.md` (bất biến khai báo ở đó vì nó thuộc entity `WorkCenter`, validate ở `WorkCenterService` gọi `WorkCalendarLookupService`) | `WorkCenterServiceTest.create_workCalendarOfDifferentPlant_throws`, `.update_workCalendarOfDifferentPlant_throws` |

## Quyết định thiết kế cần nhớ

### 1. Quy ước qui-thuộc-ngày cho ca qua đêm — `C2-8` sẽ đọc lại đúng đoạn này

Một shift qua đêm (`endTime < startTime`, vd `22:00`-`06:00`) được gán cho weekday nó **bắt đầu**.
`WorkingWindowCalculator.computeDay(calendar, date)` trả về interval có thể **kết thúc ở ngày dương
lịch kế tiếp**: shift `22:00`-`06:00` gán cho thứ Hai ⇒ cửa sổ làm việc là
`[thứ Hai 22:00, thứ Ba 06:00)`, trừ đi break nằm trong khoảng đó. Đừng đảo ngược quy ước này khi
viết `C2-8` (Capacity Board) — nó phải cộng dồn giờ làm đúng theo ngày **bắt đầu ca**, không phải
ngày ca kết thúc.

### 2. `ShiftTimeWindow` — toán học circular dùng chung cho break validation VÀ net working window

`ShiftTimeWindow.durationMinutes(start, end)` luôn trả về số phút **không âm**, tự động "wrap" qua
nửa đêm khi `end` đứng trước `start` trong giờ đồng hồ. `containsInterval(outerStart, outerEnd,
innerStart, innerEnd)` dùng phép đo này để kiểm "interval trong có nằm trọn trong interval ngoài
không" mà **không cần if/else riêng cho trường hợp qua đêm** — cùng công thức áp dụng cho: (a) một
break có nằm trong shift không (`ShiftService`), và (b) shift đó chiếm bao nhiêu phút trong ngày
(`WorkingWindowCalculator`). Sửa công thức này ảnh hưởng **cả hai** nơi — đừng chỉ sửa một chỗ.

### 3. Không có endpoint public cho net working window (Part D)

`WorkCalendarLookupService.computeWorkingWindows(calendarId, from, to)` — quyết định `NEXT_PHASE_PLAN.md`
C2-7 §1.4 vẫn đúng: không có endpoint public, chỉ module khác gọi vào qua service này. ✅
**[`C2-8`] Đã có consumer thật** — `CapacityBoardService.buildDayContext` gọi `computeWorkingWindows`
+ `findNonWorkingExceptionDates` (mới); `WorkOrderService.scheduleOperations` (qua `release()`) gọi
`computeEndInstant` (mới, forward-scheduling — dùng `WorkingWindowCalculator.advance`, không phải
`computeWorkingWindows`, vì cần "khi nào đủ N phút" thay vì "cửa sổ làm việc trong khoảng ngày cho
trước"). Cả ba đều **đọc-only** (rule C7, không có `@PreAuthorize` — caller đã tự authorize trên
plant của mình). Chi tiết: `CLAUDE.md §0.30`.

### 4. `WorkCalendarRepository.findWithWeeklyShiftsByWorkCalendarId` cố ý KHÔNG fetch `exceptions`

`weeklyShifts` và `exceptions` đều là `List` (bag) trên `WorkCalendar`. Join-fetch cả hai trong cùng
một `@EntityGraph`/JPQL ném `MultipleBagFetchException` — đúng cái bẫy `C2-4` đã gặp giữa
`operations`/`componentLines` (`CLAUDE.md §0.27` hệ quả #1). `exceptions` được để lazy-load trong
cùng transaction thay vì fetch cùng lúc — đây là **một** aggregate detail, không phải report lặp
theo dòng, nên rule C14 không áp dụng. Đừng "gộp lại cho gọn" bằng cách thêm `exceptions` vào cùng
`@EntityGraph`.

### 5. `WorkCalendarUpdateRequest.effectiveTo` / `WorkCenterUpdateRequest.workCalendarId`: `null` = "không đổi", không có đường "xoá về trống"

Cả hai field đều nullable **có ý nghĩa thật** (không expiry / không gắn calendar) nhưng PATCH dùng
quy ước "`null` = giữ nguyên" như mọi field khác trong repo. Hệ quả chấp nhận: không có cách xoá một
`effectiveTo` đã set về "không hết hạn" hay gỡ một `workCalendarId` đã gán qua các endpoint này — cần
thì đó là mở rộng phase sau (thêm sentinel hoặc field riêng), không phải thiếu sót của `C2-7`.

### 6. `Shift`/`WorkCalendar` không dùng lookup service để validate lẫn nhau (khác Work Center↔Routing)

`WorkCalendarService.resolveShiftsInPlant` gọi thẳng `ShiftRepository.findAllById` — không có
`ShiftLookupService` riêng. Lý do: hai entity cùng module, không có ranh giới cross-module cần rule
C7 bảo vệ. `WorkCenter` (module khác) validate `workCalendarId` **qua** `WorkCalendarLookupService`
đúng theo rule C7, vì nó ở module `workcenter`.

### 7. Chưa làm — chờ phase sau

✅ **Capacity Board + schedule adjustment đã xong ở `C2-8`** (mục 3). `WorkCalendarException` kiểu
`WORKING_OVERRIDE` (làm bù) và `shiftIds[]` riêng trên `WorkCenter` bị loại có chủ đích (quyết định
§1.2/§1.3), không phải thiếu sót. Còn lại ngoài phạm vi module này: `predecessorOperationIds`/
sequence-dependency validation (`C2-8b`, xem `module/workcenter/CLAUDE.md` mục 7).
