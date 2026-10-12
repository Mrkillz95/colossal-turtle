# Installs the newest Colossal Turtle jar from release\ into Minecraft.
# Run it from anywhere: powershell -ExecutionPolicy Bypass -File $HOME\colossal-turtle\install.ps1
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$mc = Join-Path $env:APPDATA '.minecraft'
$targets = @((Join-Path $mc 'mods'))

git -C $repo pull --ff-only | Out-Host

$jar = Get-ChildItem (Join-Path $repo 'release') -Filter 'colossal-turtle-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.BaseName -replace '^colossal-turtle-', '') } | Select-Object -Last 1
if (-not $jar) { Write-Host 'No Colossal Turtle jar in release\ yet.'; exit 1 }

foreach ($t in $targets) {
    if (-not (Test-Path $t)) { continue }
    # Only one Colossal Turtle jar can be in a mods folder at a time.
    Get-ChildItem $t -Filter 'colossal-turtle-*.jar' | Where-Object Name -ne $jar.Name | Remove-Item -Confirm:$false
    Copy-Item $jar.FullName $t -Force
    Write-Host "Installed $($jar.Name) in $t"
}
Write-Host 'Done. Open the launcher, pick fabric-loader-0.19.5-1.21.1 and press Play.'
