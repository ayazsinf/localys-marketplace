param(
  [string]$EnvFile = ".env",
  [string[]]$Set,
  [switch]$NoRun
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent
$envPath = Join-Path $root $EnvFile
$vars = [ordered]@{}

if (Test-Path $envPath) {
  Get-Content $envPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    $vars[$name] = $value
  }
}

if ($Set) {
  foreach ($item in $Set) {
    $idx = $item.IndexOf("=")
    if ($idx -lt 1) { continue }
    $name = $item.Substring(0, $idx).Trim()
    $value = $item.Substring($idx + 1)
    $vars[$name] = $value
  }
}

foreach ($k in $vars.Keys) {
  Set-Item -Path "Env:$k" -Value $vars[$k]
}

if ($vars.Count -gt 0) {
  Write-Host ("Loaded env vars: " + ($vars.Keys -join ", "))
}

if ($NoRun) { return }

Push-Location (Join-Path $root "backend")
try {
  mvn spring-boot:run
} finally {
  Pop-Location
}
