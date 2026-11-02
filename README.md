# RA1 Act1 - AadTex Store Ingestion Engine

**Alumno:** Pablo Eugenio Escolar Carrellan  
**Curso:** 2ºDAM - Acceso a Datos  
**Repositorio:** https://github.com/lPablo27l/Acceso-a-datos

Aplicación Spring Boot para procesar ventas CSV en UTF-8, balances AES-128, lotes ZIP y firmas JPG. El procesamiento se ejecuta automáticamente cada cinco segundos. Los archivos inválidos se trasladan a errores y la alerta de correo se simula mediante el log.

## Ejecución

Requisito: Java 21. Desde la carpeta del proyecto, ejecutar:

```powershell
.\mvnw.cmd spring-boot:run
```

También se puede abrir `iniciar.cmd`, utilizar `mvn spring-boot:run` si Maven está instalado o ejecutar `AadApplication` desde el IDE. Maven Wrapper descarga Maven y las dependencias en la primera ejecución, por lo que se necesita conexión a Internet para esa primera preparación.

La ruta predeterminada es `C:/workspace/store_data`, como indica el enunciado. Puede modificarse en `src/main/resources/application.properties`.

Los datos de ejemplo incluidos en `src/main/resources/datos/` se copian a `input/` la primera vez que arranca la aplicación. El archivo `.datos-generados` evita volver a crear los mismos ejemplos en cada reinicio.

Después de unos segundos, los resultados están en:

- `output/processed/`: los CSV, ENC y ZIP originales procesados.
- `output/processed/signatures/`: las cuatro firmas JPG.
- `output/error/`: el fichero vacío y los archivos inválidos.
- `output/bucket/aadtex-daily-reports/2026/09/`: los cuatro resúmenes JSON.
- `aadtex.log`: el registro de ejecución.

Los totales de los ejemplos son España 20100.80, China 41300.80, financiero 95000.00 y USA 68450.75; el acumulado es 224852.35, con nueve ventas. El balance financiero y el ZIP se procesan sin generar un CSV descifrado o extraído en disco. El JPG del ZIP se copia directamente a su destino, sin archivos temporales.

## Comprobación

```powershell
.\mvnw.cmd clean verify
```

La memoria con las capturas y la correspondencia de los ocho requisitos está en `Memoria.pdf`.
