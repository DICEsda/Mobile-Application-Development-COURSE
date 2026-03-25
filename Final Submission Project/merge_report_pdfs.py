from pathlib import Path
from pypdf import PdfReader, PdfWriter

base = Path(r"C:\Users\YahyaAli\Desktop\Mobile-Application-Development-COURSE\Final Submission Project")
front = base / "Frontpage.pdf"
body = base / "Project Rapport Body.pdf"  # export markdown to this first
out = base / "Project Rapport Final.pdf"

if not front.exists():
    raise SystemExit(f"Missing: {front}")
if not body.exists():
    raise SystemExit(f"Missing: {body} (export report body PDF first)")

writer = PdfWriter()
for src in (front, body):
    reader = PdfReader(str(src))
    for page in reader.pages:
        writer.add_page(page)

with out.open("wb") as f:
    writer.write(f)

print(f"Created: {out}")
