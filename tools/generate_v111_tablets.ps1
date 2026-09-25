Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$textureRoot = Join-Path $projectRoot "src/main/resources/assets/tristorage/textures/item"
$previewRoot = Join-Path $projectRoot "design"
$frameSize = 32
$frameCount = 32

New-Item -ItemType Directory -Force -Path $textureRoot | Out-Null
New-Item -ItemType Directory -Force -Path $previewRoot | Out-Null

function New-PixelCanvas([int]$width, [int]$height) {
    return New-Object System.Drawing.Bitmap $width, $height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Pixel($bitmap, [int]$x, [int]$y, [string]$hex) {
    $bitmap.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($hex))
}

function Fill-Rect($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$hex) {
    for ($py = $y; $py -lt ($y + $height); $py++) {
        for ($px = $x; $px -lt ($x + $width); $px++) {
            Set-Pixel $bitmap $px $py $hex
        }
    }
}

function Draw-Chassis($bitmap, [string]$edge, [string]$body, [string]$bevel, [string]$shadow, [string]$accent) {
    # The source is a logical 16x16 sprite. It is expanded exactly 2x with
    # nearest-neighbour sampling, so every visible GUI pixel remains regular.
    Fill-Rect $bitmap 4 3 8 10 $edge
    Fill-Rect $bitmap 5 2 6 12 $edge
    Fill-Rect $bitmap 5 3 6 10 $body
    Fill-Rect $bitmap 6 3 4 1 $bevel
    Fill-Rect $bitmap 6 12 4 1 $shadow
    Set-Pixel $bitmap 4 4 $bevel
    Set-Pixel $bitmap 11 4 $shadow
    Set-Pixel $bitmap 4 11 $shadow
    Set-Pixel $bitmap 11 11 $shadow
    Set-Pixel $bitmap 5 2 $accent
    Set-Pixel $bitmap 10 13 $accent
}

function New-RemoteFrame([int]$frame) {
    $b = New-PixelCanvas 16 16
    Draw-Chassis $b "#07101D" "#172A3E" "#49617A" "#080C16" "#D58A22"

    Fill-Rect $b 5 4 6 7 "#050D1B"
    Fill-Rect $b 6 5 4 5 "#09253B"
    Set-Pixel $b 5 4 "#56E6EF"
    Set-Pixel $b 10 10 "#7436A8"

    $scanY = 5 + [int][Math]::Floor(($frame * 5) / $frameCount)
    Fill-Rect $b 6 $scanY 4 1 "#1B7C96"
    Set-Pixel $b (6 + ($frame % 4)) $scanY "#C5FFFF"

    $signalPath = @(
        @(5,5), @(5,6), @(5,7), @(5,8), @(5,9),
        @(6,10), @(7,10), @(8,10), @(9,10),
        @(10,9), @(10,8), @(10,7), @(10,6), @(10,5),
        @(9,4), @(8,4), @(7,4), @(6,4)
    )
    $signal = $signalPath[$frame % $signalPath.Count]
    Set-Pixel $b $signal[0] $signal[1] "#63F7F1"

    Set-Pixel $b 6 11 "#35D8E2"
    Set-Pixel $b 7 11 ($(if (($frame % 8) -lt 4) { "#9CFDFF" } else { "#287F99" }))
    Set-Pixel $b 8 11 "#44306C"
    Set-Pixel $b 9 11 ($(if (($frame % 16) -lt 8) { "#B55BE4" } else { "#6A318F" }))
    return $b
}

