# Datos de prueba

`input/` contiene los ocho ficheros generados antes de activar el procesamiento. `keys/AadTexSecret.key` contiene la clave del ejercicio. Las firmas son imágenes JPEG sintéticas, sin firma personal real.

No hace falta copiarlos para el primer arranque: `MultiLanguageDataGenerator` crea el conjunto automáticamente. Para ingerir manualmente estos ejemplos, iniciar la aplicación con `--aadtex.generate-demo=false --aadtex.data-dir=./manual`, copiar los ocho archivos de `input/` a `manual/input/` y esperar al siguiente escaneo. Copiar el lote completo con la aplicación detenida evita que vea un ZIP mientras todavía se está copiando.
