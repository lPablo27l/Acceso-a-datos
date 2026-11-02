# AadTex Store Ingestion Engine · RA1 Actividad 1

**Alumno:** Pablo Eugenio Escolar Carrellan  
**Curso:** 2ºDAM  
**Módulo:** Acceso a Datos · RA1 Manejo de Ficheros  
**Repositorio:** https://github.com/lPablo27l/Acceso-a-datos

Aplicación Spring Boot que revisa la entrada cada cinco segundos, valida ventas internacionales, descifra AES-128 en memoria, ingiere ZIP en flujo y copia firmas JPEG con un buffer de 4096 bytes. Publica resúmenes JSON en un bucket local y aísla entradas defectuosas.

## Inicio rápido para la entrega y la defensa

1. Descomprimir `ra1_act1.zip` y entrar en la carpeta `ra1_act1`.
2. Tener **Java 21** instalado. Comprobarlo con `java -version`.
3. En Windows, abrir **`iniciar.cmd`**. En Linux/macOS, ejecutar `sh iniciar.sh`.
4. Esperar unos 5–10 segundos. Aparecerán `[SCAN]`, `[JPG]`, `[ZIP]`, `[AES]`, `[OK]`, `[JSON]` y la alerta del fichero vacío.
5. Revisar `store_data/output/` y `store_data/logs/aadtex.log`. Detener con **Ctrl+C**.

El JAR ejecutable está incluido en `dist/ra1-act1.jar`: el arranque no requiere Maven ni descargar dependencias. La primera ejecución crea sus datos automáticamente; la carpeta de trabajo no se entrega ya consumida.

Equivalente desde la raíz del proyecto:

```shell
java -Dfile.encoding=UTF-8 -jar dist/ra1-act1.jar
```

Para repetir toda la demostración sin borrar resultados anteriores, abrir **`nueva-demo.cmd`**. Cada ejecución emplea una carpeta nueva bajo `demostraciones/`. La aplicación muestra la ruta exacta en el log. También puede elegirse otra carpeta:

```shell
java -jar dist/ra1-act1.jar --aadtex.data-dir=./otra-demo
```

## Compilar desde el código y ejecutar las pruebas

Con Java 21 y conexión a Internet en la primera compilación:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

En Linux/macOS: `sh mvnw clean verify` y `sh mvnw spring-boot:run`.
Si Maven está instalado: `mvn clean verify` y `mvn spring-boot:run`.
El Maven Wrapper descarga Maven 3.9.9 automáticamente. El proyecto usa Spring Boot 3.5.7. El archivo generado al compilar es `target/ra1-act1-1.0.0.jar`. El JAR de `dist/` corresponde al código verificado de esta entrega; si se modifica el código, ejecutar el nuevo JAR de `target/`.

## Datos de prueba y resultados esperados

Los ficheros originales están incluidos en `datos-prueba/input/` y la clave en `datos-prueba/keys/`. `MultiLanguageDataGenerator` crea el mismo conjunto al arrancar, incluidas imágenes JPEG reales con firmas **ficticias** para la práctica.

| Lote | Transacciones | Total |
|---|---:|---:|
| España · UTF-8 y «Gran Vía» | 3 | 20100.80 |
| China · 上海南京东路店 | 2 | 41300.80 |
| Financiero · AES-128 | 1 | 95000.00 |
| Estados Unidos · CSV y JPG dentro del ZIP | 3 | 68450.75 |
| **Total** | **9** | **224852.35** |

Se generan **4 JSON**, **4 firmas** y se aísla **1 fichero de 0 bytes**. No se atribuye una moneda que los CSV no especifican. Los importes se calculan con `BigDecimal`.

```text
store_data/
├── .demo-generated
├── keys/AadTexSecret.key
├── input/                              # vacía tras procesar los ejemplos
├── logs/aadtex.log
└── output/
    ├── processed/                      # CSV originales, ENC cifrado y ZIP original
    │   └── signatures/                 # los 4 JPEG copiados
    ├── error/daily_sales_broken_store.csv
    ├── .staging/                       # temporales binarios/JSON; vacía al finalizar
    └── bucket/aadtex-daily-reports/2026/09/
        └── summary_batch_[UUID].json    # uno por lote válido
```

Cada JSON contiene identificador, origen, número de transacciones, total, tiendas, categorías, fechas de transacción y fecha real del procesamiento. La ruta `2026/09` se mantiene porque la exige el ejercicio; no depende del mes en el que se ejecute.

## Correspondencia con los requisitos

