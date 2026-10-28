[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArgs
)

$script = Join-Path $PSScriptRoot 'java\run.ps1'
if (Test-Path -LiteralPath $script) {
    if ($RemainingArgs) {
        & $script @RemainingArgs
    } else {
        & $script
    }
} else {
    throw "Runner script not found at $script"
}
