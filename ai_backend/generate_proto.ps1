$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$protoDir = Join-Path $repoRoot "src/main/proto"
$protoFiles = @(
    "terragis_service.proto",
    "terra_ai_service.proto"
)

foreach ($protoName in $protoFiles) {
    $protoPath = Join-Path $protoDir $protoName
    if (-not (Test-Path $protoPath)) {
        throw "Proto file not found: $protoPath"
    }

    python -m grpc_tools.protoc `
        -I"$protoDir" `
        --python_out="$scriptDir" `
        --grpc_python_out="$scriptDir" `
        "$protoPath"
}

Write-Host "Generated Python gRPC files in $scriptDir for: $($protoFiles -join ', ')"
