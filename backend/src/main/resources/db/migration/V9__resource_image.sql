-- Ključ slike u skladištu (disk lokalno, S3-kompatibilno na produkciji). Sama slika nije u bazi,
-- jer bi bajtovi svake slike išli kroz svaki backup i svaki SELECT * nad resursima.
ALTER TABLE resources ADD COLUMN image_key VARCHAR(200);
