-- Local development only (loaded by the "local" profile). The memberships of the three demo
-- students from local/dev-students.json (the same ids exam-analysis seeds), so attendance can be
-- tried before the user service's events reach this service.

INSERT INTO membership_replica (id, classroom_id, student_id, branch_id, start_date, end_date,
                                student_number, full_name, created_at, updated_at, version)
VALUES ('60000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222',
        '50000000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-00000000b001',
        DATE '2026-09-14', NULL, 'ST7001', 'Ali Yılmaz', now(), now(), 0),
       ('60000000-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222',
        '50000000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-00000000b001',
        DATE '2026-09-14', NULL, 'ST7002', 'Ayşe Kaya', now(), now(), 0),
       ('60000000-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222',
        '50000000-0000-4000-8000-000000000003', '00000000-0000-0000-0000-00000000b001',
        DATE '2026-09-14', NULL, 'ST7003', 'Can Demir', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
