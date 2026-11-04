-- Optional contact phone the user adds to their profile, in E.164 (+ and up to 15 digits)
ALTER TABLE users ADD COLUMN phone VARCHAR(16);
