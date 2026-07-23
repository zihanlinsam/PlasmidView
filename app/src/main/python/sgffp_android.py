"""SnapGene .dna parser for Android via Chaquopy."""
import json, sys, traceback, os

try:
    from sgffp.reader import SgffReader
except ImportError:
    print(json.dumps({"error": "sgffp not installed"}))
    sys.exit(1)

COLOR_MAP = {
    "gene": "#4CAF50", "cds": "#2196F3", "promoter": "#FF9800",
    "rep_origin": "#F44336", "ori": "#F44336", "primer": "#9C27B0",
    "misc_feature": "#757575", "protein_bind": "#00BCD4",
    "misc_recomb": "#E91E63", "LTR": "#FF5722", "repeat_region": "#795548",
    "terminator": "#E91E63", "misc_signal": "#607D8B",
}

def parse(filepath: str) -> str:
    try:
        reader = SgffReader(filepath)
        doc = reader.read()
        name = "Untitled"
        try:
            if hasattr(doc, "notes"):
                name = doc.notes.get("CustomMapLabel", "") or doc.notes.get("name", "") or name
        except: pass
        if name == "Untitled":
            name = os.path.basename(filepath).rsplit(".", 1)[0]

        seq = ""
        if hasattr(doc, "sequence") and doc.sequence is not None:
            if hasattr(doc.sequence, "value"):
                seq = str(doc.sequence.value) or ""
            elif hasattr(doc.sequence, "data"):
                seq = str(doc.sequence.data) or ""

        topology = "CIRCULAR"
        try:
            if hasattr(doc.sequence, "topology"):
                topology = str(doc.sequence.topology)
            elif hasattr(doc.sequence, "is_circular"):
                topology = "CIRCULAR" if doc.sequence.is_circular else "LINEAR"
        except: pass
        topology = "LINEAR" if "linear" in topology.lower() else "CIRCULAR"

        features = []
        if hasattr(doc, "features"):
            for f in doc.features:
                try:
                    ft = str(getattr(f, "type", "misc_feature")).lower()
                    start = getattr(f, "start", 0) or 0
                    end = getattr(f, "end", 0) or 0
                    if end <= start: continue
                    strand = "forward"
                    try: strand = str(getattr(f, "strand", "forward")).lower()
                    except: pass
                    features.append({
                        "name": getattr(f, "name", "") or "",
                        "type": ft, "start": int(start), "end": int(end),
                        "strand": strand, "color": COLOR_MAP.get(ft, "#757575"),
                        "description": "",
                    })
                except: continue
        features.sort(key=lambda x: x["start"])
        return json.dumps({"name": name, "sequence": seq, "topology": topology, "features": features})
    except Exception as e:
        return json.dumps({"error": str(e), "detail": traceback.format_exc()})
