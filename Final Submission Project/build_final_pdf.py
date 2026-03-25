from __future__ import annotations
from pathlib import Path
import re
import urllib.parse
from fpdf import FPDF
from pypdf import PdfReader, PdfWriter
from PIL import Image

BASE = Path(r"C:\Users\YahyaAli\Desktop\Mobile-Application-Development-COURSE\Final Submission Project")
REPORT_MD = BASE / "Project Rapport.md"
BODY_PDF = BASE / "Project Rapport Body.pdf"
FINAL_PDF = BASE / "Project Rapport Final.pdf"
FRONT_PDF = BASE / "Frontpage.pdf"

IMG_RE = re.compile(r"!\[([^\]]*)\]\(([^\)]+)\)")


def sanitize(text: str) -> str:
    replacements = {
        "–": "-",
        "—": "-",
        "’": "'",
        "“": '"',
        "”": '"',
        "•": "-",
    }
    for k, v in replacements.items():
        text = text.replace(k, v)
    return text.encode("latin-1", "ignore").decode("latin-1")


def resolve_image(path_raw: str) -> Path:
    decoded = urllib.parse.unquote(path_raw.strip())
    return (REPORT_MD.parent / decoded).resolve()


def safe_multicell(pdf: FPDF, text: str, h: float = 6.0, align: str = "L") -> None:
    text = sanitize(text)
    if not text.strip():
        pdf.ln(2)
        return

    # Split very long unbreakable tokens to avoid FPDF width exceptions
    chunks = []
    for token in text.split(" "):
        if len(token) > 70:
            for i in range(0, len(token), 60):
                chunks.append(token[i:i+60])
        else:
            chunks.append(token)
    safe_text = " ".join(chunks)

    try:
        pdf.multi_cell(0, h, safe_text, align=align)
    except Exception:
        # Last-resort fallback line-by-line
        for line in safe_text.splitlines() or [safe_text]:
            if not line:
                pdf.ln(h)
            else:
                pdf.cell(0, h, line[:1000], ln=1)


def add_image(pdf: FPDF, img_path: Path, caption: str | None = None) -> None:
    if not img_path.exists():
        return

    with Image.open(img_path) as im:
        px_w, px_h = im.size

    max_w = 170.0
    disp_h = max_w * (px_h / px_w)

    if disp_h > 230:
        disp_h = 230
        max_w = disp_h * (px_w / px_h)

    if pdf.get_y() + disp_h + 10 > 280:
        pdf.add_page()

    x = (210 - max_w) / 2.0
    pdf.image(str(img_path), x=x, y=pdf.get_y(), w=max_w)
    pdf.set_y(pdf.get_y() + disp_h + 3)

    if caption:
        pdf.set_font("Helvetica", "I", 9)
        safe_multicell(pdf, caption, h=5, align="C")
        pdf.ln(1)


def build_body_pdf() -> None:
    md_text = REPORT_MD.read_text(encoding="utf-8")
    lines = md_text.splitlines()

    pdf = FPDF(format="A4")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()

    in_code = False

    for raw in lines:
        line = raw.rstrip("\n")

        if line.strip().startswith("```"):
            in_code = not in_code
            continue

        if in_code:
            continue

        m = IMG_RE.search(line)
        if m:
            alt = m.group(1).strip()
            img_path = resolve_image(m.group(2))
            add_image(pdf, img_path, alt if alt else None)
            continue

        stripped = line.strip()

        if not stripped:
            pdf.ln(3)
            continue

        if stripped == "---":
            y = pdf.get_y()
            pdf.line(20, y, 190, y)
            pdf.ln(4)
            continue

        if line.startswith("# "):
            pdf.set_font("Helvetica", "B", 20)
            safe_multicell(pdf, line[2:].strip(), h=10)
            pdf.ln(2)
            continue

        if line.startswith("## "):
            if pdf.get_y() > 255:
                pdf.add_page()
            pdf.set_font("Helvetica", "B", 16)
            safe_multicell(pdf, line[3:].strip(), h=9)
            pdf.ln(1)
            continue

        if line.startswith("### "):
            if pdf.get_y() > 260:
                pdf.add_page()
            pdf.set_font("Helvetica", "B", 13)
            safe_multicell(pdf, line[4:].strip(), h=7)
            pdf.ln(1)
            continue

        if re.match(r"^\s*[-*]\s+", line):
            txt = re.sub(r"^\s*[-*]\s+", "- ", line)
            pdf.set_font("Helvetica", "", 11)
            safe_multicell(pdf, txt, h=6)
            continue

        if re.match(r"^\s*\d+\.\s+", line):
            pdf.set_font("Helvetica", "", 11)
            safe_multicell(pdf, line.strip(), h=6)
            continue

        if line.strip().startswith("|"):
            pdf.set_font("Courier", "", 8)
            safe_multicell(pdf, line.strip(), h=4.5)
            continue

        plain = line.replace("**", "").replace("`", "")
        pdf.set_font("Helvetica", "", 11)
        safe_multicell(pdf, plain, h=6)

    pdf.output(str(BODY_PDF))


def merge_final_pdf() -> None:
    if not FRONT_PDF.exists():
        raise FileNotFoundError(f"Missing front page PDF: {FRONT_PDF}")
    if not BODY_PDF.exists():
        raise FileNotFoundError(f"Missing body PDF: {BODY_PDF}")

    writer = PdfWriter()
    for src in (FRONT_PDF, BODY_PDF):
        reader = PdfReader(str(src))
        for page in reader.pages:
            writer.add_page(page)

    with FINAL_PDF.open("wb") as f:
        writer.write(f)


def main() -> int:
    build_body_pdf()
    merge_final_pdf()
    print(f"Created body PDF: {BODY_PDF}")
    print(f"Created final PDF: {FINAL_PDF}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
