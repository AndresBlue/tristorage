Add-Type -AssemblyName System.Drawing

$assetRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\tristorage\textures'
$blockRoot = Join-Path $assetRoot 'block'
$itemRoot = Join-Path $assetRoot 'item'
$entityRoot = Join-Path $assetRoot 'entity'
$particleRoot = Join-Path $assetRoot 'particle'
@($blockRoot, $itemRoot, $entityRoot, $particleRoot) | ForEach-Object {
    [System.IO.Directory]::CreateDirectory($_) | Out-Null
}

function Color([string]$hex, [int]$alpha = 255) {
    $value = $hex.TrimStart('#')
    return [System.Drawing.Color]::FromArgb(
        $alpha,
        [Convert]::ToInt32($value.Substring(0, 2), 16),
        [Convert]::ToInt32($value.Substring(2, 2), 16),
        [Convert]::ToInt32($value.Substring(4, 2), 16))
}

function Pixel($bitmap, [int]$x, [int]$y, $color) {
    if ($x -ge 0 -and $x -lt $bitmap.Width -and $y -ge 0 -and $y -lt $bitmap.Height) {
        $bitmap.SetPixel($x, $y, $color)
    }
}

function Rect($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, $color) {
    for ($py = $y; $py -lt $y + $height; $py++) {
        for ($px = $x; $px -lt $x + $width; $px++) {
            Pixel $bitmap $px $py $color
        }
    }
}

function FrameRect($bitmap, [int]$frame, [int]$x, [int]$y, [int]$width, [int]$height, $color) {
    Rect $bitmap $x ($frame * 16 + $y) $width $height $color
}

function Save($bitmap, [string]$path) {
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function New-CoreSide([string]$name, [string]$accentHex, [string]$brightHex) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 256)
    $dark = Color '#090D19'; $frame = Color '#263449'; $rail = Color '#B87611'
    $accent = Color $accentHex; $bright = Color $brightHex; $shadow = Color '#171E2D'
    for ($f = 0; $f -lt 16; $f++) {
        FrameRect $bitmap $f 0 0 16 16 $dark
        FrameRect $bitmap $f 0 0 16 2 $frame
        FrameRect $bitmap $f 0 14 16 2 $frame
        FrameRect $bitmap $f 0 0 2 16 $frame
        FrameRect $bitmap $f 14 0 2 16 $frame
        FrameRect $bitmap $f 2 2 2 2 $accent
        FrameRect $bitmap $f 12 2 2 2 $accent
        FrameRect $bitmap $f 2 12 2 2 $accent
        FrameRect $bitmap $f 12 12 2 2 $accent
        for ($railIndex = 0; $railIndex -lt 3; $railIndex++) {
            $y = 5 + $railIndex * 3
            FrameRect $bitmap $f 3 $y 10 1 $rail
            $position = 3 + (($f + $railIndex * 5) % 10)
            FrameRect $bitmap $f $position $y 2 1 $bright
            Pixel $bitmap ($position - 1) ($f * 16 + $y) $shadow
        }
    }
    Save $bitmap (Join-Path $blockRoot "$name.png")
}

function New-CoreTop([string]$name, [string]$accentHex, [string]$brightHex) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 256)
    $dark = Color '#080C17'; $frame = Color '#263449'; $accent = Color $accentHex
    $bright = Color $brightHex; $inner = Color '#151C2B'
    $path = @(@(3,3),@(5,3),@(7,3),@(9,3),@(11,3),@(12,5),@(12,7),@(12,9),@(12,11),@(10,12),@(8,12),@(6,12),@(4,12),@(3,10),@(3,8),@(3,6))
    for ($f = 0; $f -lt 16; $f++) {
        FrameRect $bitmap $f 0 0 16 16 $dark
        FrameRect $bitmap $f 0 0 16 2 $frame; FrameRect $bitmap $f 0 14 16 2 $frame
        FrameRect $bitmap $f 0 0 2 16 $frame; FrameRect $bitmap $f 14 0 2 16 $frame
        FrameRect $bitmap $f 2 2 12 12 $inner
        FrameRect $bitmap $f 4 4 8 8 $accent
        FrameRect $bitmap $f 6 6 4 4 $dark
        $point = $path[$f]
        FrameRect $bitmap $f $point[0] $point[1] 2 2 $bright
    }
    Save $bitmap (Join-Path $blockRoot "$name.png")
}

