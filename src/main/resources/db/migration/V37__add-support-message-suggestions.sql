-- The questions the bot offers when it understood only part of a message, one per line
ALTER TABLE support_messages ADD COLUMN suggestions TEXT;
