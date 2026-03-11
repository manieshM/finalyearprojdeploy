PostgreSQL enhancement setup

1. Install PostgreSQL locally.
2. Create a database, for example `missing_person_ai`.
3. Run the schema from [postgresql-schema.sql](D:\project2\database\postgresql-schema.sql).
4. Download the PostgreSQL JDBC driver jar and place it in `backend/lib/`.
5. Set environment variables before running the app:

```powershell
$env:APP_DB_URL="jdbc:postgresql://localhost:5432/missing_person_ai"
$env:APP_DB_USER="postgres"
$env:APP_DB_PASSWORD="your_password"
```

6. Start the app with `run.bat`.

If `APP_DB_URL` is not set, the app falls back to the existing TSV storage automatically.
