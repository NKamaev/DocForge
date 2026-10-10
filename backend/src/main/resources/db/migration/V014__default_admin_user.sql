INSERT INTO users (id, login, password_hash, display_name, email, is_admin, created_at, updated_at) VALUES
(
    gen_random_uuid(),
    'admin',
    '$2a$10$MPSa06iTtJ3ak0DlpjdQ4OMWhhrxXBUzw6PCu2lcugCxjCl9w7eR6',
    'Администратор',
    'admin@localhost',
    TRUE,
    now(),
    now()
);
