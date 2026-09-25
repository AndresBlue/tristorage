Add-Type -AssemblyName System.Drawing

$textureRoot = Join-Path $PSScriptRoot "..\src\main\resources\assets\tristorage\textures\block"
$textureRoot = [System.IO.Path]::GetFullPath($textureRoot)

function Convert-Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function New-Canvas([string]$background) {
    $bitmap = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $bitmap.SetPixel($x, $y, (Convert-Color $background))
        }
    }
    return $bitmap
}

function Set-Pixel($bitmap, [int]$x, [int]$y, [string]$color) {
    $bitmap.SetPixel($x, $y, (Convert-Color $color))
}

function Fill-Rect($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$color) {
    for ($py = $y; $py -lt ($y + $height); $py++) {
        for ($px = $x; $px -lt ($x + $width); $px++) {
            Set-Pixel $bitmap $px $py $color
        }
    }
}

function Save-Texture($bitmap, [string]$name) {
    $path = Join-Path $textureRoot $name
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Add-Chassis($bitmap, [string]$base, [string]$frame, [string]$edge, [string]$circuit) {
    Fill-Rect $bitmap 0 0 16 16 $base
    Fill-Rect $bitmap 0 0 16 1 $frame
    Fill-Rect $bitmap 0 15 16 1 $frame
    Fill-Rect $bitmap 0 0 1 16 $frame
    Fill-Rect $bitmap 15 0 1 16 $frame

    # Corner armour and recessed rails.
    foreach ($point in @(@(1,1),@(2,1),@(1,2),@(13,1),@(14,1),@(14,2),@(1,13),@(1,14),@(2,14),@(14,13),@(13,14),@(14,14))) {
        Set-Pixel $bitmap $point[0] $point[1] $edge
    }
    Fill-Rect $bitmap 5 1 6 1 $edge
    Fill-Rect $bitmap 5 14 6 1 $edge
    Fill-Rect $bitmap 1 5 1 6 $edge
    Fill-Rect $bitmap 14 5 1 6 $edge

    # Shared orange bus contacts keep every block in the same visual family.
    Fill-Rect $bitmap 6 1 4 1 $circuit
    Fill-Rect $bitmap 6 14 4 1 $circuit
    Fill-Rect $bitmap 1 6 1 4 $circuit
    Fill-Rect $bitmap 14 6 1 4 $circuit
}

function New-CoreTop([string]$name, [string]$bright, [string]$mid, [string]$deep) {
    $bitmap = New-Canvas "#111820"
    Add-Chassis $bitmap "#111820" "#20272D" "#3C454D" "#D99B2B"

    # Cross-shaped containment ring.
    Fill-Rect $bitmap 5 3 6 10 $deep
    Fill-Rect $bitmap 3 5 10 6 $deep
    Fill-Rect $bitmap 5 4 6 8 $mid
    Fill-Rect $bitmap 4 5 8 6 $mid

    # Recessed inner vault and luminous core.
    Fill-Rect $bitmap 6 6 4 4 "#111820"
    Fill-Rect $bitmap 6 6 4 1 $bright
    Fill-Rect $bitmap 6 9 4 1 $bright
    Fill-Rect $bitmap 6 6 1 4 $bright
    Fill-Rect $bitmap 9 6 1 4 $bright
    Fill-Rect $bitmap 7 7 2 2 $bright

    # Small tier-energy highlights.
    Set-Pixel $bitmap 5 5 $bright
    Set-Pixel $bitmap 10 5 $bright
    Set-Pixel $bitmap 5 10 $bright
    Set-Pixel $bitmap 10 10 $bright
    Save-Texture $bitmap $name
}

function Recolor-IronCoreSide {
    $path = Join-Path $textureRoot "iron_storage_core.png"
    $bitmap = New-Object System.Drawing.Bitmap $path
    $replacement = @{
        "#76E5EE" = "#ECF7F6"
        "#217985" = "#91A9AE"
    }

    for ($y = 0; $y -lt $bitmap.Height; $y++) {
        for ($x = 0; $x -lt $bitmap.Width; $x++) {
            $pixel = $bitmap.GetPixel($x, $y)
            $key = "#{0:X2}{1:X2}{2:X2}" -f $pixel.R, $pixel.G, $pixel.B
            if ($replacement.ContainsKey($key)) {
                $bitmap.SetPixel($x, $y, (Convert-Color $replacement[$key]))
            }
        }
    }

    $temporaryPath = Join-Path $textureRoot "iron_storage_core.tmp.png"
    $bitmap.Save($temporaryPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Move-Item -Force $temporaryPath $path
}

function New-LinkerTop {
    $bitmap = New-Canvas "#11151E"
    Add-Chassis $bitmap "#11151E" "#252C38" "#493261" "#F0AD31"

    # Cyan target brackets.
    Fill-Rect $bitmap 3 3 3 1 "#6DEAF0"
    Fill-Rect $bitmap 3 3 1 3 "#6DEAF0"
    Fill-Rect $bitmap 10 3 3 1 "#6DEAF0"
    Fill-Rect $bitmap 12 3 1 3 "#6DEAF0"
    Fill-Rect $bitmap 3 12 3 1 "#6DEAF0"
    Fill-Rect $bitmap 3 10 1 3 "#6DEAF0"
    Fill-Rect $bitmap 10 12 3 1 "#6DEAF0"
    Fill-Rect $bitmap 12 10 1 3 "#6DEAF0"

    # Wireless pulses on each axis.
    Fill-Rect $bitmap 6 3 4 1 "#C675F0"
    Fill-Rect $bitmap 6 12 4 1 "#C675F0"
    Fill-Rect $bitmap 3 6 1 4 "#C675F0"
    Fill-Rect $bitmap 12 6 1 4 "#C675F0"
    foreach ($point in @(@(7,5),@(8,5),@(7,10),@(8,10),@(5,7),@(5,8),@(10,7),@(10,8))) {
        Set-Pixel $bitmap $point[0] $point[1] "#6D299A"
    }

    # Central link node.
    Fill-Rect $bitmap 6 6 4 4 "#493261"
    Fill-Rect $bitmap 7 7 2 2 "#6DEAF0"
    Set-Pixel $bitmap 7 7 "#D9FFFF"
    Save-Texture $bitmap "storage_linker_top.png"
}

function New-CraftingTerminalTop {
    $bitmap = New-Canvas "#11181C"
    Add-Chassis $bitmap "#11181C" "#414A55" "#137A85" "#E3A52E"

    # A luminous 3x3 crafting matrix embedded in the terminal chassis.
    Fill-Rect $bitmap 3 3 10 10 "#0B3037"
    Fill-Rect $bitmap 4 4 8 8 "#101E25"
    foreach ($y in @(4,7,10)) {
        foreach ($x in @(4,7,10)) {
            Fill-Rect $bitmap $x $y 2 2 "#137A85"
            Set-Pixel $bitmap $x $y "#80FFFF"
            Set-Pixel $bitmap ($x + 1) ($y + 1) "#38C8CE"
        }
    }
    Set-Pixel $bitmap 7 7 "#FFE08A"
    Set-Pixel $bitmap 8 8 "#E3A52E"
    Save-Texture $bitmap "crafting_terminal_top.png"
}

function New-TexturePreview {
    $previewPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\design\texture-preview-v1.5.png"))
    $tileSize = 128
    $preview = New-Object System.Drawing.Bitmap ($tileSize * 4), ($tileSize * 2), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($preview)
    $graphics.Clear((Convert-Color "#080B10"))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

    $textures = @(
        "iron_storage_core_top.png", "diamond_storage_core_top.png", "blaze_storage_core_top.png", "cosmic_storage_core_top.png",
        "iron_storage_core.png", "diamond_storage_core.png", "storage_linker_top.png", "crafting_terminal_top.png"
    )

    for ($index = 0; $index -lt $textures.Count; $index++) {
        $source = New-Object System.Drawing.Bitmap (Join-Path $textureRoot $textures[$index])
        $x = ($index % 4) * $tileSize
        $y = [Math]::Floor($index / 4) * $tileSize
        $graphics.DrawImage($source, $x, $y, $tileSize, $tileSize)
        $source.Dispose()
    }

    $graphics.Dispose()
    $preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $preview.Dispose()
}

Recolor-IronCoreSide
New-CoreTop "iron_storage_core_top.png" "#F7FFFF" "#D5E7E8" "#82949A"
New-CoreTop "diamond_storage_core_top.png" "#8AFFF4" "#35C9D2" "#126C78"
New-CoreTop "blaze_storage_core_top.png" "#FFE26B" "#FF8A22" "#A33320"
New-CoreTop "cosmic_storage_core_top.png" "#E5A5FF" "#B14CE5" "#5A2784"
New-LinkerTop
New-CraftingTerminalTop
New-TexturePreview