function New-CraftingFrame([int]$frame) {
    $b = New-PixelCanvas 16 16
    Draw-Chassis $b "#05070D" "#171823" "#4A4C5A" "#05050A" "#7351A9"

    Fill-Rect $b 5 4 6 7 "#050710"
    Set-Pixel $b 5 4 "#55E6E5"
    Set-Pixel $b 10 4 "#7443B2"
    Set-Pixel $b 5 10 "#49306F"
    Set-Pixel $b 10 10 "#2A8997"

    $activeCell = [int][Math]::Floor($frame / 3) % 9
    $secondaryCell = ($activeCell + 8) % 9
    for ($cell = 0; $cell -lt 9; $cell++) {
        $x = 6 + (($cell % 3) * 2)
        $y = 5 + ([int][Math]::Floor($cell / 3) * 2)
        $color = if ($cell -eq $activeCell) {
            "#D8FFFF"
        } elseif ($cell -eq $secondaryCell) {
            "#C55AF0"
        } elseif ((($cell + $frame) % 4) -eq 0) {
            "#36C6D5"
        } else {
            "#343150"
        }
        Set-Pixel $b $x $y $color
    }

    $pulse = @("#5D3A91", "#7545AC", "#A653D4", "#D26CF3", "#A653D4", "#7545AC", "#5D3A91", "#3B285F")
    Set-Pixel $b 5 (5 + ($frame % 5)) $pulse[$frame % $pulse.Count]
    Set-Pixel $b 10 (9 - ($frame % 5)) "#42E1E5"
    Set-Pixel $b 6 11 "#69509A"
    Set-Pixel $b 7 11 "#31B9C8"
    Set-Pixel $b 8 11 ($(if (($frame % 8) -lt 4) { "#EFFCFF" } else { "#5B6674" }))
    Set-Pixel $b 9 11 "#A54ED2"
    return $b
}

function Expand-LogicalSprite($logical) {
    $expanded = New-PixelCanvas $frameSize $frameSize
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $color = $logical.GetPixel($x, $y)
            for ($dy = 0; $dy -lt 2; $dy++) {
                for ($dx = 0; $dx -lt 2; $dx++) {
                    $expanded.SetPixel(($x * 2) + $dx, ($y * 2) + $dy, $color)
                }
            }
        }
    }
    return $expanded
}

function Save-Animation([string]$name, [scriptblock]$factory) {
    $strip = New-PixelCanvas $frameSize ($frameSize * $frameCount)
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $logical = & $factory $frame
        $sprite = Expand-LogicalSprite $logical
        for ($y = 0; $y -lt $frameSize; $y++) {
            for ($x = 0; $x -lt $frameSize; $x++) {
                $strip.SetPixel($x, ($frame * $frameSize) + $y, $sprite.GetPixel($x, $y))
            }
        }
        $sprite.Dispose()
        $logical.Dispose()
    }
    $path = Join-Path $textureRoot ($name + ".png")
    $strip.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $strip.Dispose()
    return $path
}

$remotePath = Save-Animation "remote_tablet" { param($frame) New-RemoteFrame $frame }
$craftingPath = Save-Animation "wireless_crafting_terminal" { param($frame) New-CraftingFrame $frame }

# Produce a nearest-neighbour contact sheet for quick visual verification.
$preview = New-PixelCanvas 544 288
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml("#202634"))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$remoteStrip = [System.Drawing.Bitmap]::FromFile($remotePath)
$craftingStrip = [System.Drawing.Bitmap]::FromFile($craftingPath)
$sampleFrames = @(0, 8, 16, 24)
for ($i = 0; $i -lt $sampleFrames.Count; $i++) {
    $source = New-Object System.Drawing.Rectangle 0, ($sampleFrames[$i] * 32), 32, 32
    $remoteDest = New-Object System.Drawing.Rectangle (16 + ($i * 132)), 12, 120, 120
    $craftingDest = New-Object System.Drawing.Rectangle (16 + ($i * 132)), 156, 120, 120
    $graphics.DrawImage($remoteStrip, $remoteDest, $source, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawImage($craftingStrip, $craftingDest, $source, [System.Drawing.GraphicsUnit]::Pixel)
}
$graphics.Dispose()
$remoteStrip.Dispose()
$craftingStrip.Dispose()
$previewPath = Join-Path $previewRoot "tablet-preview-v1.11.png"
$preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()

Write-Output $remotePath
Write-Output $craftingPath
Write-Output $previewPath
