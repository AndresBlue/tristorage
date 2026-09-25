Add-Type -AssemblyName System.Drawing

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$blockRoot = Join-Path $projectRoot "src\main\resources\assets\tristorage\textures\block"
$itemRoot = Join-Path $projectRoot "src\main\resources\assets\tristorage\textures\item"
$previewPath = Join-Path $projectRoot "design\texture-preview-v1.9.png"
$animationPreviewPath = Join-Path $projectRoot "design\animation-preview-v1.9-hotfix.png"
$frameCount = 12

function Color([string]$hex) { return [System.Drawing.ColorTranslator]::FromHtml($hex) }

function Canvas([string]$background = "#00000000") {
    $bitmap = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $fill = Color $background
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bitmap.SetPixel($x, $y, $fill) } }
    return $bitmap
}

function Pixel($bitmap, [int]$x, [int]$y, [string]$color) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $bitmap.SetPixel($x, $y, (Color $color)) }
}

function Rect($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$color) {
    for ($py = $y; $py -lt ($y + $height); $py++) { for ($px = $x; $px -lt ($x + $width); $px++) { Pixel $bitmap $px $py $color } }
}

function Pixel-Alpha($bitmap, [int]$x, [int]$y, [int]$alpha, [string]$color) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
        $rgb = Color $color
        $bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, $rgb.R, $rgb.G, $rgb.B))
    }
}

function Rect-Alpha($bitmap, [int]$x, [int]$y, [int]$width, [int]$height,
                    [int]$alpha, [string]$color) {
    for ($py = $y; $py -lt ($y + $height); $py++) {
        for ($px = $x; $px -lt ($x + $width); $px++) {
            Pixel-Alpha $bitmap $px $py $alpha $color
        }
    }
}

function Add-Chassis($bitmap, [string]$base = "#0C1420") {
    Rect $bitmap 0 0 16 16 $base
    Rect $bitmap 0 0 16 1 "#26374D"; Rect $bitmap 0 15 16 1 "#070B12"
    Rect $bitmap 0 0 1 16 "#26374D"; Rect $bitmap 15 0 1 16 "#070B12"
    Rect $bitmap 1 1 2 2 "#33465C"; Rect $bitmap 13 1 2 2 "#33465C"
    Rect $bitmap 1 13 2 2 "#192638"; Rect $bitmap 13 13 2 2 "#192638"
    Pixel $bitmap 1 1 "#5B6B79"; Pixel $bitmap 14 1 "#5B6B79"
    Pixel $bitmap 1 14 "#05080E"; Pixel $bitmap 14 14 "#05080E"
    Rect $bitmap 6 1 4 1 "#E39B24"; Rect $bitmap 6 14 4 1 "#A86414"
    Rect $bitmap 1 6 1 4 "#E39B24"; Rect $bitmap 14 6 1 4 "#A86414"
}

function Save-Frames([string]$name, [scriptblock]$builder) {
    $strip = New-Object System.Drawing.Bitmap 16, (16 * $frameCount), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $sprite = & $builder $frame
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $strip.SetPixel($x, $frame * 16 + $y, $sprite.GetPixel($x, $y)) } }
        $sprite.Dispose()
    }
    $strip.Save((Join-Path $blockRoot $name), [System.Drawing.Imaging.ImageFormat]::Png)
    $strip.Dispose()
}

function Core-Top([int]$frame, [string[]]$palette) {
    $b = Canvas; Add-Chassis $b
    Rect $b 3 3 10 10 "#111E2C"; Rect $b 4 4 8 8 "#07101B"
    Rect $b 5 5 6 6 $palette[2]; Rect $b 6 6 4 4 $palette[1]
    Rect $b 7 7 2 2 $(if (($frame % 6) -lt 3) { $palette[0] } else { $palette[1] })
    $path = @(@(5,3),@(7,3),@(10,3),@(12,5),@(12,7),@(12,10),@(10,12),@(8,12),@(5,12),@(3,10),@(3,8),@(3,5))
    foreach ($offset in @(0,4,8)) {
        $head = ($frame + $offset) % $path.Count; $tail = ($head + $path.Count - 1) % $path.Count
        Pixel $b $path[$tail][0] $path[$tail][1] $palette[1]; Pixel $b $path[$head][0] $path[$head][1] $palette[0]
    }
    return $b
}

