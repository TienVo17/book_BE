ALTER TABLE `the_loai`
    ADD COLUMN `slug` VARCHAR(255) NULL AFTER `ten_the_loai`;

UPDATE `the_loai`
SET `slug` = LOWER(TRIM(`ten_the_loai`))
WHERE `slug` IS NULL OR TRIM(`slug`) = '';

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'à', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'á', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ạ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ả', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ã', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ă', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ằ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ắ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ặ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẳ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẵ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'â', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ầ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ấ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ậ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẩ', 'a') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẫ', 'a') WHERE `slug` IS NOT NULL;

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'è', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'é', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẹ', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẻ', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ẽ', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ê', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ề', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ế', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ệ', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ể', 'e') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ễ', 'e') WHERE `slug` IS NOT NULL;

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ì', 'i') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'í', 'i') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ị', 'i') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỉ', 'i') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ĩ', 'i') WHERE `slug` IS NOT NULL;

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ò', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ó', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ọ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỏ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'õ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ô', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ồ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ố', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ộ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ổ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỗ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ơ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ờ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ớ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ợ', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ở', 'o') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỡ', 'o') WHERE `slug` IS NOT NULL;

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ù', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ú', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ụ', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ủ', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ũ', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ư', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ừ', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ứ', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ự', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ử', 'u') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ữ', 'u') WHERE `slug` IS NOT NULL;

UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỳ', 'y') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ý', 'y') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỵ', 'y') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỷ', 'y') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'ỹ', 'y') WHERE `slug` IS NOT NULL;
UPDATE `the_loai` SET `slug` = REPLACE(`slug`, 'đ', 'd') WHERE `slug` IS NOT NULL;

UPDATE `the_loai`
SET `slug` = REGEXP_REPLACE(`slug`, '[^a-z0-9]+', '-')
WHERE `slug` IS NOT NULL;

UPDATE `the_loai`
SET `slug` = TRIM(BOTH '-' FROM `slug`)
WHERE `slug` IS NOT NULL;

UPDATE `the_loai`
SET `slug` = CONCAT('the-loai-', `ma_the_loai`)
WHERE `slug` IS NULL OR TRIM(`slug`) = '';

UPDATE `the_loai` t
JOIN (
    SELECT `slug`
    FROM `the_loai`
    GROUP BY `slug`
    HAVING COUNT(*) > 1
) dup ON dup.`slug` = t.`slug`
SET t.`slug` = CONCAT(t.`slug`, '-', t.`ma_the_loai`);

ALTER TABLE `the_loai`
    MODIFY COLUMN `slug` VARCHAR(255) NOT NULL;

ALTER TABLE `the_loai`
    ADD UNIQUE KEY `uk_the_loai_slug` (`slug`);
