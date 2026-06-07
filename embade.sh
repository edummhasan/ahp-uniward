#!/bin/bash

set -euo pipefail
shopt -s nullglob nocaseglob

PASSWORD="9f3c8a7d4e1b6c0a82f5d91e37a64b2c4d8f0a6e13c95b7d20e4a8f1c6b39d52"

INPUT_DIR="input"
MSG_DIR="msg"
OUTPUT_DIR="output"

EXTRACT_SCRIPT="extract_all.sh"

mkdir -p "$OUTPUT_DIR"

PNG_FILES=("$INPUT_DIR"/*.png)
MSG_FILES=("$MSG_DIR"/*.msg)

if [ ${#PNG_FILES[@]} -eq 0 ]; then
    echo "No PNG files found in $INPUT_DIR"
    exit 1
fi

if [ ${#MSG_FILES[@]} -eq 0 ]; then
    echo "No MSG files found in $MSG_DIR"
    exit 1
fi

cat > "$EXTRACT_SCRIPT" <<EOF
#!/bin/bash

set -euo pipefail

PASSWORD="$PASSWORD"

EOF

for PNG_FILE in "${PNG_FILES[@]}"
do
    IMAGE_BASE=$(basename "$PNG_FILE")
    IMAGE_BASE="${IMAGE_BASE%.*}"

    echo "======================================"
    echo "Processing image: $IMAGE_BASE"
    echo "======================================"

    for MSG_FILE in "${MSG_FILES[@]}"
    do
        MSG_BASE=$(basename "$MSG_FILE")
        MSG_BASE="${MSG_BASE%.*}"

        OUT_FILE="$OUTPUT_DIR/${IMAGE_BASE}_${MSG_BASE}_stego.jpg"

        echo "Embedding: $PNG_FILE + $MSG_FILE -> $OUT_FILE"

        java -Djava.library.path="native" \
             -cp "ahp-100-jni-system.jar" \
             thesis.ahp.app.Ahp100Cli embed \
             --png "$PNG_FILE" \
             --msgFile "$MSG_FILE" \
             --out "$OUT_FILE" \
             --password "$PASSWORD"

        cat >> "$EXTRACT_SCRIPT" <<EOF
echo "Extracting: $OUT_FILE"

java -Djava.library.path="native" \\
     -cp "ahp-100-jni-system.jar" \\
     thesis.ahp.app.Ahp100Cli extract \\
     --png "$PNG_FILE" \\
     --stego "$OUT_FILE" \\
     --password "\$PASSWORD"

echo

EOF

    done
done

chmod +x "$EXTRACT_SCRIPT"

echo "All images and messages processed successfully."
echo "Extract script created: $EXTRACT_SCRIPT"