function Core-Side([int]$frame, [string[]]$palette) {
    $b = Canvas; Add-Chassis $b
    Rect $b 2 2 12 12 "#172332"; Rect $b 3 3 10 10 "#080F18"
    Rect $b 3 3 10 2 $palette[2]; Rect $b 4 4 8 1 $palette[1]
    for ($row = 0; $row -lt 4; $row++) {
        $y = 6 + $row * 2; Rect $b 2 $y 12 1 "#8A5614"; Rect $b 3 $y 10 1 "#C67C1B"
        $headX = 2 + (($frame + $row * 3) % 12); $tailX = 2 + (($frame + $row * 3 + 11) % 12)
        Pixel $b $tailX $y $palette[1]; Pixel $b $headX $y $palette[0]
    }
    Rect $b 1 6 1 6 $palette[2]; Rect $b 14 6 1 6 $palette[2]
    return $b
}

function Terminal-Face([int]$frame) {
    $b = Canvas; Add-Chassis $b
    Rect $b 3 3 10 10 "#17313A"; Rect $b 4 4 8 8 "#06171F"
    for ($y = 0; $y -lt 4; $y++) { for ($x = 0; $x -lt 4; $x++) {
        $scan = (($frame + $x + $y * 2) % 12)
        $shade = if ($scan -eq 0) { "#E8FFFF" } elseif ($scan -le 2) { "#43E5E8" } else { "#147A89" }
        Pixel $b (5 + $x * 2) (5 + $y * 2) $shade
    } }
    $orbit = @(@(5,2),@(8,2),@(11,3),@(13,5),@(13,8),@(12,11),@(10,13),@(7,13),@(4,12),@(2,10),@(2,7),@(3,4))
    foreach ($offset in @(0,6)) {
        $head = ($frame + $offset) % $orbit.Count; $tail = ($head + $orbit.Count - 1) % $orbit.Count
        Pixel $b $orbit[$tail][0] $orbit[$tail][1] "#A86414"; Pixel $b $orbit[$head][0] $orbit[$head][1] "#FFE08A"
    }
    return $b
}

function Crafting-Top([int]$frame) {
    $b = Canvas; Add-Chassis $b
    Rect $b 3 3 10 10 "#153743"; Rect $b 4 4 8 8 "#07151E"
    $cells = @(@(4,4),@(7,4),@(10,4),@(10,7),@(10,10),@(7,10),@(4,10),@(4,7),@(7,7),@(7,4),@(10,7),@(7,10))
    foreach ($p in @(@(4,4),@(7,4),@(10,4),@(4,7),@(7,7),@(10,7),@(4,10),@(7,10),@(10,10))) {
        Rect $b $p[0] $p[1] 2 2 "#157F8C"; Pixel $b $p[0] $p[1] "#38BBC4"
    }
    $head = $cells[$frame]; Rect $b $head[0] $head[1] 2 2 "#63F5F4"; Pixel $b $head[0] $head[1] "#E8FFFF"
    Pixel $b 7 7 "#FFE18A"; Pixel $b 8 8 "#E39B24"
    return $b
}

function Linker-Top([int]$frame) {
    $b = Canvas; Add-Chassis $b "#0D1220"
    Rect $b 3 3 10 10 "#15152B"; Rect $b 4 4 8 8 "#090C18"
    Rect $b 6 6 4 4 "#283F5A"; Rect $b 7 7 2 2 "#74F7F2"; Pixel $b 7 7 "#E5FFFF"
    $flow = @(@(5,4),@(7,4),@(10,4),@(11,5),@(11,8),@(11,10),@(10,11),@(7,11),@(5,11),@(4,10),@(4,7),@(4,5))
    for ($trail = 0; $trail -lt 4; $trail++) {
        $index = ($frame + $flow.Count - $trail) % $flow.Count; $shade = @("#E8A7FF","#B84DEB","#7B30B4","#42215F")[$trail]
        Pixel $b $flow[$index][0] $flow[$index][1] $shade
    }
    foreach ($p in @(@(3,3),@(12,3),@(3,12),@(12,12))) { Pixel $b $p[0] $p[1] "#40DCE2" }
    return $b
}

