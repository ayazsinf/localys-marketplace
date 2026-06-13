ALTER TABLE products
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2);

UPDATE products
SET country_code = CASE UPPER(COALESCE(currency, 'EUR'))
    WHEN 'TRY' THEN 'TR'
    WHEN 'USD' THEN 'US'
    WHEN 'GBP' THEN 'GB'
    WHEN 'CAD' THEN 'CA'
    WHEN 'CHF' THEN 'CH'
    ELSE 'FR'
END
WHERE country_code IS NULL OR TRIM(country_code) = '';

ALTER TABLE products
    ALTER COLUMN country_code SET NOT NULL;

ALTER TABLE products
    ALTER COLUMN country_code SET DEFAULT 'FR';
