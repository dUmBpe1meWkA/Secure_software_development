$ErrorActionPreference = 'Stop'
# Force a TCP connection and a password prompt, using the same restricted user as the API.
docker exec -it task4-postgres psql -h 127.0.0.1 -U parking_app -d parking -W
exit $LASTEXITCODE
