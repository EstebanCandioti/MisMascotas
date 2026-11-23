"""Impide empaquetar credenciales literales en las propiedades de producción."""

from pathlib import Path
import re
import subprocess
import sys


def main() -> int:
    raiz = Path(__file__).resolve().parent.parent
    errores = []
    local = "backend/src/main/resources/application.properties"
    versionado = subprocess.run(
        ["git", "ls-files", "--error-unmatch", "--", local], cwd=raiz,
        capture_output=True, check=False,
    )
    if versionado.returncode == 0:
        errores.append(f"{local}: debe permanecer local, fuera del índice de Git")
    plantilla = raiz / "backend/configuracion/application.properties"
    if not plantilla.is_file():
        errores.append("Falta backend/configuracion/application.properties")
    if "/src/main/resources/application.properties" not in (raiz / "backend/.dockerignore").read_text().splitlines():
        errores.append("Falta excluir application.properties local del contexto Docker")
    archivos = [plantilla] if plantilla.is_file() else []
    archivos.extend(p for p in (raiz / "backend/src/main/resources").rglob("*.properties")
                    if p != raiz / local)
    for archivo in archivos:
        for numero, linea in enumerate(archivo.read_text(encoding="utf-8").splitlines(), 1):
            if not linea.strip() or linea.lstrip().startswith(("#", "!")):
                continue
            clave, separador, valor = linea.partition("=")
            if not separador:
                continue
            if re.search(r"(?i)(?:password|secret|access-token)$", clave.strip()):
                valor = valor.strip()
                # Se admiten variables sin valor por defecto o con defecto vacío.
                if valor and not re.fullmatch(r"\$\{[A-Z][A-Z0-9_]*:?\}", valor):
                    errores.append(f"{archivo.relative_to(raiz)}:{numero}: {clave.strip()}")
    if errores:
        print("Configuración sensible literal: usar variables y el archivo local ignorado.", file=sys.stderr)
        print("\n".join(errores), file=sys.stderr)
        return 1
    print("Configuración empaquetada: sin claves literales en las propiedades verificadas.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
