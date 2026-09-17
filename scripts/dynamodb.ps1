<#
.SYNOPSIS
    Manage the DynamoDB Local container for the Task Management API.

.DESCRIPTION
    Starts, stops, restarts, or reports the status of the DynamoDB Local
    container defined in docker-compose.yml. The "status" action also tells
    you whether the container currently exists at all.

.PARAMETER Action
    The operation to perform: start, stop, status (default), or restart.

.EXAMPLE
    .\scripts\dynamodb.ps1 start
    .\scripts\dynamodb.ps1 stop
    .\scripts\dynamodb.ps1 status
    .\scripts\dynamodb.ps1 restart

.NOTES
    If you get an "execution policy" error, run PowerShell as administrator and:
        Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status', 'restart')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Continue'

# Resolve the project root (this script lives in <root>/scripts).
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot  = Split-Path -Parent $ScriptDir
$ComposeFile  = Join-Path $ProjectRoot 'docker-compose.yml'
$ContainerName = 'taskmgmt-dynamodb'

# Returns 'running', 'stopped', or 'missing' based on the container state.
function Get-ContainerState {
    # Merge stderr into stdout (2>&1) and capture exit code, so a missing
    # container reports cleanly instead of raising a NativeCommandError.
    $state = docker inspect -f '{{.State.Running}}' $ContainerName 2>&1
    if ($LASTEXITCODE -ne 0) {
        return 'missing'
    }
    if (($state | Out-String).Trim() -eq 'true') {
        return 'running'
    }
    return 'stopped'
}

function Start-DynamoDb {
    $state = Get-ContainerState

    if ($state -eq 'running') {
        Write-Host "DynamoDB Local is already running (container '$ContainerName')." -ForegroundColor Green
        return
    }

    if ($state -eq 'stopped') {
        Write-Host "Container '$ContainerName' exists but is stopped. Starting it..." -ForegroundColor Yellow
        docker start $ContainerName | Out-Null
    }
    else {
        Write-Host "Container '$ContainerName' does not exist. Creating it with docker compose..." -ForegroundColor Yellow
        docker compose -f $ComposeFile up -d | Out-Null
    }

    Write-Host "DynamoDB Local is running on http://localhost:8000" -ForegroundColor Green
}

function Stop-DynamoDb {
    $state = Get-ContainerState

    if ($state -eq 'missing') {
        Write-Host "Container '$ContainerName' does not exist. Nothing to stop." -ForegroundColor Yellow
        return
    }

    Write-Host "Stopping and removing '$ContainerName'..." -ForegroundColor Yellow
    docker compose -f $ComposeFile down | Out-Null

    # Fallback: if compose didn't remove it (e.g. it was created via `docker run`),
    # force-remove it directly.
    if ((Get-ContainerState) -ne 'missing') {
        docker rm -f $ContainerName | Out-Null
    }

    Write-Host "DynamoDB Local stopped and removed." -ForegroundColor Green
}

function Show-Status {
    $state = Get-ContainerState
    switch ($state) {
        'running' { Write-Host "Status: RUNNING  (container '$ContainerName', port 8000)" -ForegroundColor Green }
        'stopped' { Write-Host "Status: STOPPED  (container exists but is not running)" -ForegroundColor Yellow }
        'missing' { Write-Host "Status: MISSING  (container does not exist yet)" -ForegroundColor Red }
    }
}

switch ($Action) {
    'start'   { Start-DynamoDb }
    'stop'    { Stop-DynamoDb }
    'restart' { Stop-DynamoDb; Start-DynamoDb }
    'status'  { Show-Status }
}