function Linker-Side([int]$frame) {
    $b = Canvas; Add-Chassis $b "#0D1220"
    Rect $b 4 3 8 10 "#17162A"; Rect $b 5 4 6 8 "#080C17"; Rect $b 6 5 4 6 "#2A1B42"; Rect $b 7 6 2 4 "#6B3097"
    $stream = @(@(7,5),@(8,5),@(9,6),@(9,7),@(9,8),@(9,9),@(8,10),@(7,10),@(6,9),@(6,8),@(6,7),@(6,6))
    for ($trail = 0; $trail -lt 4; $trail++) {
        $index = ($frame + $stream.Count - $trail) % $stream.Count; $shade = @("#F0B7FF","#C45CF1","#7F39B4","#3B2056")[$trail]
        Pixel $b $stream[$index][0] $stream[$index][1] $shade
    }
    Pixel $b 2 (5 + ($frame % 7)) "#7040A0"; Pixel $b 13 (11 - ($frame % 7)) "#7040A0"; Pixel $b 8 8 "#75F5F1"
    return $b
}

function Antenna-Texture([int]$frame) {
    $b = Canvas
    # A black-hole-like core: broad, nearly opaque layers with only a restrained
    # purple rim. This keeps the singularity readable in bright Overworld light.
    Rect-Alpha $b 4 1 8 14 48 "#2A073A"
    Rect-Alpha $b 2 3 12 10 62 "#3B0A4D"
    Rect-Alpha $b 1 5 14 6 82 "#4E1064"
    Rect-Alpha $b 4 4 8 8 135 "#190522"
    Rect-Alpha $b 5 5 6 6 185 "#09020F"
    Rect-Alpha $b 6 6 4 4 225 "#020106"
    Rect-Alpha $b 7 7 2 2 250 "#000000"
    foreach ($p in @(@(4,2),@(8,1),@(11,2),@(13,4),@(14,8),@(13,11),@(11,13),@(8,14),@(4,13),@(2,11),@(1,8),@(2,4))) {
        Pixel-Alpha $b $p[0] $p[1] 112 "#4C1467"
    }
    $flow = @(@(4,2),@(8,1),@(11,2),@(13,4),@(14,8),@(13,11),@(11,13),@(8,14),@(4,13),@(2,11),@(1,8),@(2,4))
    for ($trail = 0; $trail -lt 4; $trail++) {
        $index = ($frame + $flow.Count - $trail) % $flow.Count
        $shade = @("#B86DD5","#84309F","#53156D","#26082F")[$trail]
        Pixel-Alpha $b $flow[$index][0] $flow[$index][1] (205 - $trail * 28) $shade
    }
    $inner = @(@(6,4),@(9,4),@(11,6),@(11,9),@(9,11),@(6,11),@(4,9),@(4,6))
    $innerIndex = [Math]::Floor($frame / 2) % $inner.Count
    Pixel-Alpha $b $inner[$innerIndex][0] $inner[$innerIndex][1] 210 "#4C9B9D"
    return $b
}

function Antenna-Metal {
    $b = Canvas "#182437"
    Rect $b 0 0 16 1 "#52667A"; Rect $b 0 15 16 1 "#080D16"
    Rect $b 0 0 1 16 "#52667A"; Rect $b 15 0 1 16 "#080D16"
    Rect $b 2 2 12 12 "#22344A"; Rect $b 3 3 10 10 "#101827"
    Rect $b 6 1 4 14 "#A96515"; Rect $b 7 1 2 14 "#E8A02A"
    Pixel $b 7 3 "#FFF0A8"; Pixel $b 8 7 "#56F4F0"; Pixel $b 7 11 "#BA52E7"
    Rect $b 2 6 2 4 "#334B61"; Rect $b 12 6 2 4 "#334B61"
    return $b
}

function Antenna-Item {
    $b = Canvas
    Rect $b 7 1 2 9 "#D9E5E4"; Pixel $b 7 1 "#FFFFFF"; Pixel $b 8 2 "#8799A8"
    Rect $b 5 4 6 1 "#D88E1F"; Pixel $b 4 5 "#7C39AA"; Pixel $b 11 5 "#7C39AA"; Pixel $b 3 6 "#B750E7"; Pixel $b 12 6 "#B750E7"
    Rect $b 6 9 4 2 "#E39B24"; Rect $b 5 11 6 2 "#394C61"; Rect $b 4 13 8 2 "#182536"
    Pixel $b 5 13 "#74F7F2"; Pixel $b 10 13 "#74F7F2"
    return $b
}

function Tablet-Canvas {
    $bitmap = New-Object System.Drawing.Bitmap 32, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bitmap.MakeTransparent(); return $bitmap
}

