# Guion de defensa · Pablo Eugenio Escolar Carrellan · 2ºDAM

## Demostración de unos cinco minutos

1. Mostrar `README.md`, el enlace del repositorio y las clases de `src/main/java/es/aadtex/`.
2. Abrir `nueva-demo.cmd`. Mostrar el arranque Spring Boot y la ruta nueva de entrada.
3. Esperar el ciclo automático de cinco segundos. Identificar `[SCAN]`, copia JPG de 4096 bytes, procesamiento ZIP, descifrado AES y alerta del fichero de 0 bytes.
4. Abrir la carpeta de esa demostración. Enseñar que `input/` queda vacío, el archivo vacío queda en `output/error/` y las cuatro firmas están en `output/processed/signatures/`.
5. Abrir los JSON: España 20100.80, China 41300.80, financiero 95000.00 y USA 68450.75. Mostrar los caracteres chinos y la tilde de «Gran Vía».
6. Mostrar que el financiero se conserva como `.enc` y que no se creó su CSV descifrado. Explicar que el resumen JSON publica los totales solicitados.
7. Esperar otro ciclo. El escaneo indica cero entradas y no aparecen JSON nuevos. Detener con Ctrl+C.

Si se dispone de tiempo, ejecutar `.\mvnw.cmd test` para mostrar las 21 pruebas. El JAR incluido permite demostrar el funcionamiento sin Internet; la primera compilación del código con Maven sí puede descargar dependencias.

## Demostración extra de recuperación

Con la aplicación detenida después de procesar los ejemplos, copiar `datos-prueba/input/daily_sales_spain.csv` de nuevo a `store_data/input/`. Abrir `iniciar.cmd` y esperar el escaneo. Aparecerá `[RECOVER]`: el lote se archiva y siguen existiendo exactamente cuatro JSON con sus fechas originales. No hace falta descifrar ni extraer el lote otra vez porque el resumen confirma una publicación previa.

## Preguntas habituales

**¿Qué activa la automatización?**  
`@EnableScheduling` en `AadApplication`. Spring detecta el componente `StoreIngestionScheduler` y ejecuta `processStoreBatches()` con `@Scheduled(fixedRate = 5000)`. El procesamiento empieza cuando `ApplicationReadyEvent` confirma que el generador terminó.

**¿Qué significa fixedRate?**  
Planifica los inicios con un periodo de 5000 ms. No significa «esperar cinco segundos después de terminar»; eso sería `fixedDelay`. Se usa un planificador y el servicio sincroniza el procesamiento para evitar solapamiento dentro de esta instancia.

**¿Por qué NIO.2?**  
`Path` representa rutas sin concatenar separadores manualmente; `Files` permite crear directorios, consultar tamaños, listar y mover. Los streams de directorio se cierran con `try-with-resources`.

**¿Qué ocurre con un fichero vacío o corrupto?**  
Se escribe el mensaje de alerta requerido y se mueve a `output/error/`. Los demás lotes continúan. La alerta es simulada; no hay envío SMTP real.

**¿Cómo se lee el ZIP sin extraerlo primero?**  
`ZipInputStream` devuelve cada `ZipEntry`. El CSV se pasa al lector y el JPG se copia desde ese flujo. Una envoltura evita que al cerrar el lector se cierre el ZIP antes de la siguiente entrada. Sólo se publica el resumen después de validar ambas entradas.

**¿Por qué se comprueban los nombres del ZIP?**  
Para rechazar rutas como `../archivo` que podrían escapar del directorio previsto. El programa sólo acepta nombres simples y una pareja de ventas y firma.

**¿Cómo funciona AES?**  
El generador cifra un CSV con `Cipher`, `AES/ECB/PKCS5Padding` y la clave de 16 bytes indicada en la actividad. `CryptoService` lo descifra y `ByteArrayInputStream` permite leer el resultado directamente desde RAM. No se guarda el CSV descifrado y se limpia el array después de leerlo. La clave fija y ECB corresponden al formato exigido en este ejercicio.

**¿Para qué sirve UTF-8?**  
Especificar la codificación evita depender del valor por defecto del equipo. El decodificador conserva tildes, eñes y caracteres CJK, y rechaza secuencias inválidas.

**¿Por qué usar BigDecimal para ventas?**  
Mantiene exactitud decimal y evita errores de redondeo binario de `double`. Se exige una escala de dos decimales sin redondeos silenciosos.

**¿Por qué un buffer de 4096 bytes?**  
Permite copiar la firma a trozos y mantener un uso de memoria acotado independientemente del tamaño de la imagen. Los streams con buffer reducen llamadas de entrada/salida.

**¿Cómo se evita que el generador repita la carga?**  
Un marcador `.demo-generated` indica que ya generó los ejemplos. Los ficheros procesados salen de `input/`. Se verificaron varios ciclos y un reinicio normal. Para otra demostración se elige una carpeta nueva.

**¿El bucket es un servicio cloud real?**  
No. `CloudBucketService` simula el bucket con directorios y JSON locales, dejando la salida lista para la App 2. No se implementa la App 2 porque esta actividad pide la App 1.

**¿Por qué SHA-256 en el nombre del resumen?**  
La huella depende del nombre original y sus bytes. Permite saber, incluso después de reiniciar, que ese lote ya publicó su resumen. No es un cifrado ni permite recuperar el contenido. Una retransmisión idéntica conserva el JSON; si cambian las ventas cambia la huella.

**¿Qué diferencia hay entre `[RETRY]` y la alerta?**  
La alerta identifica datos inválidos y los aísla. `[RETRY]` indica un problema de acceso/publicación o un archivo que cambió durante la lectura, por lo que el origen se deja en entrada para otro intento. Un problema del disco no convierte unas ventas válidas en corruptas.

**¿Cómo se comprueba una interrupción?**  
Las pruebas simulan una excepción después de publicar el JSON o después de copiar la firma de un ZIP y crean otra instancia del servicio para reintentar. Se comprueba que el JSON no se duplica y que los archivos válidos no van a errores. Además se hizo un reenvío real de España con el JAR.

**¿Qué contienen las pruebas?**  
Son 21: incluyen reenvíos, cambios de contenido, fallos de publicación, interrupciones entre pasos y cambios durante la lectura, además de los ejemplos completos y sus totales, conservación de UTF-8 y bytes JPEG, AES, ausencia de duplicados, firma pendiente, CSV corrupto, cifrado inválido, ZIP truncado, rutas ZIP peligrosas y pareja incompleta.

**¿Qué límites tiene esta solución?**  
Una sola instancia, alertas y bucket simulados, CSV de una transacción por línea, límites de 16 MiB en texto/cifrado y 32 MiB por firma y sin garantía de transacción global ante una terminación brusca entre escrituras. Las firmas de demostración son ficticias y el validador JPEG comprueba cabecera y final, no todos los segmentos de una imagen arbitraria.
