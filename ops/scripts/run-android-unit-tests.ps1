$ErrorActionPreference='Stop'
$project=Join-Path $PSScriptRoot '..\..\apps\android'
$cacheRoot=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$junit=Get-ChildItem -LiteralPath (Join-Path $cacheRoot 'junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1 -ExpandProperty FullName
$hamcrest=Get-ChildItem -LiteralPath (Join-Path $cacheRoot 'org.hamcrest\hamcrest-core\1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1 -ExpandProperty FullName
$kotlin=Get-ChildItem -LiteralPath (Join-Path $cacheRoot 'org.jetbrains.kotlin\kotlin-stdlib') -Recurse -Filter 'kotlin-stdlib-*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1 -ExpandProperty FullName
$gson=Get-ChildItem -LiteralPath (Join-Path $cacheRoot 'com.google.code.gson\gson') -Recurse -Filter 'gson-*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1 -ExpandProperty FullName
$classpath=@((Join-Path $project 'app\build\tmp\kotlin-classes\debugUnitTest'),(Join-Path $project 'app\build\tmp\kotlin-classes\debug'),$junit,$hamcrest,$kotlin,$gson) -join ';'
& 'D:\tools\java\jdk-25.0.2\bin\java.exe' -cp $classpath org.junit.runner.JUnitCore cn.zhzgo.study.data.PracticeWorkflowTest cn.zhzgo.study.data.ExamWorkflowTest cn.zhzgo.study.data.StudyCalendarTest cn.zhzgo.study.ui.AppNavigationPolicyTest
exit $LASTEXITCODE
