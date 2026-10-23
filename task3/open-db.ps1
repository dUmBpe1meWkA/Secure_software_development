$ErrorActionPreference = 'Stop'
docker exec -it task3-postgres psql -h 127.0.0.1 -U parking_app -d parking -W
exit $LASTEXITCODE
