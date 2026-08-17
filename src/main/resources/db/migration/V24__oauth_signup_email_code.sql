-- Ma xac minh email cho luot dang ky bang provider.
--
-- Provider khong phai luc nao cung chung minh duoc dia chi email. Facebook thi khong bao gio:
-- Graph tra ve dia chi nhung khong noi duoc no da duoc xac minh hay chua, nen ung dung coi la
-- chua xac minh. Google co `email_verified` nhung van co the vang.
--
-- Khi khong co bang chung tu provider, ung dung phai tu gui ma. Ma chi luu duoi dang bam, cung
-- kieu voi `state` va `browser binding` cua `oauth_transaction`: mot lan lo database chi doc
-- khong duoc phep du de hoan tat dang ky thay nguoi khac.
--
-- `email_code_attempts` chan do ma: ma ngan de nguoi dung go duoc, nen thieu bo dem thi doan
-- het khong gian ma chi la van de thoi gian.
ALTER TABLE `oauth_signup_intent`
    ADD COLUMN `email_code_hash` VARCHAR(64) NULL AFTER `email_verified`,
    ADD COLUMN `email_code_expires_at` DATETIME(6) NULL AFTER `email_code_hash`,
    ADD COLUMN `email_code_attempts` INT NOT NULL DEFAULT 0 AFTER `email_code_expires_at`;
