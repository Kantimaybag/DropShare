DropShare reviewed files

Included:
- backend/src/main/resources/application.yml
  Corrected CORS YAML indentation and aligned default MySQL database/user/password with docker-compose.yml.
- docker-compose.yml
  Uses matching database credentials and adds a MySQL healthcheck.
- OtpService.java, DownloadService.java, ShareService.java
  Included as they exist in the uploaded ZIP for reference. They are NOT fully corrected for recipient-specific OTP sessions or one-time multi-file bundle downloads.

Important limitations:
1. Fixing recipient-specific OTP access requires coordinated changes beyond these three service files, including a recipient-session entity/repository, database migration, verification response DTO/controller, download controller, and frontend request handling. Do not treat the current share-wide OTP verification as recipient-isolated security.
2. Burn-after-read for a multi-file share needs a ZIP/bundle endpoint or a carefully designed per-file consumption model. Merely changing maxDownloads can allow repeated downloads of one file or block remaining files.
3. The multipart configuration allows one file up to 5 GB and a whole request up to 5200 MB. It does not allow 20 files of 5 GB in one request.

After copying application.yml and docker-compose.yml, make sure the running MySQL instance was initialized with the same credentials. Existing Docker volumes keep their original database users/passwords; changing compose environment values does not rewrite an already initialized database.
