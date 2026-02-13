# Find connected Android phone
Write-Host "Searching for connected devices..."
Write-Host ""

# Method 1: Check Shell namespace for portable devices
Write-Host "=== Method 1: Shell Namespace ==="
try {
    $shell = New-Object -ComObject Shell.Application
    $computer = $shell.NameSpace(17)
    $items = $computer.Items()
    
    foreach ($item in $items) {
        if ($item.Type -like "*Phone*" -or $item.Type -like "*Portable*" -or $item.Type -like "*Device*" -or $item.Type -like "*MTP*") {
            Write-Host "Found: $($item.Name)"
            Write-Host "Type: $($item.Type)"
            Write-Host "Path: $($item.Path)"
            Write-Host ""
        }
    }
} catch {
    Write-Host "Error in Method 1: $_"
}

# Method 2: Check PnP Devices
Write-Host "=== Method 2: PnP Devices ==="
try {
    $devices = Get-PnpDevice | Where-Object { 
        $_.FriendlyName -like "*Android*" -or 
        $_.FriendlyName -like "*Phone*" -or 
        $_.FriendlyName -like "*MTP*" -or
        $_.FriendlyName -like "*ADB*" -or
        $_.Class -eq "WPD"
    }
    
    foreach ($device in $devices) {
        Write-Host "Device: $($device.FriendlyName)"
        Write-Host "Class: $($device.Class)"
        Write-Host "Status: $($device.Status)"
        Write-Host ""
    }
} catch {
    Write-Host "Error in Method 2: $_"
}

# Method 3: List all drives
Write-Host "=== Method 3: All Logical Disks ==="
try {
    Get-WmiObject -Class Win32_LogicalDisk | ForEach-Object {
        Write-Host "$($_.DeviceID) - $($_.VolumeName) - DriveType: $($_.DriveType)"
    }
} catch {
    Write-Host "Error in Method 3: $_"
}

Write-Host ""
Write-Host "=== Done ==="
