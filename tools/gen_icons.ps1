Add-Type -AssemblyName System.Drawing

function New-Icon {
    param([int]$Size, [string]$Path, [bool]$Round)

    $bmp = New-Object System.Drawing.Bitmap($Size, $Size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
    $c1 = [System.Drawing.Color]::FromArgb(255, 124, 99, 216)
    $c2 = [System.Drawing.Color]::FromArgb(255, 46, 28, 99)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45.0)

    if ($Round) {
        $g.FillEllipse($brush, $rect)
    } else {
        $r = [float]($Size * 0.22)
        $p = New-Object System.Drawing.Drawing2D.GraphicsPath
        $d = $r * 2
        $p.AddArc(0, 0, $d, $d, 180, 90)
        $p.AddArc($Size - $d, 0, $d, $d, 270, 90)
        $p.AddArc($Size - $d, $Size - $d, $d, $d, 0, 90)
        $p.AddArc(0, $Size - $d, $d, $d, 90, 90)
        $p.CloseFigure()
        $g.FillPath($brush, $p)
    }

    $white = [System.Drawing.Brushes]::White
    $accent = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 210, 125))

    # молния
    $bolt = @(
        (New-Object System.Drawing.PointF([float]($Size * 0.565), [float]($Size * 0.222))),
        (New-Object System.Drawing.PointF([float]($Size * 0.315), [float]($Size * 0.583))),
        (New-Object System.Drawing.PointF([float]($Size * 0.477), [float]($Size * 0.583))),
        (New-Object System.Drawing.PointF([float]($Size * 0.398), [float]($Size * 0.796))),
        (New-Object System.Drawing.PointF([float]($Size * 0.694), [float]($Size * 0.398))),
        (New-Object System.Drawing.PointF([float]($Size * 0.519), [float]($Size * 0.398)))
    )
    $g.FillPolygon($white, $bolt)

    # звезда
    $star = @(
        (New-Object System.Drawing.PointF([float]($Size * 0.741), [float]($Size * 0.204))),
        (New-Object System.Drawing.PointF([float]($Size * 0.770), [float]($Size * 0.287))),
        (New-Object System.Drawing.PointF([float]($Size * 0.852), [float]($Size * 0.317))),
        (New-Object System.Drawing.PointF([float]($Size * 0.770), [float]($Size * 0.346))),
        (New-Object System.Drawing.PointF([float]($Size * 0.741), [float]($Size * 0.430))),
        (New-Object System.Drawing.PointF([float]($Size * 0.711), [float]($Size * 0.346))),
        (New-Object System.Drawing.PointF([float]($Size * 0.630), [float]($Size * 0.317))),
        (New-Object System.Drawing.PointF([float]($Size * 0.711), [float]($Size * 0.287)))
    )
    $g.FillPolygon($accent, $star)

    $g.Dispose()
    $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $brush.Dispose()
    $accent.Dispose()
}

$root = "C:\kvest\app\src\main\res"
$map = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }

$count = 0
foreach ($k in $map.Keys) {
    $dir = Join-Path $root $k
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    New-Icon -Size $map[$k] -Path (Join-Path $dir "ic_launcher.png") -Round $false
    New-Icon -Size $map[$k] -Path (Join-Path $dir "ic_launcher_round.png") -Round $true
    $count += 2
}
Write-Output "icons written: $count"
