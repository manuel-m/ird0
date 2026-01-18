
# --- CONF ---
$source = "."
$target = "\\wsl.localhost\Ubuntu\tmp\makeit"
$excludeDirs = @(".git", ".github", ".idea", ".vscode", "doc", "target")
$logFile = "$env:USERPROFILE\sync-to-wsl.log"

# --- SYNC ---
Write-Host "Synchronisation de $source → $target ..."

# Construire le tableau d'arguments séparés
$excludeParams = $excludeDirs | ForEach-Object { "/XD", $_ }

# Exécuter robocopy
robocopy $source $target /MIR /MT:8 /R:2 /W:2 $excludeParams /LOG:$logFile /NFL /NDL /NP /NJH /NJS

Write-Host "Sync done. Logs disponibles dans $logFile"
