-- V46__create_shift_and_work_calendar.sql
-- C2-7 Part A+B+C: Shift + Work Calendar master data (BACKEND_CAPSTONE2_API_GAPS.md §3.5),
-- continuing the P4 cluster started by Work Center (C2-6).
--
-- Shift: one continuous interval per shift (start_time/end_time, TIME not TIMESTAMP — not tied to
-- a calendar date) plus breaks[] carved out of it. An overnight shift has end_time < start_time
-- (e.g. 22:00-06:00); see module/shift/CLAUDE.md for the day-attribution convention.
--
-- WorkCalendar: a weekly shift pattern (weekday -> shift, a weekday MAY carry more than one shift,
-- e.g. day shift + night shift both running on Monday) plus one-off NON_WORKING exceptions (no
-- "special working day" override — see NEXT_PHASE_PLAN.md C2-7 §1.2).
--
-- work_centers.work_calendar_id: nullable FK added here per C2-7 §1.3 — a work center already
-- existed before Work Calendar did, and not every work center needs one.

CREATE TABLE shifts (
    shift_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id    UUID          NOT NULL REFERENCES plants(plant_id),
    code        VARCHAR(100)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    start_time  TIME          NOT NULL,
    end_time    TIME          NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT        DEFAULT 0,
    CONSTRAINT uk_shifts_plant_code UNIQUE (plant_id, code),
    CONSTRAINT chk_shifts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_shifts_plant_id ON shifts(plant_id);
CREATE INDEX idx_shifts_status ON shifts(status);

CREATE TABLE shift_breaks (
    shift_break_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id       UUID          NOT NULL REFERENCES shifts(shift_id),
    start_time     TIME          NOT NULL,
    end_time       TIME          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT        DEFAULT 0
);

CREATE INDEX idx_shift_breaks_shift_id ON shift_breaks(shift_id);

CREATE TABLE work_calendars (
    work_calendar_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plant_id         UUID          NOT NULL REFERENCES plants(plant_id),
    code             VARCHAR(100)  NOT NULL,
    name             VARCHAR(255)  NOT NULL,
    effective_from   DATE          NOT NULL,
    effective_to     DATE,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_calendars_plant_code UNIQUE (plant_id, code),
    CONSTRAINT chk_work_calendars_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_work_calendars_plant_id ON work_calendars(plant_id);
CREATE INDEX idx_work_calendars_status ON work_calendars(status);

CREATE TABLE work_calendar_weekly_shifts (
    work_calendar_weekly_shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_calendar_id              UUID          NOT NULL REFERENCES work_calendars(work_calendar_id),
    weekday                       VARCHAR(10)   NOT NULL,
    shift_id                      UUID          NOT NULL REFERENCES shifts(shift_id),
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                    UUID,
    updated_by                    UUID,
    version                       BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_calendar_weekly_shifts UNIQUE (work_calendar_id, weekday, shift_id),
    CONSTRAINT chk_work_calendar_weekly_shifts_weekday CHECK (weekday IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

CREATE INDEX idx_work_calendar_weekly_shifts_calendar_id ON work_calendar_weekly_shifts(work_calendar_id);
CREATE INDEX idx_work_calendar_weekly_shifts_shift_id ON work_calendar_weekly_shifts(shift_id);

CREATE TABLE work_calendar_exceptions (
    work_calendar_exception_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_calendar_id           UUID          NOT NULL REFERENCES work_calendars(work_calendar_id),
    exception_date             DATE          NOT NULL,
    reason                     VARCHAR(255),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,
    version                    BIGINT        DEFAULT 0,
    CONSTRAINT uk_work_calendar_exceptions UNIQUE (work_calendar_id, exception_date)
);

CREATE INDEX idx_work_calendar_exceptions_calendar_id ON work_calendar_exceptions(work_calendar_id);

ALTER TABLE work_centers
    ADD COLUMN work_calendar_id UUID REFERENCES work_calendars(work_calendar_id);

CREATE INDEX idx_work_centers_work_calendar_id ON work_centers(work_calendar_id);
