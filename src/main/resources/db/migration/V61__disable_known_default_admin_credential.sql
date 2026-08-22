-- Disable only the legacy seeded administrator that still has the publicly known password.
-- Operators must provision a unique administrator credential through the approved bootstrap flow.
UPDATE users
SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND password = '$2a$12$zUhsQTqnZs0KqpFHioEgpOXyJtVwLyWdwZsgaAl49LA5ocVTYwgqe';