| Requisito | Implementación | Evidencia |
|---|---|---|
| 1. Scheduler | `AadApplication`, `StoreIngestionScheduler`, `@Scheduled(fixedRate = 5000)` | `docs/evidencias/02-ejecucion-scheduler.log` |
| 2. NIO.2 y aislamiento | `StoragePaths`, `StoreIngestionService`: `Path`, `Files.exists`, `createDirectories`, `size`, `move` | `docs/evidencias/salida-real/error/` |
| 3. ZIP en caliente | `StoreIngestionService.processZip`: `ZipInputStream`, `ZipEntry`; lectores que no cierran el contenedor | Log `[ZIP]` y resumen USA |
| 4. AES-128 en RAM | `CryptoService`, `ByteArrayInputStream`; modo ECB/PKCS5Padding del enunciado | Log `[AES]`, ENC archivado, ausencia de CSV financiero |
| 5. UTF-8 | `CsvSalesReader`: `BufferedReader`, `InputStreamReader`, decodificador UTF-8 estricto | JSON de España y China |
| 6. Buffer 4 KB | `SignatureService`: `BufferedInputStream`, `BufferedOutputStream`, `byte[4096]` | Log `[JPG]` y pruebas de igualdad binaria |
| 7. Generador | `MultiLanguageDataGenerator` implementa `CommandLineRunner` | `datos-prueba/`, `01-generacion.log` |
| 8. Bucket JSON | `CloudBucketService` | `salida-real/bucket/aadtex-daily-reports/2026/09/` |

## Decisiones para resolver las diferencias del enunciado

- **Directorio portable:** por defecto `./store_data`, relativo al directorio desde el que se arranca; los lanzadores se sitúan en la raíz. Evita permisos y ajustes en otro equipo. Puede emplearse exactamente `C:/workspace/store_data` pasando `--aadtex.data-dir=C:/workspace/store_data`, o `/workspace/store_data` en Linux.
- **Firmas:** se usa `output/processed/signatures/`, siguiendo el requisito 6 y el árbol del enunciado principal. El documento de ejemplos menciona también `output/signatures/`.
- **Correo:** la alerta a `soporte@aadtex.com` es una **simulación en el log**, como permite el ejercicio; no necesita cuenta de correo ni envía mensajes reales.
- **Clave:** `AadTexSecret2026` son 16 bytes UTF-8, válidos para AES-128. El generador crea el fichero de clave de ejemplo y el servicio usa la misma constante indicada en el ejercicio. El modo ECB y la clave fija reproducen exclusivamente el formato de esta actividad.
- **Memoria:** CSV y JPEG se tratan como flujos. El lote cifrado se carga en memoria por exigencia del requisito 4 y tiene un límite de 16 MiB. También se limita a 16 MiB el CSV leído. No se escribe el CSV financiero descifrado ni el CSV interno del ZIP en disco. Los resúmenes JSON sí publican los totales requeridos.
- **Movimiento:** se intenta `ATOMIC_MOVE` con `REPLACE_EXISTING`; si el sistema no soporta movimientos atómicos se recurre a `Files.move` con `REPLACE_EXISTING`. Los destinos de archivos archivados/erróneos reciben sufijo único si ya existen.
- **Repeticiones:** los originales salen de `input/` tras su procesamiento. `.demo-generated` evita recrear los ejemplos en cada arranque. Se ha comprobado que ciclos posteriores y un reinicio normal conservan los mismos cuatro JSON. La práctica está diseñada para una sola instancia; no implementa transacciones distribuidas ante un corte brusco entre la publicación y el archivado.
- **Firmas pendientes:** un CSV/ENC válido sin su firma espera al siguiente ciclo. Un fichero vacío se aísla inmediatamente.
- **ZIP:** valida el directorio central, CRC al consumir las entradas, pareja CSV/JPG, nombres y ausencia de rutas peligrosas. Las imágenes se validan por cabecera y final JPEG; no se hace una validación gráfica exhaustiva de JPEG arbitrarios.
- **CSV:** acepta comas y comillas escapadas dentro de campos entrecomillados; una transacción ocupa una línea. Rechaza cabeceras incorrectas, UTF-8 inválido, fechas incorrectas e importes negativos o con más de dos decimales.

## Capturas y evidencias

Abrir **`docs/evidencias/index.html`** para consultar las evidencias. Las capturas PNG están en `docs/capturas/`: son capturas originales del Bloc de notas de Windows mostrando los **logs, JSON y resultados reales** guardados tras ejecutar la aplicación y las pruebas. No se ha simulado una consola ni un IDE. Los originales completos se incluyen al lado para comprobar su contenido.

`docs/MEMORIA.md` explica el funcionamiento y las capturas. `docs/DEFENSA.md` contiene un guion de demostración y preguntas con respuestas.

## GitHub y Moodle

Repositorio de destino: https://github.com/lPablo27l/Acceso-a-datos

El README identifica el repositorio; la existencia del enlace no implica que la subida esté realizada. Comprobar en GitHub que aparecen `src`, `pom.xml`, `README.md`, `dist`, `datos-prueba` y `docs`. Si falta publicarlo, abrir la carpeta de este proyecto en GitHub Desktop, añadir el repositorio existente como remoto y publicar los commits. También puede usarse Git desde una sesión autenticada:

```shell
git remote -v
git push -u origin main
```

Entregar en Moodle **un único fichero `ra1_act1.zip`**, conservando exactamente ese nombre. La defensa se realiza con lo que contiene ese ZIP, incluido el código, los ejemplos, el JAR, la memoria y las evidencias.
