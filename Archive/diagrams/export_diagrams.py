import base64
import requests
import sys
from pathlib import Path

def export_plantuml(input_file, output_file):
    """Export PlantUML file to PNG using Kroki service"""
    print(f"Exporting {input_file}...")
    
    try:
        # Read PlantUML file
        with open(input_file, 'r', encoding='utf-8') as f:
            puml_content = f.read()
        
        # Use Kroki API
        url = "https://kroki.io/plantuml/png"
        response = requests.post(url, data=puml_content.encode('utf-8'))
        
        if response.status_code == 200:
            with open(output_file, 'wb') as f:
                f.write(response.content)
            print(f"  ✓ Saved to {output_file}")
            return True
        else:
            print(f"  ✗ Error: {response.status_code}")
            return False
    except Exception as e:
        print(f"  ✗ Failed: {e}")
        return False

# Export diagrams
exports_dir = Path("exports")
exports_dir.mkdir(exist_ok=True)

diagrams = [
    ("component_diagram.puml", "component_diagram.png"),
    ("sequence_playback.puml", "sequence_playback.png"),
]

print("\n=== Exporting PlantUML Diagrams ===\n")

for input_file, output_file in diagrams:
    if Path(input_file).exists():
        export_plantuml(input_file, exports_dir / output_file)
    else:
        print(f"  ⊘ Skipping {input_file} (not found)")

print("\n=== Export Complete ===\n")
