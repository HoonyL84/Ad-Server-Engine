$ErrorActionPreference = "Stop"

$connectUrl = "http://localhost:8083/connectors"
$connectorPath = Join-Path $PSScriptRoot "ad-search-outbox-connector.json"

Write-Host "Registering Debezium connector from $connectorPath"

Invoke-RestMethod `
    -Method Post `
    -Uri $connectUrl `
    -ContentType "application/json" `
    -InFile $connectorPath

Write-Host "Registered. Check status at http://localhost:8083/connectors/ad-search-outbox-connector/status"
