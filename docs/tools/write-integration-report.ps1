$ErrorActionPreference = 'Stop'
$baseDir = Split-Path $PSScriptRoot -Parent
$runName = if ($env:IT_RUN) { $env:IT_RUN } else { '20261006' }
$runDir = Join-Path $baseDir ('test-results/' + $runName)
$data = Get-Content (Join-Path $runDir 'report-data.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$source = Get-ChildItem $baseDir -Filter '*V37.xlsx' | Select-Object -First 1
$target = Join-Path $baseDir $data.output
$excel = $null
$book = $null
try {
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    $excel.AutomationSecurity = 3
    $book = $excel.Workbooks.Open($source.FullName, 0, $true)
    $book.SaveAs($target, 51)
    foreach ($sheetIndex in @(3,4)) {
        $sheet = $book.Worksheets.Item($sheetIndex)
        $end = $sheet.UsedRange.Rows.Count
        for ($row=2; $row -le $end; $row++) {
            $id = [string]$sheet.Cells.Item($row,1).Value2
            $r = $data.results.PSObject.Properties[$id].Value
            if ($null -eq $r) { throw "Missing result: $id" }
            $sheet.Cells.Item($row,13).Value2 = [string]$r.status
            $sheet.Cells.Item($row,14).Value2 = [string]($r.note + "`n" + $r.evidence)
            $sheet.Cells.Item($row,15).Value2 = [string]$r.defect
            $sheet.Cells.Item($row,16).Value2 = 'Codex'
            $sheet.Cells.Item($row,17).Value2 = [string]$data.date
            $sheet.Cells.Item($row,18).Value2 = [string]$r.method
            $sheet.Rows.Item($row).RowHeight = 100
        }
    }
    $guide = $book.Worksheets.Item(1)
    $guide.Cells.Item(2,2).Value2 = [string]$data.notesTitle
    $guide.Cells.Item(3,2).Value2 = [string]$data.meta[0][1]
    $guide.Cells.Item(4,2).Value2 = [string]$data.meta[1][1]
    $guide.Cells.Item(10,2).Value2 = [string]$data.meta[4][1]
    $guide.Cells.Item(18,2).Value2 = [string]$data.meta[4][1]
    $guide.Cells.Item(19,2).Value2 = [string]$data.meta[0][1]
    $guide.Cells.Item(20,2).Value2 = [string]$data.meta[7][1]
    $guide.Cells.Item(21,2).Value2 = [string]($data.date + ' / Codex')
    $guide.Cells.Item(7,2).Value2 = [string]$data.meta[3][1]
    $guide.Cells.Item(16,2).Value2 = [string]$data.meta[11][1]
    $defects = $book.Worksheets.Item(7)
    $row = 2
    foreach ($d in $data.defs) {
        for ($col=1;$col -le 8;$col++) { $defects.Cells.Item($row,$col).Value2 = [string]$d[$col-1] }
        $defects.Cells.Item($row,10).Value2 = $(if ($data.retest) { 'FIXED' } else { 'OPEN' })
        $defects.Cells.Item($row,11).Value2 = [string]$data.date
        $defects.Cells.Item($row,13).Value2 = $(if ($data.retest) { 'PASS' } else { 'Not fixed' })
        if ($data.retest) {
            $defects.Cells.Item($row,12).Value2 = 'V39 / V920 + working tree'
            $defects.Cells.Item($row,14).Value2 = [string]$data.fixes.PSObject.Properties[[string]$d[0]].Value
        }
        $defects.Rows.Item($row).RowHeight = 125
        $row++
    }
    $recon = $book.Worksheets.Item(6)
    $pass = [string]$data.results.'E2E-020'.status
    for ($row=2;$row -le 12;$row++) {
        $recon.Cells.Item($row,6).Value2 = [string]($recon.Cells.Item($row,4).Value2 + ' / E2E API + DB evidence; state.json')
        $recon.Cells.Item($row,7).Value2 = $pass
    }
    $recon.Cells.Item(13,6).Value2 = [string]$data.results.'ORD-009'.note
    $recon.Cells.Item(13,7).Value2 = [string]$data.results.'ORD-009'.status
    $run = $book.Worksheets.Add([Type]::Missing,$book.Worksheets.Item($book.Worksheets.Count))
    $run.Name = [string]$data.runSheet
    $run.Cells.Item(1,1).Value2 = [string]$data.notesTitle
    $run.Cells.Item(1,2).Value2 = [string]$data.date
    $row=2
    foreach ($entry in $data.meta) {
        $run.Cells.Item($row,1).Value2 = [string]$entry[0]
        $run.Cells.Item($row,2).Value2 = [string]$entry[1]
        $row++
    }
    $run.Columns.Item(1).ColumnWidth = 23
    $run.Columns.Item(2).ColumnWidth = 125
    $run.UsedRange.WrapText = $true
    $run.UsedRange.Font.Name = 'Malgun Gothic'
    $run.UsedRange.Font.Size = 11
    $run.UsedRange.Rows.RowHeight = 60
    $run.Rows.Item(1).Font.Bold = $true
    $run.Rows.Item(1).Interior.Color = 5263440
    $run.Rows.Item(1).Font.Color = 16777215
    $book.Worksheets.Item(2).Activate()
    $excel.CalculateFullRebuild()
    $book.Save()
    $summary=$book.Worksheets.Item(2)
    $last=$summary.UsedRange.Rows.Count
    $totals=@()
    for($col=1;$col -le 9;$col++) { $totals += $summary.Cells.Item($last,$col).Value2 }
    @{path=$target;totals=$totals;sheets=$book.Worksheets.Count} | ConvertTo-Json | Set-Content (Join-Path $runDir 'excel-validation.json') -Encoding UTF8
    Write-Output ($totals -join ' | ')
} finally {
    if ($book) { $book.Close($false) }
    if ($excel) { $excel.Quit(); [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($excel) }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
