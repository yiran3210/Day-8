SELECT
    id,
    local_date,
    datetime(observed_at_ms / 1000, 'unixepoch', 'localtime')
        AS observed_time,
    planned_minutes,
    completed_minutes,
    ROUND(
        100.0 * completed_minutes
        / NULLIF(planned_minutes, 0), 1
    ) AS completion_percent
FROM completion_observations
ORDER BY id DESC
LIMIT 15;