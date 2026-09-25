param(
    [string]$OutputDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'CryptoInvest\backups')
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$containerBackup = "/tmp/crypto-invest-$stamp.dump"
$restoreDatabase = "restore_verify_$stamp"
$restoreCreated = $false
$backupPath = Join-Path $OutputDirectory "crypto-invest-$stamp.dump"

function Invoke-ComposeChecked {
    param([string[]]$Arguments)
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose command failed ($LASTEXITCODE)" }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
try {
    $dumpCommand = 'pg_dump --format=custom --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --file=' + $containerBackup
    Invoke-ComposeChecked @('exec', '-T', 'postgres', 'sh', '-lc', $dumpCommand)
    Invoke-ComposeChecked @('cp', "postgres:$containerBackup", $backupPath)

    Invoke-ComposeChecked @('cp', $backupPath, "postgres:$containerBackup")
    $createCommand = 'createdb --username "$POSTGRES_USER" ' + $restoreDatabase
    Invoke-ComposeChecked @('exec', '-T', 'postgres', 'sh', '-lc', $createCommand)
    $restoreCreated = $true
    $restoreCommand = 'pg_restore --exit-on-error --username "$POSTGRES_USER" --dbname ' + $restoreDatabase + ' ' + $containerBackup
    Invoke-ComposeChecked @('exec', '-T', 'postgres', 'sh', '-lc', $restoreCommand)
    $checkCommand = 'psql --tuples-only --no-align --username "$POSTGRES_USER" --dbname ' + $restoreDatabase + ' --command "SELECT current_database()"'
    $restoredDatabase = (& docker compose exec -T postgres sh -lc $checkCommand | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $restoredDatabase -ne $restoreDatabase) { throw 'Restored database validation failed' }
    Write-Output "Backup created and isolated restore verified: $backupPath"
}
finally {
    if ($restoreCreated) {
        $dropCommand = 'dropdb --if-exists --username "$POSTGRES_USER" ' + $restoreDatabase
        & docker compose exec -T postgres sh -lc $dropCommand | Out-Null
    }
    & docker compose exec -T postgres rm -f $containerBackup 2>$null | Out-Null
}
