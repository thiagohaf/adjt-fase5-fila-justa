-- Adicionar usuario admin-tecnico para seed-adapter (Epic 5)
-- Senha: senha-tecnica-segura
-- Hash BCrypt (custo 10): $2b$10$aODKXXkXyIUwt89Qswt5yeIplnAFobXarkdRqkFENIu5zJsnlWqYG
INSERT INTO auth.usuarios (username, password_hash, role) VALUES
    ('admin-tecnico', '$2b$10$aODKXXkXyIUwt89Qswt5yeIplnAFobXarkdRqkFENIu5zJsnlWqYG', 'TECNICO')
ON CONFLICT (username) DO NOTHING;
