ALTER TABLE account_email_verification DROP CONSTRAINT account_email_verification_purpose_check;
ALTER TABLE account_email_verification ADD CONSTRAINT account_email_verification_purpose_check
    CHECK (purpose IN ('SIGNUP', 'EMAIL_CHANGE', 'PASSWORD_RESET'));
COMMENT ON COLUMN account_email_verification.purpose IS '??? ?? ??: ????, ??? ??, ???? ???';
