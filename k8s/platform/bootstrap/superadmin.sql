-- Same ids as Keycloak's realm file (the token's sub and branch_id). ON CONFLICT: safe to run again.
INSERT INTO branch (id, name, code, active, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-00000000b001', 'Merkez Şube', 'MRKZ', TRUE, now(), now(), 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, branch_id, role, first_name, last_name, email, phone, username, active,
                   created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-00000000b001', 'ADMIN',
        'Sistem', 'Yöneticisi', 'superadmin@onlinecourses.site', NULL, 'superadmin@onlinecourses.site',
        TRUE, now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
