UPDATE admin_codes
SET codigo = CONCAT('sha256$', SHA2(UPPER(TRIM(codigo)), 256))
WHERE codigo NOT LIKE 'sha256$%';
