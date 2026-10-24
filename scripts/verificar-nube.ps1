<#
  Comprueba que SoundIA esta listo en la nube y, de paso, despierta los servidores gratuitos (que se duermen a los
  15 minutos y tardan hasta dos o tres minutos en despertar). Ejecutalo ANTES de ir a tocar.

  Uso (PowerShell):
      .\verificar-nube.ps1 -Api https://soundia-api-xxxx.onrender.com -Usuario dj -Clave "tu-clave"
      .\verificar-nube.ps1 -Api https://soundia-api-xxxx.onrender.com -Usuario dj      # te pide la clave sin mostrarla

  Solo LEE: no sube, cambia ni borra nada. Pasos: 1) el API responde, 2) el login funciona, 3) el motor de IA responde a
  traves del API (la parte que tarda en despertar), 4) hay canciones en la biblioteca, 5) la copia de seguridad de las
  grabaciones se puede pedir. Al final dice si todo esta bien y, si no, que revisar.
#>
param(
    [Parameter(Mandatory = $true)][string]$Api,
    [string]$Usuario = 'dj',
    [string]$Clave = '',
    [int]$EsperaMaxima = 240
)

$ErrorActionPreference = 'Stop'
$Api = $Api.TrimEnd('/')
if (-not $Clave) {
    $segura = Read-Host "Clave de $Usuario" -AsSecureString
    $Clave = [System.Net.NetworkCredential]::new('', $segura).Password
}

$fallos = 0
function Paso($nombre, [scriptblock]$accion) {
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $detalle = & $accion
        Write-Host ("  [OK]    {0} ({1:N1} s) {2}" -f $nombre, $reloj.Elapsed.TotalSeconds, $detalle) -ForegroundColor Green
        return $true
    } catch {
        $script:fallos++
        Write-Host ("  [FALLO] {0} ({1:N1} s): {2}" -f $nombre, $reloj.Elapsed.TotalSeconds, $_.Exception.Message) -ForegroundColor Red
        return $false
    }
}

function Pedir($metodo, $ruta, $cabeceras, $cuerpo, $segundos) {
    $parametros = @{ Uri = "$Api$ruta"; Method = $metodo; TimeoutSec = $segundos; UseBasicParsing = $true }
    if ($cabeceras) { $parametros.Headers = $cabeceras }
    if ($cuerpo) { $parametros.Body = $cuerpo; $parametros.ContentType = 'application/json' }
    Invoke-WebRequest @parametros
}

Write-Host "Comprobando $Api ..." -ForegroundColor Cyan

$token = $null
$apiVivo = Paso '1. El API responde (puede tardar si estaba dormido)' {
    $r = Pedir 'GET' '/api/v1/auth/status' $null $null $EsperaMaxima
    "login " + $(if (($r.Content | ConvertFrom-Json).enabled) { 'encendido' } else { 'apagado' })
}

if ($apiVivo) {
    $sesion = Paso "2. El login de '$Usuario' funciona" {
        $cuerpo = @{ username = $Usuario; password = $Clave } | ConvertTo-Json
        try {
            $r = Pedir 'POST' '/api/v1/auth/login' $null $cuerpo 30
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { throw 'usuario o clave incorrectos' }
            throw
        }
        $script:token = ($r.Content | ConvertFrom-Json).token
        if (-not $script:token) { throw 'el API no devolvio un token' }
        ''
    }

    if ($sesion) {
        $cabeceras = @{ Authorization = "Bearer $token" }
        $biblioteca = $null
        Paso '3. El motor de IA responde a traves del API (aqui se despierta)' {
            $r = Pedir 'GET' '/api/v1/tracks' $cabeceras $null $EsperaMaxima
            $script:biblioteca = ($r.Content | ConvertFrom-Json).tracks
            ''
        } | Out-Null
        if ($null -ne $biblioteca) {
            Paso '4. La biblioteca' { "$($biblioteca.Count) cancion(es) subida(s)" } | Out-Null
        }
        Paso '5. La copia de seguridad de las grabaciones se puede pedir' {
            $r = Pedir 'GET' '/api/v1/recordings/export' $cabeceras $null 60
            "$((($r.Content | ConvertFrom-Json).recordings).Count) grabacion(es) guardada(s)"
        } | Out-Null
    }
}

Write-Host ''
if ($fallos -eq 0) {
    Write-Host 'Todo listo: los servidores estan despiertos y la cadena completa responde. Ya puedes ir a tocar.' -ForegroundColor Green
    Write-Host 'Recuerda: abre la aplicacion unos minutos antes y deja la pestana abierta (mantiene los servidores despiertos).'
    exit 0
}
Write-Host "Hay $fallos problema(s). Que revisar:" -ForegroundColor Yellow
Write-Host '  - Si el paso 1 o 3 se agoto: los servidores estaban dormidos; vuelve a ejecutar el script en un minuto.'
Write-Host '  - Si el paso 2 falla: revisa el usuario y la clave (son los de SOUNDIA_AUTH_USERNAME y SOUNDIA_AUTH_PASSWORD en Render).'
Write-Host '  - Si el paso 3 da 502 o "motor no disponible": mira los Logs de soundia-motor en Render.'
Write-Host '  - Plan B sin nube: SoundIA\iniciar-demo.ps1 -ConLogin -Datos C:\SoundIA-datos'
exit 1
