-- Store only a hash of the sender-only credential used to request a replacement OTP.
ALTER TABLE otp_verifications
    ADD COLUMN resend_secret_hash VARCHAR(64) NULL;
