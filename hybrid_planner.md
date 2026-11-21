# Hybrid Planner – Requirements

1. Launch new screen from Files app from navigation drawer and as well as app must provide facility to long press on Files app icon to launch this new activity

Reference images: `hybrid_planner.png` (table rows), `hybrid_planner_enums.png` (enums).

## Supabase Table: `Hybrid` (schema `public`)

| Column | Type | Notes |
|---|---|---|
| `date_of_month` | date | Primary key (one row per calendar day), e.g. `2026-11-01` |
| `day_of_week` | enum `Days of The Week` | Derived from `date_of_month` |
| `planned_mode` | enum `PlannedHybridModes` | Planned work mode for the day |
| `H_O_W` | enum `HomeOfficeWeekend` | Short code matching `planned_mode` |
| `office_or_home_serial_number` | text | Nullable (NULL by default) |
| `status` | text | Nullable (NULL by default) |

## Enums (schema `public`)

| Enum | Values |
|---|---|
| `Days of The Week` | Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday |
| `PlannedHybridModes` | HOME, OFFICE, LEAVE, HOLIDAY, weekend, comp-off |
| `HomeOfficeWeekend` | H, O, w |

## Example Rows

| date_of_month | day_of_week | planned_mode | H_O_W | office_or_home_serial_number | status |
|---|---|---|---|---|---|
| 2026-11-01 | Sunday | weekend | w | NULL | NULL |
| 2026-11-02 | Monday | HOME | H | NULL | NULL |
| 2026-11-03 | Tuesday | OFFICE | O | NULL | NULL |

## Mapping Rules

- `HOME` → `H`
- `OFFICE` → `O`
- `weekend` → `w`
- `LEAVE`, `HOLIDAY`, `comp-off` → no code defined in `HomeOfficeWeekend` yet (`H_O_W` should be NULL, or enum extended – to be decided).
- Saturday/Sunday default to `weekend` / `w`.

## App Requirements

1. Fetch rows from the Supabase `Hybrid` table by month (`date_of_month`).
2. Display a planner/calendar showing `day_of_week`, `planned_mode`, and `H_O_W` per day.
3. Allow changing `planned_mode`; `H_O_W` is updated automatically per the mapping.
4. DON'T Allow editing `office_or_home_serial_number` . Here selected planned_mode will be displayed for its serial number count in that month (Increamenting value)
5. Kotlin enums must mirror the Supabase enums exactly (case-sensitive values, including `weekend`, `comp-off`, `w`).
6. Handle NULL values for `status`.

- `H_O_W` code for LEAVE, HOLIDAY will be "O"
- `H_O_W` code for comp-off will be empty for now.
- Rows for 3 month must be available in app, 1. for current month 2. for previous month 3. for next month
- Show date as dd-mm-yyyy
- daye of the week looks awesome
- instead of showing "OFFICE #1" just show "OFFICE" , "weekend" , "HOME" , "LEAVE" , "HOLIDAY"
- while displaying rightmost H/O/W , show it as serial number of count for selected planned mode as example H1 / O2 / O3
- for Saturdays and Sunday allow selection as weekend / comp-off. See planner_enums since we already have "comp-off" in enums

## Implementation as per observations
- HOLIDAY and LEAVE selection MUST INCREASE serial number counter for OFFICE only
- RULE is to consider OFFICE toal days for OFFICE/HOLIDAY/LEAVE selections