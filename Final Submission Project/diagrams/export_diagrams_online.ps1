# Export PlantUML diagrams using online PlantUML server
# Usage: .\export_diagrams_online.ps1

$diagramsFolder = $PSScriptRoot
$exportsFolder = Join-Path $diagramsFolder "exports"

# Ensure exports folder exists
if (-not (Test-Path $exportsFolder)) {
    New-Item -ItemType Directory -Path $exportsFolder | Out-Null
}

function Export-PlantUMLOnline {
    param(
        [string]$InputFile,
        [string]$OutputFile
    )
    
    Write-Host "Exporting $InputFile..." -ForegroundColor Cyan
    
    # Read the PlantUML file
    $pumlContent = Get-Content $InputFile -Raw
    
    # Encode for PlantUML server
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($pumlContent)
    $compressed = [System.IO.Compression.DeflateStream]::new(
        [System.IO.MemoryStream]::new(), 
        [System.IO.Compression.CompressionMode]::Compress
    )
    
    # Alternative: Use kroki.io (supports PlantUML)
    $encoded = [Convert]::ToBase64String($bytes)
    $url = "https://kroki.io/plantuml/png/$encoded"
    
    try {
        Invoke-WebRequest -Uri $url -OutFile $OutputFile
        Write-Host "  ✓ Exported to $OutputFile" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ Failed to export: $_" -ForegroundColor Red
    }
}

# Export all diagrams
Write-Host "`n=== Exporting PlantUML Diagrams ===" -ForegroundColor Yellow
Write-Host "Using Kroki online service (https://kroki.io)`n" -ForegroundColor Gray

$diagrams = @(
    @{Input="component_diagram.puml"; Output="component_diagram.png"},
    @{Input="sequence_playback.puml"; Output="sequence_playback.png"},
    @{Input="ui_flow.puml"; Output="ui_flow.png"},
    @{Input="use_case_diagram.puml"; Output="use_case_diagram.png"}
)

foreach ($diagram in $diagrams) {
    $inputPath = Join-Path $diagramsFolder $diagram.Input
    $outputPath = Join-Path $exportsFolder $diagram.Output
    
    if (Test-Path $inputPath) {
        Export-PlantUMLOnline -InputFile $inputPath -OutputFile $outputPath
    } else {
        Write-Host "  ⊘ Skipping $($diagram.Input) (not found)" -ForegroundColor Gray
    }
}

Write-Host "`n=== Export Complete ===" -ForegroundColor Green
Write-Host "Diagrams saved to: $exportsFolder`n" -ForegroundColor Cyan

# List exported files
Get-ChildItem $exportsFolder\*.png | Select-Object Name, @{Name="Size(KB)";Expression={[math]::Round($_.Length/1KB,1)}}, LastWriteTime
