-- Older deployed schemas predate the optional debt/account association.
-- Existing debts remain unassigned; do not infer an account from balances.
ALTER TABLE debt ADD COLUMN IF NOT EXISTS account_id BIGINT REFERENCES wallet_account(id);