function TPixel($bitmap, [int]$x, [int]$y, [string]$color) {
    if ($x -ge 0 -and $x -lt 32 -and $y -ge 0 -and $y -lt 32) { $bitmap.SetPixel($x, $y, (Color $color)) }
}

function TRect($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$color) {
    for ($py = $y; $py -lt ($y + $height); $py++) { for ($px = $x; $px -lt ($x + $width); $px++) { TPixel $bitmap $px $py $color } }
}

function Tablet-Frame([int]$frame) {
    $b = Tablet-Canvas
    TRect $b 5 1 22 30 "#080D18"; TRect $b 2 4 28 24 "#080D18"; TRect $b 4 2 24 28 "#101A2B"
    TRect $b 2 7 2 18 "#263A55"; TRect $b 28 7 2 18 "#050812"; TRect $b 7 1 18 2 "#334B6D"; TRect $b 7 29 18 2 "#050812"
    TRect $b 5 4 22 24 "#1A2B44"
    foreach ($p in @(@(3,4),@(25,4),@(3,24),@(25,24))) {
        TRect $b $p[0] $p[1] 4 4 "#A76312"; TRect $b ($p[0] + 1) $p[1] 3 2 "#FFC241"; TPixel $b ($p[0] + 1) ($p[1] + 2) "#FFE5A0"
    }
    TRect $b 8 3 6 1 "#36EAF0"; TRect $b 18 3 6 1 "#1E9EBD"; TRect $b 8 28 6 1 "#1E9EBD"; TRect $b 18 28 6 1 "#36EAF0"
    TRect $b 3 10 1 5 "#33E8EA"; TRect $b 28 17 1 5 "#33E8EA"
    TRect $b 6 5 20 21 "#050816"; TRect $b 7 6 18 19 "#07152A"; TRect $b 8 7 16 17 "#08213A"; TRect $b 9 8 14 15 "#06172A"
    for ($y = 9; $y -le 21; $y += 3) { TRect $b 10 $y 12 1 "#0D3151" }
    foreach ($p in @(@(10,8),@(21,8),@(10,22),@(21,22))) { TRect $b $p[0] $p[1] 2 1 "#40F3F3" }
    $scanY = 9 + ($frame % 12); TRect $b 10 $scanY 12 1 "#176B83"; TPixel $b (10 + ($frame % 12)) $scanY "#B8FFFF"
    $orbit = @(@(13,10),@(16,9),@(19,10),@(21,12),@(21,15),@(20,18),@(18,20),@(15,21),@(12,20),@(10,18),@(10,15),@(11,12))
    for ($trail = 0; $trail -lt 4; $trail++) {
        $index = ($frame + $orbit.Count - $trail) % $orbit.Count; $shade = @("#FFB7FF","#D85AFF","#8E38D0","#452168")[$trail]
        TPixel $b $orbit[$index][0] $orbit[$index][1] $shade
    }
    TRect $b 14 13 4 5 "#4B2082"; TRect $b 15 12 2 1 "#B84EF1"; TRect $b 15 14 2 3 "#B84EF1"; TPixel $b 15 14 "#F2B7FF"
    TRect $b 13 18 6 1 "#1BBED0"; TPixel $b 16 18 "#DFFFFF"
    TPixel $b 7 (7 + ($frame % 16)) "#56FFFF"; TPixel $b 24 (22 - ($frame % 16)) "#B84EF1"

    # Keep the 32px source detail, but give the item enough transparent padding
    # to match the visual footprint of ordinary vanilla tools in GUI slots.
    $scaled = Tablet-Canvas
    $graphics = [System.Drawing.Graphics]::FromImage($scaled)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $sourceRect = New-Object System.Drawing.Rectangle 0, 0, 32, 32
    $destinationRect = New-Object System.Drawing.Rectangle 3, 3, 26, 26
    $graphics.DrawImage($b, $destinationRect, $sourceRect, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    $b.Dispose()
    return $scaled
}

function Save-TabletFrames {
    $strip = New-Object System.Drawing.Bitmap 32, (32 * $frameCount), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $sprite = Tablet-Frame $frame
        for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 32; $x++) { $strip.SetPixel($x, $frame * 32 + $y, $sprite.GetPixel($x, $y)) } }
        $sprite.Dispose()
    }
    $strip.Save((Join-Path $itemRoot "remote_tablet.png"), [System.Drawing.Imaging.ImageFormat]::Png); $strip.Dispose()
}

