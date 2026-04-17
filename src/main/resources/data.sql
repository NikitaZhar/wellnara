INSERT INTO users (username, email, password, role)
SELECT 'admin', 'adm@gmail.com', '123', 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);