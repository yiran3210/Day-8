SELECT name
FROM sqlite_master
WHERE type = 'trigger'
  AND name LIKE 'trg_completion_%'
ORDER BY name;