$tiers = @(
    @{ Side="iron_storage_core.png"; Top="iron_storage_core_top.png"; Palette=@("#FFFFFF","#D8E8EA","#7E929C") },
    @{ Side="diamond_storage_core.png"; Top="diamond_storage_core_top.png"; Palette=@("#C9FFFF","#46E4E5","#138493") },
    @{ Side="blaze_storage_core.png"; Top="blaze_storage_core_top.png"; Palette=@("#FFF4A8","#FF9D25","#A43B1C") },
    @{ Side="cosmic_storage_core.png"; Top="cosmic_storage_core_top.png"; Palette=@("#F0C4FF","#C453F0","#65258A") }
)
foreach ($tier in $tiers) {
    $palette = $tier.Palette
    Save-Frames $tier.Side { param($frame) Core-Side $frame $palette }
    Save-Frames $tier.Top { param($frame) Core-Top $frame $palette }
}
Save-Frames "storage_terminal.png" { param($frame) Terminal-Face $frame }
Save-Frames "crafting_terminal_top.png" { param($frame) Crafting-Top $frame }
Save-Frames "storage_linker.png" { param($frame) Linker-Side $frame }
Save-Frames "storage_linker_top.png" { param($frame) Linker-Top $frame }
Save-Frames "dimensional_antenna.png" { param($frame) Antenna-Texture $frame }
Save-TabletFrames

$antennaMetal = Antenna-Metal
$antennaMetal.Save((Join-Path $blockRoot "dimensional_antenna_metal.png"), [System.Drawing.Imaging.ImageFormat]::Png); $antennaMetal.Dispose()

$antennaItem = Antenna-Item
$antennaItem.Save((Join-Path $itemRoot "dimensional_antenna.png"), [System.Drawing.Imaging.ImageFormat]::Png); $antennaItem.Dispose()

$previewNames = @("iron_storage_core_top.png","diamond_storage_core_top.png","blaze_storage_core_top.png","cosmic_storage_core_top.png","storage_terminal.png","crafting_terminal_top.png","storage_linker_top.png","dimensional_antenna.png")
$preview = New-Object System.Drawing.Bitmap 512, 256, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($preview); $graphics.Clear((Color "#070B11"))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor; $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
for ($index = 0; $index -lt $previewNames.Count; $index++) {
    $source = New-Object System.Drawing.Bitmap (Join-Path $blockRoot $previewNames[$index]); $sourceRect = New-Object System.Drawing.Rectangle 0,0,16,16
    $destRect = New-Object System.Drawing.Rectangle (($index % 4) * 128),([Math]::Floor($index / 4) * 128),128,128
    $graphics.DrawImage($source,$destRect,$sourceRect,[System.Drawing.GraphicsUnit]::Pixel); $source.Dispose()
}
$graphics.Dispose(); $preview.Save($previewPath,[System.Drawing.Imaging.ImageFormat]::Png); $preview.Dispose()

$animationPreview = New-Object System.Drawing.Bitmap (64 * $frameCount), 320, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$animationGraphics = [System.Drawing.Graphics]::FromImage($animationPreview); $animationGraphics.Clear((Color "#070B11"))
$animationGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor; $animationGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$rows = @(
    @{ Path=(Join-Path $blockRoot "iron_storage_core.png"); Size=16 },
    @{ Path=(Join-Path $blockRoot "storage_terminal.png"); Size=16 },
    @{ Path=(Join-Path $blockRoot "storage_linker.png"); Size=16 },
    @{ Path=(Join-Path $blockRoot "storage_linker_top.png"); Size=16 },
    @{ Path=(Join-Path $itemRoot "remote_tablet.png"); Size=32 }
)
for ($row = 0; $row -lt $rows.Count; $row++) {
    $source = New-Object System.Drawing.Bitmap $rows[$row].Path; $size = $rows[$row].Size
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $sourceRect = New-Object System.Drawing.Rectangle 0,($frame * $size),$size,$size; $destRect = New-Object System.Drawing.Rectangle ($frame * 64),($row * 64),64,64
        $animationGraphics.DrawImage($source,$destRect,$sourceRect,[System.Drawing.GraphicsUnit]::Pixel)
    }
    $source.Dispose()
}
$animationGraphics.Dispose(); $animationPreview.Save($animationPreviewPath,[System.Drawing.Imaging.ImageFormat]::Png); $animationPreview.Dispose()