function New-TerminalSheet([string]$name, [bool]$crafting) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 256)
    $dark = Color '#070C17'; $frame = Color '#263449'; $cyan = Color '#20AFC1'
    $cyanBright = Color '#67F3F1'; $orange = Color '#D28A16'; $orangeBright = Color '#FFE6A4'
    for ($f = 0; $f -lt 16; $f++) {
        FrameRect $bitmap $f 0 0 16 16 $dark
        FrameRect $bitmap $f 0 0 16 2 $frame; FrameRect $bitmap $f 0 14 16 2 $frame
        FrameRect $bitmap $f 0 0 2 16 $frame; FrameRect $bitmap $f 14 0 2 16 $frame
        for ($row = 0; $row -lt 3; $row++) {
            for ($column = 0; $column -lt 3; $column++) {
                $lit = (($row * 3 + $column + $f) % 9) -eq 0
                $cellColor = if ($lit) { $cyanBright } else { $cyan }
                FrameRect $bitmap $f (3 + $column * 4) (3 + $row * 4) 3 3 $cellColor
            }
        }
        if ($crafting) {
            FrameRect $bitmap $f 2 2 1 12 $orange
            FrameRect $bitmap $f 13 2 1 12 $orange
        } else {
            Pixel $bitmap (2 + ($f % 12)) ($f * 16 + 2) $orangeBright
            Pixel $bitmap (13 - ($f % 12)) ($f * 16 + 13) $orange
        }
    }
    Save $bitmap (Join-Path $blockRoot "$name.png")
}

function New-TerminalTop([string]$name, [bool]$crafting) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 256)
    $dark = Color '#080C17'; $frame = Color '#263449'; $cyan = Color '#25BAC6'
    $cyanBright = Color '#71FFFF'; $orange = Color '#D98C18'; $wood = Color '#9B542E'
    $ring = @(@(3,2),@(6,2),@(9,2),@(12,3),@(13,6),@(13,9),@(12,12),@(9,13),@(6,13),@(3,12),@(2,9),@(2,6),@(3,3),@(6,3),@(9,3),@(12,4))
    for ($f = 0; $f -lt 16; $f++) {
        FrameRect $bitmap $f 0 0 16 16 $dark
        FrameRect $bitmap $f 0 0 16 2 $frame; FrameRect $bitmap $f 0 14 16 2 $frame
        FrameRect $bitmap $f 0 0 2 16 $frame; FrameRect $bitmap $f 14 0 2 16 $frame
        if ($crafting) {
            FrameRect $bitmap $f 2 2 12 12 $wood
            FrameRect $bitmap $f 3 3 10 10 $dark
        }
        for ($row = 0; $row -lt 3; $row++) {
            for ($column = 0; $column -lt 3; $column++) {
                $cellColor = if (($row + $column + $f) % 5 -eq 0) { $cyanBright } else { $cyan }
                FrameRect $bitmap $f (5 + $column * 2) (5 + $row * 2) 1 1 $cellColor
            }
        }
        $point = $ring[$f]
        FrameRect $bitmap $f $point[0] $point[1] 2 1 $orange
    }
    Save $bitmap (Join-Path $blockRoot "$name.png")
}

