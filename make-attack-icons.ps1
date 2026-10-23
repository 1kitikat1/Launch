# Генерация иконок Attack (логотип, иконка карточки, баннер клиента)
# Всё рисуется в большом разрешении, лаунчер сам уменьшает — без пиксельности.
Add-Type -AssemblyName System.Drawing

$assets = 'C:\Users\Kitikat\Desktop\Loader\src\main\resources\assets'
$red = [System.Drawing.Color]::FromArgb(255, 226, 48, 84)
$redLight = [System.Drawing.Color]::FromArgb(255, 255, 122, 150)
$dark = [System.Drawing.Color]::FromArgb(255, 22, 23, 30)
$darker = [System.Drawing.Color]::FromArgb(255, 13, 14, 19)

function New-Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $bmp.SetResolution(96, 96)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    return @($bmp, $g)
}

function RoundedPath($g, $x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Save($bmp, $g, $path) {
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  -> $(Split-Path $path -Leaf)"
}

# --- основная иконка: тёмный квадрат с красной «A» ---
function Draw-Mark($size) {
    $c = New-Canvas $size $size
    $bmp = $c[0]; $g = $c[1]

    # фон со скруглением
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle 0, 0, $size, $size), $dark, $darker, 55.0)
    $path = RoundedPath $g 0.5 0.5 ($size - 1) ($size - 1) ($size * 0.22)
    $g.FillPath($bg, $path)
    $penColor = [System.Drawing.Color]::FromArgb(70, 255, 255, 255)
    $penWidth = [single]([Math]::Max(1.0, $size * 0.012))
    $g.DrawPath((New-Object System.Drawing.Pen($penColor, $penWidth)), $path)

    # диагональный блик
    $shine = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle 0, 0, $size, $size),
        [System.Drawing.Color]::FromArgb(46, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(0, 255, 255, 255), 40.0)
    $g.SetClip($path)
    $g.FillRectangle($shine, 0, 0, $size, $size)
    $g.ResetClip()

    # буква A
    $font = New-Object System.Drawing.Font('Impact', ($size * 0.66), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
    $letter = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle 0, 0, $size, $size), $redLight, $red, 90.0)
    $g.DrawString('A', $font, $letter, (New-Object System.Drawing.RectangleF 0, (-$size * 0.03), $size, $size), $fmt)
    return @($bmp, $g)
}

# логотип для шапки (рисуем крупно — лаунчер уменьшит)
$c = Draw-Mark 256
Save $c[0] $c[1] (Join-Path $assets 'attack.png')

# иконки разных размеров
foreach ($size in 128, 64, 32) {
    $c = Draw-Mark $size
    Save $c[0] $c[1] (Join-Path $assets ("attack-icon-{0}.png" -f $size))
}
Copy-Item (Join-Path $assets 'attack-icon-128.png') (Join-Path $assets 'attackdlc-icon.png') -Force

# --- баннер карточки клиента ---
$c = New-Canvas 640 360
$bmp = $c[0]; $g = $c[1]
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Rectangle 0, 0, 640, 360), [System.Drawing.Color]::FromArgb(255, 30, 32, 42), $darker, 35.0)
$g.FillRectangle($brush, 0, 0, 640, 360)
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(30, 255, 255, 255)), 52
for ($x = -360; $x -lt 1000; $x += 148) {
    $g.DrawLine($pen, $x, 380, $x + 380, -20)
}
$pen.Dispose()
$g.FillRectangle((New-Object System.Drawing.SolidBrush $red), 0, 300, 640, 10)

$font = New-Object System.Drawing.Font('Impact', 116, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
$letter = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Rectangle 0, 0, 640, 360), $redLight, $red, 90.0)
$g.DrawString('ATTACK', $font, $letter, (New-Object System.Drawing.RectangleF 0, 80, 640, 190), $fmt)
$font2 = New-Object System.Drawing.Font('Segoe UI', 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('DLC  ·  26.2', $font2, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(225, 232, 236, 244))), (New-Object System.Drawing.RectangleF 0, 210, 640, 60), $fmt)
Save $bmp $g (Join-Path $assets 'attackdlc-client.png')

Write-Host "готово"
