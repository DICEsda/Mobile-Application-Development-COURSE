from pathlib import Path
import subprocess
import shutil

base = Path(r"C:\Users\YahyaAli\Desktop\Mobile-Application-Development-COURSE\Final Submission Project")
diagrams = base / "diagrams"
exports = diagrams / "exports"
exports.mkdir(parents=True, exist_ok=True)

jar = diagrams / "plantuml.jar"
if not jar.exists():
    print("Missing plantuml.jar. Place it at:", jar)
    raise SystemExit(1)

java = shutil.which("java")
if not java:
    print("Java not found. Install JRE/JDK first.")
    raise SystemExit(1)

pumls = [
    diagrams / "ui_flow.puml",
    diagrams / "component_diagram.puml",
    diagrams / "use_case_diagram.puml",
]

for p in pumls:
    if not p.exists():
        print("Missing:", p)
        raise SystemExit(1)

for p in pumls:
    cmd = [java, "-jar", str(jar), "-tpng", "-o", str(exports), str(p)]
    print("Running:", " ".join(cmd))
    subprocess.check_call(cmd)

print("Export complete. Output in:", exports)
