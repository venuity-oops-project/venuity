[CmdletBinding()]
param(
    [switch]$ForceRebuild,
    [switch]$SkipBuild,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

$script = Join-Path $PSScriptRoot 'java\run.ps1'
if (Test-Path -LiteralPath $script) {
    $params = @{}
    if ($ForceRebuild) { $params['ForceRebuild'] = $true }
    if ($SkipBuild) { $params['SkipBuild'] = $true }
    if ($RemainingArgs) {
        & $script @params @RemainingArgs
    } else {
        & $script @params
    }
} else {
    throw "Runner script not found at $script"
}
