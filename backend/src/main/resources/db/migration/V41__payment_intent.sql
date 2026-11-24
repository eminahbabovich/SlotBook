-- Stripe vraća novac po payment intentu, ne po Checkout sesiji. Stiže tek s uplatom (webhook),
-- pa je prazan dok je uplata otvorena ili istekla, a plaćena i vraćena ga moraju imati.
ALTER TABLE payments ADD COLUMN stripe_payment_intent_id VARCHAR(255);
ALTER TABLE payments ADD CONSTRAINT payments_intent_uz_novac
    CHECK (status IN ('OPEN', 'EXPIRED') OR stripe_payment_intent_id IS NOT NULL);
