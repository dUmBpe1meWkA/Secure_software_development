INSERT INTO app_users VALUES (1, 'Иван', 'ivan@example.com'), (2, 'Анна', 'anna@example.com');
INSERT INTO vehicles VALUES (1, 'А123АА77', 1), (2, 'В456ВВ77', 2);
INSERT INTO parking_zones VALUES (1, 'Центр', 2, 120), (2, 'Вокзал', 1, 80);
INSERT INTO parking_passes VALUES (1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days', 'ACTIVE');
INSERT INTO parking_sessions(id, vehicle_id, zone_id, entry_time, exit_time, amount, hourly_price)
VALUES (1, 2, 1, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour', 120, 120);