function New-LinkerSheet([string]$name, [bool]$top) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 256)
    $dark = Color '#080A16'; $frame = Color '#27344A'; $purple = Color '#60268E'
    $violet = Color '#A84EE3'; $cyan = Color '#42CFD6'; $black = Color '#03030A'
    for ($f = 0; $f -lt 16; $f++) {
        FrameRect $bitmap $f 0 0 16 16 $dark
        FrameRect $bitmap $f 0 0 16 2 $frame; FrameRect $bitmap $f 0 14 16 2 $frame
        FrameRect $bitmap $f 0 0 2 16 $frame; FrameRect $bitmap $f 14 0 2 16 $frame
        if ($top) {
            FrameRect $bitmap $f 3 3 10 10 $purple
            FrameRect $bitmap $f 5 5 6 6 $black
            $angle = $f * [Math]::PI * 2 / 16
            $px = 7 + [int][Math]::Round([Math]::Cos($angle) * 5)
            $py = 7 + [int][Math]::Round([Math]::Sin($angle) * 5)
            $dotColor = if ($f % 4 -eq 0) { $cyan } else { $violet }
            FrameRect $bitmap $f $px $py 2 2 $dotColor
        } else {
            FrameRect $bitmap $f 3 3 10 10 $purple
            FrameRect $bitmap $f 5 5 6 6 $black
            $flow = 4 + ($f % 8)
            FrameRect $bitmap $f $flow 4 2 1 $violet
            FrameRect $bitmap $f (14 - $flow) 11 2 1 $cyan
        }
    }
    Save $bitmap (Join-Path $blockRoot "$name.png")
}

function New-StaticBlockTextures {
    $bottom = [System.Drawing.Bitmap]::new(16, 16)
    Rect $bottom 0 0 16 16 (Color '#090D19')
    Rect $bottom 0 0 16 2 (Color '#263449'); Rect $bottom 0 14 16 2 (Color '#263449')
    Rect $bottom 0 0 2 16 (Color '#263449'); Rect $bottom 14 0 2 16 (Color '#263449')
    Rect $bottom 4 4 8 8 (Color '#151C2B')
    Save $bottom (Join-Path $blockRoot 'machine_bottom.png')

    foreach ($pair in @(@('antenna_dark.png','#151424'),@('antenna_accent.png','#5A2A86'))) {
        $bitmap = [System.Drawing.Bitmap]::new(16, 16)
        Rect $bitmap 0 0 16 16 (Color $pair[1])
        Save $bitmap (Join-Path $blockRoot $pair[0])
    }
}

function New-Tablet {
    $bitmap = [System.Drawing.Bitmap]::new(32, 640)
    $transparent = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
    $frame = Color '#243249'; $edge = Color '#080B16'; $purple = Color '#7435B0'
    $cyan = Color '#42E5EF'; $blue = Color '#145EA5'; $orange = Color '#F0A323'
    for ($f = 0; $f -lt 20; $f++) {
        Rect $bitmap 0 ($f * 32) 32 32 $transparent
        Rect $bitmap 4 ($f * 32 + 1) 24 30 $edge
        Rect $bitmap 6 ($f * 32 + 2) 20 28 $frame
        Rect $bitmap 8 ($f * 32 + 4) 16 22 (Color '#030611')
        Rect $bitmap 9 ($f * 32 + 5) 14 20 (Color '#07152B')
        Rect $bitmap 5 ($f * 32 + 5) 2 4 $purple
        Rect $bitmap 25 ($f * 32 + 22) 2 4 $orange
        Rect $bitmap 12 ($f * 32 + 28) 8 1 $cyan
        $scan = 6 + ($f % 17)
        Rect $bitmap 10 ($f * 32 + $scan) 12 1 $blue
        Pixel $bitmap (10 + (($f * 3) % 12)) ($f * 32 + 8 + ($f % 10)) $cyan
        Pixel $bitmap (21 - (($f * 2) % 10)) ($f * 32 + 20 - ($f % 8)) $purple
        Rect $bitmap 13 ($f * 32 + 11) 6 6 $purple
        Rect $bitmap 15 ($f * 32 + 13) 2 2 $cyan
    }
    Save $bitmap (Join-Path $itemRoot 'remote_tablet.png')
}

