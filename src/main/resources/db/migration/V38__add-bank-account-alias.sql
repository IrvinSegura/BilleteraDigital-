-- A name the owner gives a bank account ("Nómina", "Ahorro") to tell them apart; it never reaches the bank.
ALTER TABLE bank_accounts ADD COLUMN alias VARCHAR(40);