function New-AntennaItem {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16)
    $transparent = [System.Drawing.Color]::FromArgb(0,0,0,0)
    Rect $bitmap 0 0 16 16 $transparent
    $dark = Color '#151424'; $frame = Color '#29354B'; $purple = Color '#7E36B5'; $cyan = Color '#4FE4E8'
    Rect $bitmap 2 11 12 2 $frame; Rect $bitmap 3 9 2 3 $dark; Rect $bitmap 11 9 2 3 $dark
    Rect $bitmap 4 7 2 3 $purple; Rect $bitmap 10 7 2 3 $purple
    Rect $bitmap 5 5 2 3 $dark; Rect $bitmap 9 5 2 3 $dark
    Rect $bitmap 6 3 4 4 (Color '#020208'); Pixel $bitmap 6 4 $purple; Pixel $bitmap 9 5 $cyan
    Save $bitmap (Join-Path $itemRoot 'dimensional_antenna.png')
}

function New-Singularity {
    $bitmap = [System.Drawing.Bitmap]::new(32, 512)
    for ($f = 0; $f -lt 16; $f++) {
        for ($y = 0; $y -lt 32; $y++) {
            for ($x = 0; $x -lt 32; $x++) {
                $dx = $x - 15.5; $dy = $y - 15.5
                $distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
                $angle = [Math]::Atan2($dy, $dx)
                $wave = [Math]::Sin($angle * 5 + $f * [Math]::PI / 8)
                if ($distance -lt 8.2) {
                    $shade = [int](2 + [Math]::Max(0, 8 - $distance))
                    $color = [System.Drawing.Color]::FromArgb(255, $shade, 0, $shade + 5)
                } elseif ($distance -lt 11.4 + $wave * 0.7) {
                    $pulse = [int](120 + 80 * (($wave + 1) / 2))
                    $blue = [Math]::Min(255, $pulse + 65)
                    $color = [System.Drawing.Color]::FromArgb(255, $pulse, 22, $blue)
                } elseif ($distance -lt 13.2) {
                    $alpha = [int](160 * (13.2 - $distance) / 1.8)
                    $color = [System.Drawing.Color]::FromArgb([Math]::Max(0,$alpha), 86, 24, 170)
                } else {
                    $color = [System.Drawing.Color]::FromArgb(0,0,0,0)
                }
                Pixel $bitmap $x ($f * 32 + $y) $color
            }
        }
    }
    Save $bitmap (Join-Path $entityRoot 'singularity.png')
}

function New-Particle([string]$name, [string]$mainHex, [string]$brightHex) {
    $bitmap = [System.Drawing.Bitmap]::new(8, 8)
    Rect $bitmap 0 0 8 8 ([System.Drawing.Color]::FromArgb(0,0,0,0))
    $main = Color $mainHex 190; $bright = Color $brightHex 235
    Rect $bitmap 2 2 4 4 $main
    Rect $bitmap 3 1 2 6 $main
    Rect $bitmap 1 3 6 2 $main
    Rect $bitmap 3 3 2 2 $bright
    Save $bitmap (Join-Path $particleRoot "$name.png")
}

New-CoreSide 'iron_storage_core_side' '#CBE7E9' '#FFFFFF'
New-CoreSide 'diamond_storage_core_side' '#35C9D8' '#9CFFFF'
New-CoreSide 'blaze_storage_core_side' '#E97822' '#FFD071'
New-CoreSide 'cosmic_storage_core_side' '#8E45C4' '#E39BFF'
New-CoreTop 'iron_storage_core_top' '#BFDDE1' '#FFFFFF'
New-CoreTop 'diamond_storage_core_top' '#28BBC9' '#9CFFFF'
New-CoreTop 'blaze_storage_core_top' '#E97822' '#FFD071'
New-CoreTop 'cosmic_storage_core_top' '#853ABB' '#E39BFF'
New-TerminalSheet 'storage_terminal_side' $false
New-TerminalTop 'storage_terminal_top' $false
New-TerminalSheet 'crafting_terminal_side' $true
New-TerminalTop 'crafting_terminal_top' $true
New-LinkerSheet 'storage_linker_side' $false
New-LinkerSheet 'storage_linker_top' $true
New-StaticBlockTextures
New-Tablet
New-AntennaItem
New-Singularity
New-Particle 'converging_portal' '#9C31DC' '#F1A3FF'
New-Particle 'converging_end' '#5769D6' '#BBD2FF'
